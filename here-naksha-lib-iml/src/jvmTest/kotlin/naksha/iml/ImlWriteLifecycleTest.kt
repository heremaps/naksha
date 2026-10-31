@file:Suppress("OPT_IN_USAGE", "DEPRECATION")
@file:OptIn(naksha.model.v30_experimental::class)

package naksha.iml

import java.sql.SQLException
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import naksha.base.NakshaException
import naksha.base.TupleNumber
import naksha.model.IReadSession
import naksha.model.IWriteSession
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
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ImlWriteLifecycleTest {
  @Test
  fun `metadata publication never regresses and snapshots remain independent`() = withWriter { fixture, _, _ ->
    val storage = fixture.storage
    val old = ImlMetadata(fixture.connection, fixture.config, storage.number)
    fixture.addLayer("late-layer", fixture.otherTable)
    val newer = ImlMetadata(fixture.connection, fixture.config, storage.number)
    assertNull(old.getLayer(fixture.schema, "late-layer"))
    assertNotNull(newer.getLayer(fixture.schema, "late-layer"))

    storage.publishMetadata(old, 10)
    assertEquals(10L, storage.generation)
    assertSame(old, storage.metadata)
    storage.publishMetadata(newer, 12)
    storage.publishMetadata(old, 11)
    assertEquals(12L, storage.generation)
    assertSame(newer, storage.metadata)
    assertNotNull(storage.metadata.getLayer(fixture.schema, "late-layer"))
    // Reusing the same snapshot must still advance the publication revision.
    storage.publishMetadata(newer, 13)
    storage.publishMetadata(old, 12)
    assertEquals(13L, storage.generation)
    assertSame(newer, storage.metadata)
    assertNull(old.getLayer(fixture.schema, "late-layer"))
  }

  @Test
  fun `late tuple load from an old generation cannot repopulate the cache`() = withWriter { fixture, _, _ ->
    val storage = fixture.storage
    storage.newReadSession().use { reader ->
      val response = read(reader, NakshaCollection(fixture.layer, fixture.schema), "00123")
      val entry = checkNotNull(response.featureTupleList[0])
      val tuple = checkNotNull(entry.tuple)
      val generation = storage.generation
      storage.cacheTuple(tuple, generation)
      assertNotNull(storage.cachedTuple(entry.tupleNumber))
      storage.publishMetadata(storage.metadata, generation + 1)
      assertNull(storage.cachedTuple(entry.tupleNumber))
      storage.cacheTuple(tuple, generation)
      assertNull(storage.cachedTuple(entry.tupleNumber))
      storage.cacheTuple(tuple, generation + 1)
      assertNotNull(storage.cachedTuple(entry.tupleNumber))
    }
  }

  @Test
  fun `failed map creation batch restores private metadata and preserves earlier transaction`() = withWriter { fixture, writer, maps ->
    val collection = NakshaCollection(fixture.layer, fixture.schema)
    write(writer, Write().createFeature(collection, NakshaFeature("earlier")))
    val version = writer.useTransaction().version.number
    val modified = writer.useTransaction().featuresModified
    val mapId = ownedMap(maps)
    val added = NakshaCollection("items", mapId)
    assertThrows(NakshaException::class.java) {
      writer.executeWrite(WriteRequest()
        .add(Write().createMap(NakshaCatalog(mapId)))
        .add(Write().createCollection(added))
        .add(Write().createMap(NakshaCatalog(mapId))))
    }
    assertNull(writer.getCatalogById(mapId))
    assertEquals(version, writer.useTransaction().version.number)
    assertEquals(modified, writer.useTransaction().featuresModified)
    fixture.connection.prepareStatement("SELECT to_regnamespace(?)").use {
      it.setString(1, mapId)
      it.executeQuery().use { rows -> assertTrue(rows.next()); assertNull(rows.getString(1)) }
    }
    assertEquals(1, read(writer, collection, "earlier").length)
    writer.executeWrite(WriteRequest()
      .add(Write().createMap(NakshaCatalog(mapId)))
      .add(Write().createCollection(added)))
    writer.commit()
    fixture.storage.newReadSession().use { reader ->
      assertNotNull(reader.getCatalogById(mapId))
      assertEquals(1, read(reader, collection, "earlier").length)
      assertNotNull(reader.getCollectionById(checkNotNull(reader.getCatalogById(mapId)), added.id))
    }
  }

  @Test
  fun `committed map collection and feature versions survive discovery under another storage identity`() = withWriter { fixture, writer, maps ->
    val mapId = ownedMap(maps)
    val collection = NakshaCollection("items", mapId)
    val mapTn = checkNotNull(write(writer, Write().createMap(NakshaCatalog(mapId))).featureTupleList[0]).tupleNumber
    val collectionTn = checkNotNull(write(writer, Write().createCollection(collection)).featureTupleList[0]).tupleNumber
    val featureTn = checkNotNull(write(writer, Write().createFeature(collection, NakshaFeature("persisted"))).featureTupleList[0]).tupleNumber
    writer.commit()
    val config = otherConfig(fixture)
    try {
      val other = Naksha.useStorage(config) as ImlStorage
      assertNotEquals(fixture.storage.number, other.number)
      other.newReadSession().use { reader ->
        val catalog = checkNotNull(reader.getCatalogById(mapId))
        assertEquals(TupleNumber.copy(mapTn, storageNumber = other.number), catalog.tupleNumber)
        assertEquals(TupleNumber.copy(collectionTn, storageNumber = other.number), reader.getCollectionById(catalog, collection.id)?.tupleNumber)
        assertEquals(TupleNumber.copy(featureTn, storageNumber = other.number), read(reader, collection, "persisted").featureTupleList[0]?.tupleNumber)
      }
    } finally { Naksha.removeStorage(config) }
  }

  @Test
  fun `idle transaction timeout is applied to every writer transaction connection`() = withWriter { fixture, _, _ ->
    fixture.storage.newWriteSession(SessionOptions(appId = "iml-lifecycle-idle", idleTxTimeout = 12345)).use { writer ->
      for (commit in arrayOf(true, false)) {
        writer.useTransaction()
        (writer as ImlWriteSession).withConnection { connection ->
          assertFalse(connection.isReadOnly)
          connection.createStatement().use {
            it.executeQuery("SHOW idle_in_transaction_session_timeout").use { rows ->
              assertTrue(rows.next())
              assertEquals("12345ms", rows.getString(1))
            }
          }
        }
        if (commit) writer.commit() else writer.rollback()
      }
    }
  }

  @Test
  fun `same collection and feature IDs in multiple catalogs remain separated by ID and number`() = withWriter { fixture, writer, maps ->
    val first = NakshaCollection("items", ownedMap(maps))
    val second = NakshaCollection("items", ownedMap(maps))
    val identities = ArrayList<TupleNumber>()
    for ((index, collection) in arrayOf(first, second).withIndex()) {
      write(writer, Write().createMap(NakshaCatalog(checkNotNull(collection.catalogId))))
      write(writer, Write().createCollection(collection))
      identities.add(checkNotNull(write(writer, Write().createFeature(collection,
        NakshaFeature("same-id").apply { properties.setRaw("catalogValue", index) })).featureTupleList[0]).tupleNumber)
    }
    assertEquals(identities[0].collectionNumber, identities[1].collectionNumber)
    assertNotEquals(identities[0].catalogNumber, identities[1].catalogNumber)
    for ((index, collection) in arrayOf(first, second).withIndex()) {
      assertEquals(index, read(writer, collection, "same-id").featureTupleList[0]?.feature?.properties?.getAs("catalogValue", Int::class))
    }
    writer.commit()
    fixture.storage.newReadSession().use { reader ->
      for ((index, collection) in arrayOf(first, second).withIndex()) {
        val catalog = checkNotNull(reader.getCatalogById(checkNotNull(collection.catalogId)))
        assertEquals(collection.id, reader.getCollectionByNumber(catalog, identities[index].collectionNumber)?.id)
        assertEquals(collection.catalogId, reader.getCollectionByNumber(catalog, identities[index].collectionNumber)?.catalogId)
        assertEquals(identities[index], read(reader, collection, "same-id").featureTupleList[0]?.tupleNumber)
      }
      write(writer, Write().updateFeature(first, NakshaFeature("same-id").apply { properties.setRaw("catalogValue", 10) }, false))
      writer.commit()
      assertEquals(10, read(reader, first, "same-id").featureTupleList[0]?.feature?.properties?.getAs("catalogValue", Int::class))
      assertEquals(1, read(reader, second, "same-id").featureTupleList[0]?.feature?.properties?.getAs("catalogValue", Int::class))
    }
  }

  @Test
  fun `contending writers time out before allocation then serialize with unique consecutive versions`() = withWriter { fixture, first, _ ->
    val collection = NakshaCollection(fixture.layer, fixture.schema)
    val executor = Executors.newSingleThreadExecutor()
    try {
      val initial = checkNotNull(write(first, Write().createFeature(collection, NakshaFeature("first"))).featureTupleList[0]).tupleNumber
      fixture.storage.newWriteSession(SessionOptions(appId = "iml-lifecycle-timeout", lockTimeout = 100, stmtTimeout = 2000)).use { timedOut ->
        executor.submit {
          assertThrows(SQLException::class.java) { timedOut.useTransaction() }
          assertNull(timedOut.getTransaction())
        }.get(5, TimeUnit.SECONDS)
        first.commit()
        val retried = checkNotNull(write(timedOut, Write().createFeature(collection, NakshaFeature("retried"))).featureTupleList[0]).tupleNumber
        assertEquals((initial.version ushr 2) + 1, retried.version ushr 2)
        val started = CountDownLatch(1)
        val waiting = executor.submit<TupleNumber> {
          fixture.storage.newWriteSession(SessionOptions(appId = "iml-lifecycle-waiting", lockTimeout = 5000, stmtTimeout = 10000)).use { next ->
            started.countDown()
            val tuple = checkNotNull(write(next, Write().createFeature(collection, NakshaFeature("waiting"))).featureTupleList[0]).tupleNumber
            next.commit()
            tuple
          }
        }
        assertTrue(started.await(5, TimeUnit.SECONDS))
        assertThrows(TimeoutException::class.java) { waiting.get(150, TimeUnit.MILLISECONDS) }
        timedOut.commit()
        val last = waiting.get(10, TimeUnit.SECONDS)
        assertEquals((retried.version ushr 2) + 1, last.version ushr 2)
        assertEquals(3, setOf(initial, retried, last).size)
        fixture.connection.createStatement().use {
          it.executeQuery("SELECT source_version FROM ${ImlMetadata.quote(fixture.metadataSchema)}.iml_transactions ORDER BY source_version").use { rows ->
            for (identity in arrayOf(initial, retried, last)) {
              assertTrue(rows.next())
              assertEquals(identity.version ushr 2, rows.getLong(1))
            }
            assertFalse(rows.next())
          }
        }
      }
    } finally {
      first.rollback()
      executor.shutdownNow()
      assertTrue(executor.awaitTermination(15, TimeUnit.SECONDS))
    }
  }

  @Test
  fun `writer reads never publish tentative tuples to the global reader cache`() = withWriter { fixture, writer, _ ->
    val collection = NakshaCollection(fixture.layer, fixture.schema)
    val created = checkNotNull(write(writer, Write().createFeature(collection, NakshaFeature("tentative"))).featureTupleList[0]).tupleNumber
    assertNotNull(read(writer, collection, "tentative").featureTupleList[0]?.tuple)
    assertNull(Naksha.cache[created])
    fixture.storage.newReadSession().use { reader ->
      assertEquals(0, read(reader, collection, "tentative").length)
      assertNull(Naksha.cache[created])
      writer.rollback()
      assertEquals(0, read(reader, collection, "tentative").length)
      val committed = checkNotNull(write(writer, Write().createFeature(collection,
        NakshaFeature("tentative").apply { properties.setRaw("state", "committed") })).featureTupleList[0]).tupleNumber
      writer.commit()
      assertEquals(committed, read(reader, collection, "tentative").featureTupleList[0]?.tupleNumber)
      val updated = checkNotNull(write(writer, Write().updateFeature(collection,
        NakshaFeature("tentative").apply { properties.setRaw("state", "pending") }, false)).featureTupleList[0]).tupleNumber
      assertEquals("pending", read(writer, collection, "tentative").featureTupleList[0]?.feature?.properties?.getRaw("state"))
      assertNull(Naksha.cache[updated])
      assertEquals("committed", read(reader, collection, "tentative").featureTupleList[0]?.feature?.properties?.getRaw("state"))
      writer.rollback()
      assertNull(Naksha.cache[created])
      assertNull(Naksha.cache[updated])
      assertEquals(committed, read(reader, collection, "tentative").featureTupleList[0]?.tupleNumber)
    }
  }

  @Test
  fun `existing read only storage sees committed metadata after explicit refresh`() = withWriter { fixture, writer, maps ->
    val config = otherConfig(fixture)
    try {
      val other = Naksha.useStorage(config) as ImlStorage
      assertFalse(other.config.writeEnabled)
      other.newReadSession().use { reader ->
        val mapId = ownedMap(maps)
        val collection = NakshaCollection("items", mapId)
        assertNull(reader.getCatalogById(mapId))
        writer.executeWrite(WriteRequest().add(Write().createMap(NakshaCatalog(mapId)))
          .add(Write().createCollection(collection)).add(Write().createFeature(collection, NakshaFeature("visible"))))
        other.refreshMetadata(reader.options)
        assertNull(reader.getCatalogById(mapId))
        writer.commit()
        assertNull(reader.getCatalogById(mapId))
        other.refreshMetadata(reader.options)
        assertEquals(fixture.storage.generation, other.generation)
        assertNotNull(reader.getCatalogById(mapId))
        assertEquals(1, read(reader, collection, "visible").length)
      }
    } finally { Naksha.removeStorage(config) }
  }

  private fun write(session: IWriteSession, operation: Write): SuccessResponse =
    session.executeWrite(WriteRequest().add(operation).apply { returnResults = true }) as SuccessResponse

  private fun read(session: IReadSession, collection: NakshaCollection, id: String): SuccessResponse {
    val response = session.executeRead(ReadFeatures().apply {
      catalogId = collection.catalogId
      collectionId = collection.id
      featureIds.add(id)
    }) as SuccessResponse
    session.loadTuples(response.featureTupleList)
    return response
  }

  private fun ownedMap(maps: MutableList<String>): String =
    "imll_${UUID.randomUUID().toString().replace("-", "")}".also { maps.add(it) }

  private fun otherConfig(fixture: ImlTestFixture) = ImlConfig("iml-peer-${UUID.randomUUID()}", fixture.config.jdbcUrl).apply {
    schema = fixture.schema
    metadataSchema = fixture.metadataSchema
    hardCap = 100
  }

  private fun withWriter(block: (ImlTestFixture, IWriteSession, MutableList<String>) -> Unit) = ImlTestFixture.withFixture { fixture ->
    fixture.config.writeEnabled = true
    val context = NakshaContext.currentContext()
    val previous = context.su
    val maps = ArrayList<String>()
    context.su = true
    try {
      fixture.storage.newWriteSession(SessionOptions(appId = "iml-lifecycle-test", author = "lifecycle")).use { writer ->
        try { block(fixture, writer, maps) } finally {
          writer.rollback()
          for (map in maps) {
            if (writer.getCatalogById(map) != null) write(writer, Write().deleteMapById(map))
          }
          writer.commit()
        }
      }
    } finally {
      try {
        // Only these test-generated names are eligible for fallback schema cleanup.
        for (map in maps) fixture.connection.createStatement().use {
          it.execute("DROP SCHEMA IF EXISTS ${ImlMetadata.quote(map)} CASCADE")
        }
      } finally { context.su = previous }
    }
  }
}
