@file:Suppress("OPT_IN_USAGE", "DEPRECATION")
@file:OptIn(naksha.model.v30_experimental::class)

package naksha.iml

import java.util.UUID
import naksha.base.NakshaError
import naksha.base.NakshaException
import naksha.model.IReadSession
import naksha.model.Naksha
import naksha.model.NakshaContext
import naksha.model.SessionOptions
import naksha.model.objects.NakshaCatalog
import naksha.model.objects.NakshaCollection
import naksha.model.objects.NakshaFeature
import naksha.model.request.ReadFeatures
import naksha.model.request.SuccessResponse
import naksha.model.request.Write
import naksha.model.request.WriteRequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ImlSetupTest {
  @Test
  fun `fresh writable setup supports normal map collection and feature writes`() = withSetup { fixture, config, mapId ->
    val extensionsBefore = extensionCount(fixture)
    val storage = Naksha.useStorage(config) as ImlStorage
    assertTrue(storage.initialized)
    assertTrue(storage.metadata.allLayers.isEmpty())
    fixture.connection.prepareStatement("SELECT relname FROM pg_class WHERE relnamespace = ?::regnamespace ORDER BY relname").use {
      it.setString(1, config.schema)
      it.executeQuery().use { rows -> assertFalse(rows.next()) }
      it.setString(1, config.metadataSchema)
      it.executeQuery().use { rows ->
        assertTrue(rows.next())
        assertEquals("space_meta", rows.getString(1))
        assertTrue(rows.next())
        assertEquals("space_meta_pkey", rows.getString(1))
        assertFalse(rows.next())
      }
    }
    fixture.connection.createStatement().use {
      it.executeQuery("SELECT count(*) FROM ${ImlMetadata.quote(config.metadataSchema)}.space_meta").use { rows ->
        assertTrue(rows.next())
        assertEquals(0, rows.getInt(1))
      }
    }
    assertEquals(extensionsBefore, extensionCount(fixture))

    val collection = NakshaCollection("items", mapId)
    val feature = NakshaFeature.fromJson("""{"type":"Feature","id":"example","geometry":{"type":"Point","coordinates":[1,2,3]},"properties":{"name":"setup example"}}""")
    storage.newWriteSession(SessionOptions(appId = "iml-setup-test", author = "setup")).use { writer ->
      try {
        val response = writer.executeWrite(WriteRequest()
          .add(Write().createMap(NakshaCatalog(mapId)))
          .add(Write().createCollection(collection))
          .add(Write().createFeature(collection, feature))
          .apply { returnResults = true }) as SuccessResponse
        assertEquals(3, response.length)
        val catalog = checkNotNull(writer.getCatalogById(mapId))
        assertNotNull(writer.getCollectionById(catalog, collection.id))
        val pending = read(writer, collection)
        assertEquals(1, pending.length)
        assertEquals("setup example", pending.featureTupleList[0]?.feature?.properties?.getRaw("name"))
        val identity = checkNotNull(pending.featureTupleList[0]).tupleNumber
        writer.commit()
        storage.newReadSession().use { reader ->
          val committedCatalog = checkNotNull(reader.getCatalogById(mapId))
          assertNotNull(reader.getCollectionById(committedCatalog, collection.id))
          val committed = read(reader, collection)
          assertEquals(1, committed.length)
          assertEquals(identity, committed.featureTupleList[0]?.tupleNumber)
          assertEquals("setup example", committed.featureTupleList[0]?.feature?.properties?.getRaw("name"))
        }
        fixture.connection.prepareStatement("SELECT count(*) FROM pg_proc WHERE pronamespace IN (?::regnamespace, ?::regnamespace, ?::regnamespace)").use {
          it.setString(1, config.schema)
          it.setString(2, config.metadataSchema)
          it.setString(3, mapId)
          it.executeQuery().use { rows ->
            assertTrue(rows.next())
            assertEquals(0, rows.getInt(1))
          }
        }
        assertEquals(extensionsBefore, extensionCount(fixture))
      } finally {
        writer.rollback()
        if (writer.getCatalogById(mapId) != null) {
          writer.executeWrite(WriteRequest().add(Write().deleteMapById(mapId)))
          writer.commit()
          assertSchemasAbsent(fixture, mapId)
        }
      }
    }
  }

  @Test
  fun `read only setup rejects create without leaving schemas`() = withSetup(writeEnabled = false) { fixture, config, mapId ->
    val error = assertThrows(NakshaException::class.java) { Naksha.useStorage(config) }
    assertEquals(NakshaError.UNSUPPORTED_OPERATION, error.error.code)
    assertNull(Naksha.getStorageById(config.id))
    assertSchemasAbsent(fixture, config.schema, config.metadataSchema, mapId)
  }

  @Test
  fun `setup without super user authorization is forbidden without leaving schemas`() = withSetup(superUser = false) { fixture, config, mapId ->
    val error = assertThrows(NakshaException::class.java) { Naksha.useStorage(config) }
    assertEquals(NakshaError.FORBIDDEN, error.error.code)
    assertNull(Naksha.getStorageById(config.id))
    assertSchemasAbsent(fixture, config.schema, config.metadataSchema, mapId)
  }

  private fun read(session: IReadSession, collection: NakshaCollection): SuccessResponse {
    val response = session.executeRead(ReadFeatures().apply {
      catalogId = collection.catalogId
      collectionId = collection.id
      featureIds.add("example")
    }) as SuccessResponse
    session.loadTuples(response.featureTupleList)
    return response
  }

  private fun extensionCount(fixture: ImlTestFixture): Int = fixture.connection.createStatement().use {
    it.executeQuery("SELECT count(*) FROM pg_extension").use { rows ->
      check(rows.next())
      rows.getInt(1)
    }
  }

  private fun assertSchemasAbsent(fixture: ImlTestFixture, vararg names: String) {
    fixture.connection.prepareStatement("SELECT to_regnamespace(?)").use {
      for (name in names) {
        it.setString(1, name)
        it.executeQuery().use { rows ->
          assertTrue(rows.next())
          assertNull(rows.getString(1), name)
        }
      }
    }
  }

  private fun withSetup(writeEnabled: Boolean = true, superUser: Boolean = true,
    block: (ImlTestFixture, ImlConfig, String) -> Unit) = ImlTestFixture.withFixture { fixture ->
    val suffix = UUID.randomUUID().toString().replace("-", "")
    val config = ImlConfig("iml-setup-$suffix", fixture.config.jdbcUrl).apply {
      schema = "iml_set_$suffix"
      metadataSchema = "iml_sm_$suffix"
      this.writeEnabled = writeEnabled
      create = true
    }
    val mapId = "iml_map_$suffix"
    val ownedSchemas = arrayOf(config.schema, config.metadataSchema, mapId)
    for (name in ownedSchemas) assertTrue(name.length < 42)
    assertSchemasAbsent(fixture, *ownedSchemas)
    val context = NakshaContext.currentContext()
    val previous = context.su
    context.su = superUser
    try {
      block(fixture, config, mapId)
    } finally {
      try {
        Naksha.removeStorage(config)
      } finally {
        try {
          for (name in ownedSchemas.reversedArray()) fixture.connection.createStatement().use {
            it.execute("DROP SCHEMA IF EXISTS ${ImlMetadata.quote(name)} CASCADE")
          }
        } finally { context.su = previous }
      }
    }
  }
}
