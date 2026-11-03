package naksha.psql

import naksha.base.Id
import naksha.model.Naksha
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import java.sql.DriverManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PgBootstrapConfigTest {
  @Test
  fun recognizesTypedMasterBeforeInitializingStorage() {
    val config = PgConfig("bootstrap_probe").apply {
      master = PgInstanceConfig.fromUri("jdbc:postgresql://localhost/bootstrap_config?user=probe&password=unused")
    }
    val master = assertNotNull(config.getMasterOrNull())
    assertEquals("bootstrap_config", master.db)
    assertEquals(config.masterUri, master.toString())
  }
}

@EnabledIfEnvironmentVariable(named = "NAKSHA_TEST_BOOTSTRAP_DB_URL", matches = ".+")
class PgBootstrapTest {
  @Test
  fun bootstrapsFreshDatabaseThroughPublicFactory() {
    val url = System.getenv("NAKSHA_TEST_BOOTSTRAP_DB_URL")
    val override = System.getenv("NAKSHA_TEST_PSQL_DB_URL")
      ?: System.getProperty("naksha.test.psql.db.url")
    if (override != null) {
      assertTrue(override == url, "The PgStorage URL override must point to the bootstrap database")
    }
    DriverManager.getConnection(url).use { conn ->
      conn.createStatement().use { statement ->
        statement.executeQuery("SELECT 1 FROM pg_namespace WHERE nspname = 'naksha~admin'").use { rows ->
          assertFalse(rows.next(), "Bootstrap requires a fresh dedicated database; no schemas are deleted")
        }
      }
    }

    val config = PgConfig("bootstrap_probe").apply {
      className = "naksha.psql.PgStorage"
      master = PgInstanceConfig.fromUri(url)
      create = true
    }
    val storage = Naksha.useStorage(config) as PgStorage
    assertTrue(storage.initialized)
    assertEquals(Id(config.id), storage.id)
    assertSame(storage, Naksha.useStorageById(config.id))
    assertTrue(storage.database.schemaOid > 0)
    assertTrue(storage.database.versionSequenceOid > 0)

    storage.newSession(Naksha.useAdminOptions(), false).use { session ->
      assertNull(session.getTransaction())
      val maintained = session.useConnNonNull(false) { conn ->
        storage.database.maintain_partitions(conn)
      }
      assertTrue(maintained >= 0)
      assertNull(session.getTransaction(), "Partition maintenance must not allocate a logical transaction")
    }

    // Use an independent connection to verify bootstrap committed its identity, sequence, and roots.
    DriverManager.getConnection(url).use { conn ->
      conn.createStatement().use { statement ->
        statement.executeQuery(
          """SELECT "naksha~admin".naksha_storage_id() AS id,
            |"naksha~admin".naksha_storage_number() AS number,
            |"naksha~admin".naksha_version() AS version,
            |to_regclass('"naksha~admin".$NAKSHA_VERSION_SEQ')::oid AS sequence_oid
          """.trimMargin()
        ).use { rows ->
          assertTrue(rows.next())
          assertEquals(config.id, rows.getString("id"))
          assertEquals(storage.id.number, rows.getLong("number"))
          assertEquals(adminVersion.toLong(), rows.getLong("version"))
          assertEquals(storage.database.versionSequenceOid, rows.getInt("sequence_oid"))
        }
      }
      val database = storage.database
      conn.prepareStatement(
        """SELECT c.relkind::text, c.relispartition,
          |EXISTS (SELECT 1 FROM pg_constraint k WHERE k.conrelid = c.oid AND k.contype = 'p') AS has_primary_key
          |FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
          |WHERE n.nspname = ? AND c.relname = ?
        """.trimMargin()
      ).use { statement ->
        for (collection in listOf(
          database.pgAdminCollections,
          database.pgAdminTransactions,
          database.pgAdminCatalogs,
          database.pgAdminBooks
        )) {
          statement.setString(1, database.pgAdmin.name)
          statement.setString(2, collection.name)
          statement.executeQuery().use { rows ->
            assertTrue(rows.next(), "Missing committed admin root ${collection.name}")
            assertEquals("p", rows.getString("relkind"), collection.name)
            assertFalse(rows.getBoolean("relispartition"), collection.name)
            assertTrue(rows.getBoolean("has_primary_key"), collection.name)
            assertFalse(rows.next(), collection.name)
          }
        }
      }
    }
  }
}
