@file:Suppress("OPT_IN_USAGE", "DEPRECATION")
@file:OptIn(naksha.model.v30_experimental::class)

package naksha.iml

import naksha.base.NakshaError
import naksha.base.NakshaException
import naksha.model.Naksha
import naksha.model.objects.NakshaCollection
import naksha.model.objects.NakshaFeature
import naksha.model.request.SuccessResponse
import naksha.model.request.Write
import naksha.model.request.WriteRequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ImlStartupPerformanceTest {
  @Test
  fun `bootstrap scans distinct tables once per storage independent of hard cap`() = ImlTestFixture.withFixture(true) { fixture ->
    fixture.config.writeEnabled = true
    fixture.config.hardCap = 1
    fixture.addLayer("alias", fixture.table)
    fixture.insert("high-version", 1000, "I", null, null, 12, target = fixture.otherTable)
    val storage = fixture.storage
    (storage.newWriteSession() as ImlWriteSession).use { writer ->
      assertEquals(1001L, writer.useTransaction().version.number ushr 2)
      assertEquals(2, writer.reconciledTables)
      writer.rollback()
      for (i in 0 until 5) {
        writer.useTransaction()
        writer.commit()
      }
      assertEquals(2, writer.reconciledTables)
    }
    (storage.newWriteSession() as ImlWriteSession).use { writer ->
      writer.useTransaction()
      assertEquals(0, writer.reconciledTables)
    }
  }

  @Test
  fun `prepared transactions do not access unrelated physical tables and reuse commit metadata`() = ImlTestFixture.withFixture { fixture ->
    fixture.config.writeEnabled = true
    val storage = fixture.storage
    val collection = NakshaCollection(fixture.layer, fixture.schema)
    (storage.newWriteSession() as ImlWriteSession).use { writer ->
      write(writer, Write().createFeature(collection, NakshaFeature("first")))
      val metadata = writer.sessionMetadata
      writer.commit()
      assertSame(metadata, storage.metadata)
      fixture.connection.createStatement().use {
        it.execute("DROP TABLE ${ImlMetadata.quote(fixture.schema)}.${ImlMetadata.quote(fixture.otherTable)}")
      }
      write(writer, Write().createFeature(collection, NakshaFeature("second")))
      writer.commit()
      assertEquals(2, writer.reconciledTables)
    }
    (storage.newWriteSession() as ImlWriteSession).use { writer ->
      write(writer, Write().createFeature(collection, NakshaFeature("third")))
      writer.commit()
      assertEquals(0, writer.reconciledTables)
    }
  }

  @Test
  fun `first rollback never reuses allocated identity even after storage restart`() = ImlTestFixture.withFixture { fixture ->
    fixture.config.writeEnabled = true
    val first = (fixture.storage.newWriteSession() as ImlWriteSession).use { writer ->
      val version = writer.useTransaction().version.number
      writer.rollback()
      assertTrue(writer.useTransaction().version.number > version)
      writer.rollback()
      version
    }
    Naksha.removeStorage(fixture.config)
    (fixture.storage.newWriteSession() as ImlWriteSession).use { writer ->
      assertTrue(writer.useTransaction().version.number > first)
      assertEquals(2, writer.reconciledTables)
    }
  }

  @Test
  fun `fresh storage reconciles external versions without rewinding the durable sequence`() = ImlTestFixture.withFixture { fixture ->
    fixture.config.writeEnabled = true
    (fixture.storage.newWriteSession() as ImlWriteSession).use { writer -> writer.useTransaction(); writer.rollback() }
    Naksha.removeStorage(fixture.config)
    fixture.insert("imported", 10000, "I", null, null, 12, target = fixture.otherTable)
    (fixture.storage.newWriteSession() as ImlWriteSession).use { writer ->
      assertEquals(10001L, writer.useTransaction().version.number ushr 2)
      writer.rollback()
    }
    Naksha.removeStorage(fixture.config)
    (fixture.storage.newWriteSession() as ImlWriteSession).use { writer ->
      assertEquals(10002L, writer.useTransaction().version.number ushr 2)
    }
  }

  @Test
  fun `external target advancement after preparation remains an explicit conflict`() = ImlTestFixture.withFixture { fixture ->
    fixture.config.writeEnabled = true
    (fixture.storage.newWriteSession() as ImlWriteSession).use { writer ->
      writer.useTransaction()
      writer.rollback()
      fixture.insert("externally-advanced", 10000, "I", null, null, 17)
      val collection = NakshaCollection(fixture.layer, fixture.schema)
      val error = assertThrows(NakshaException::class.java) {
        write(writer, Write().upsertFeature(collection, NakshaFeature("externally-advanced")))
      }
      assertEquals(NakshaError.CONFLICT, error.error.code)
      assertTrue(writer.useTransaction().version.number ushr 2 < 10000L)
      write(writer, Write().createFeature(collection, NakshaFeature("after-conflict")))
      writer.commit()
      assertEquals(2, writer.reconciledTables)
    }
  }

  @Test
  fun `failed reconciliation is retried rather than marking storage prepared`() = ImlTestFixture.withFixture { fixture ->
    fixture.config.writeEnabled = true
    fixture.addLayer("unavailable", "missing")
    (fixture.storage.newWriteSession() as ImlWriteSession).use { writer ->
      assertThrows(Exception::class.java) { writer.useTransaction() }
      fixture.connection.prepareStatement("DELETE FROM ${fixture.sqlMetadata} WHERE id = ?").use {
        it.setString(1, "unavailable")
        it.executeUpdate()
      }
      assertEquals(13L, writer.useTransaction().version.number ushr 2)
    }
  }

  private fun write(writer: ImlWriteSession, operation: Write): SuccessResponse =
    writer.executeWrite(WriteRequest().add(operation).apply { returnResults = true }) as SuccessResponse
}
