package naksha.iml

import java.sql.Connection
import java.sql.SQLException
import java.util.UUID
import naksha.model.Naksha
import naksha.model.SessionOptions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ImlStorageIntegrationTest {
  @Test
  fun `storage opens a read session with a server read-only transaction`() = withStorage { storage ->
    assertTrue(storage.initialized)
    storage.newReadSession().use { session ->
      assertSame(storage, session.storage)
      assertFalse(session.isClosed())
      session.withConnection { connection ->
        assertTrue(connection.isReadOnly)
        assertFalse(connection.autoCommit)
        assertEquals("1", query(connection, "SELECT 1"))
        assertEquals("on", query(connection, "SHOW transaction_read_only"))
      }
    }
  }

  @Test
  fun `server rejects non-temporary table creation without schema changes`() = withStorage { storage ->
    storage.newReadSession().use { session ->
      val table = "iml_read_only_probe_${UUID.randomUUID().toString().replace("-", "")}"
      val before = session.withConnection { schemaState(it, table) }
      val error = assertThrows(SQLException::class.java) {
        session.withConnection { connection ->
          assertFalse(connection.autoCommit)
          assertEquals("on", query(connection, "SHOW transaction_read_only"))
          // Use an existing explicit schema, never CREATE SCHEMA or a temporary table. The transaction always rolls back.
          connection.createStatement().use { it.execute("CREATE TABLE pg_catalog.\"$table\" (id integer)") }
        }
      }
      assertEquals("25006", error.sqlState)
      val after = session.withConnection { schemaState(it, table) }
      assertEquals(before, after)
      session.withConnection { connection ->
        assertNull(query(connection, "SELECT to_regclass('pg_catalog.$table')"))
      }
    }
  }

  @Test
  fun `session timeouts propagate to each transaction and respect setter changes`() = withStorage { storage ->
    val options = SessionOptions(connectTimeout = 5000, socketTimeout = 15000, stmtTimeout = 4321, lockTimeout = 1234)
    storage.newReadSession(options).use { session ->
      assertSame(options, session.options)
      session.withConnection { connection ->
        assertEquals("4321ms", query(connection, "SHOW statement_timeout"))
        assertEquals("1234ms", query(connection, "SHOW lock_timeout"))
      }
      session.stmtTimeout = 5432
      session.lockTimeout = 2345
      session.socketTimeout = 16000
      assertEquals(16000, session.options.socketTimeout)
      session.withConnection { connection ->
        assertEquals("5432ms", query(connection, "SHOW statement_timeout"))
        assertEquals("2345ms", query(connection, "SHOW lock_timeout"))
      }
      session.stmtTimeout = 0
      session.lockTimeout = 0
      session.withConnection { connection ->
        assertEquals("0", query(connection, "SHOW statement_timeout"))
        assertEquals("0", query(connection, "SHOW lock_timeout"))
      }
    }
  }

  @Test
  fun `exception closes connection but leaves session reusable and close is idempotent`() = withStorage { storage ->
    val session = storage.newReadSession()
    var failedConnection: Connection? = null
    val failure = IllegalStateException("expected test failure")
    try {
      val thrown = assertThrows(IllegalStateException::class.java) {
        session.withConnection<Unit> { connection ->
          failedConnection = connection
          assertEquals("1", query(connection, "SELECT 1"))
          throw failure
        }
      }
      assertSame(failure, thrown)
      assertTrue(checkNotNull(failedConnection).isClosed)
      assertFalse(session.isClosed())
      val successfulConnection = session.withConnection { connection ->
        assertEquals("1", query(connection, "SELECT 1"))
        connection
      }
      assertTrue(successfulConnection.isClosed)
    } finally {
      session.close()
    }
    session.close()
    assertTrue(session.isClosed())
    assertThrows(IllegalStateException::class.java) { session.withConnection { } }
    assertThrows(IllegalStateException::class.java) { session.stmtTimeout = 1 }
  }

  @Test
  fun `removing storage closes its sessions and prevents reopening on removed instance`() = withStorage { storage ->
    val session = storage.newReadSession()
    try {
      assertSame(storage, Naksha.removeStorage(storage.config))
      assertNull(Naksha.getStorageById(storage.id))
      assertNull(Naksha.getStorageByNumber(storage.number))
      assertTrue(session.isClosed())
      assertThrows(IllegalStateException::class.java) { session.withConnection { } }
      assertThrows(IllegalStateException::class.java) { storage.newReadSession() }
      assertNull(Naksha.removeStorage(storage.config))
    } finally {
      session.close()
    }
  }

  private fun withStorage(block: (ImlStorage) -> Unit) {
    ImlTestFixture.withFixture { block(it.storage) }
  }

  private fun query(connection: Connection, sql: String): String? =
    connection.createStatement().use { statement ->
      statement.executeQuery(sql).use { result ->
        assertTrue(result.next())
        result.getString(1)
      }
    }

  private fun schemaState(connection: Connection, table: String): List<String> {
    val state = mutableListOf<String>()
    connection.prepareStatement(
      "SELECT 'schema:' || oid || ':' || nspname FROM pg_catalog.pg_namespace " +
        "UNION ALL SELECT 'relation:' || oid || ':' || relnamespace || ':' || relname " +
        "FROM pg_catalog.pg_class WHERE relname = ? ORDER BY 1"
    ).use { statement ->
      statement.setString(1, table)
      statement.executeQuery().use { result ->
        while (result.next()) state.add(result.getString(1))
      }
    }
    return state
  }
}
