@file:Suppress("OPT_IN_USAGE", "DEPRECATION")
@file:OptIn(naksha.model.v30_experimental::class)

package naksha.iml

import java.util.UUID
import naksha.base.Action
import naksha.base.NakshaError
import naksha.base.NakshaException
import naksha.base.Platform
import naksha.base.Version
import naksha.geo.SpPoint
import naksha.model.IReadSession
import naksha.model.NakshaContext
import naksha.model.SessionOptions
import naksha.model.objects.IndexList
import naksha.model.objects.Member
import naksha.model.objects.MemberList
import naksha.model.objects.MemberType
import naksha.model.objects.NakshaCatalog
import naksha.model.objects.NakshaCollection
import naksha.model.objects.NakshaFeature
import naksha.model.objects.StoreMode
import naksha.model.objects.XyzMembers
import naksha.model.request.ReadFeatures
import naksha.model.request.SuccessResponse
import naksha.model.request.Write
import naksha.model.request.WriteRequest
import org.junit.jupiter.api.Assertions.assertAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.function.Executable

class ImlExoticWriteTest {
  @Test
  fun `returned committed and historical tombstones retain timestamps through recreation`() = withWriter { fixture, writer, maps ->
    fun metadata(feature: NakshaFeature?, createdAt: Long, updatedAt: Long, author: String, action: Action) {
      val xyz = checkNotNull(feature).properties.xyz
      assertEquals(createdAt, (xyz.getRaw("createdAt") as? Number)?.toLong())
      assertEquals(updatedAt, (xyz.getRaw("updatedAt") as? Number)?.toLong())
      assertEquals(author, xyz.getRaw("author"))
      assertEquals(action.toString(), xyz.getRaw("action"))
    }

    val collection = createCollection(writer, maps)
    writer.commit()
    val created = checkNotNull(write(writer, Write().createFeature(collection, feature("timestamps", "original"))).featureTupleList[0])
    val createdAt = writer.useTransaction().time
    metadata(created.feature, createdAt, createdAt, "exotic", Action.CREATE)
    writer.commit()

    fixture.storage.newWriteSession(SessionOptions(appId = "iml-exotic-tombstone", author = "deleter")).use { next ->
      val deleted = checkNotNull(write(next, Write().deleteFeatureById(collection, "timestamps", Version(created.tupleNumber.version))).featureTupleList[0])
      val deletedAt = next.useTransaction().time
      metadata(deleted.feature, createdAt, deletedAt, "deleter", Action.DELETE)
      assertNull(deleted.feature?.geometry)
      assertEquals(0, read(next, collection, "timestamps").length)
      val pending = read(next, collection, "timestamps", deleted = true)
      assertEquals(1, pending.length)
      assertEquals(deleted.tupleNumber, pending.featureTupleList[0]?.tupleNumber)
      metadata(pending.featureTupleList[0]?.feature, createdAt, deletedAt, "deleter", Action.DELETE)
      next.commit()

      fixture.storage.newReadSession().use { reader ->
        val tombstone = read(reader, collection, "timestamps", deleted = true)
        assertEquals(1, tombstone.length)
        assertEquals(deleted.tupleNumber, tombstone.featureTupleList[0]?.tupleNumber)
        metadata(tombstone.featureTupleList[0]?.feature, createdAt, deletedAt, "deleter", Action.DELETE)
      }

      val recreated = checkNotNull(write(next, Write().createFeature(collection, feature("timestamps", "recreated"))).featureTupleList[0])
      val recreatedAt = next.useTransaction().time
      metadata(recreated.feature, createdAt, recreatedAt, "deleter", Action.CREATE)
      val mixed = write(next,
        Write().createFeature(collection, feature("same-tx-timestamps", "first")),
        Write().deleteFeatureById(collection, "same-tx-timestamps"),
        Write().createFeature(collection, feature("same-tx-timestamps", "last")))
      assertEquals(3, mixed.length)
      for (i in 0 until mixed.length) {
        metadata(mixed.featureTupleList[i]?.feature, recreatedAt, recreatedAt, "deleter", if (i == 1) Action.DELETE else Action.CREATE)
      }
      assertEquals(mixed.featureTupleList[0]?.tupleNumber, mixed.featureTupleList[2]?.tupleNumber)
      next.commit()

      fixture.storage.newReadSession().use { reader ->
        val history = reader.executeRead(ReadFeatures().apply {
          catalogId = collection.catalogId
          collectionId = collection.id
          featureIds.add("timestamps")
          queryHistory = true
          queryDeleted = true
          versions = 3
        }) as SuccessResponse
        reader.loadTuples(history.featureTupleList)
        assertEquals(3, history.length)
        val expected = arrayOf(created, deleted, recreated)
        val times = longArrayOf(createdAt, deletedAt, recreatedAt)
        val authors = arrayOf("exotic", "deleter", "deleter")
        val seen = HashSet<naksha.base.TupleNumber>()
        for (entry in history.featureTupleList) {
          val actual = checkNotNull(entry)
          val index = expected.indexOfFirst { it.tupleNumber == actual.tupleNumber }
          assertTrue(index >= 0, "Unexpected historical identity ${actual.tupleNumber}")
          assertTrue(seen.add(actual.tupleNumber))
          metadata(actual.feature, createdAt, times[index], authors[index], expected[index].tupleNumber.action)
        }
        val sameTx = read(reader, collection, "same-tx-timestamps", history = true, deleted = true)
        assertEquals(1, sameTx.length)
        metadata(sameTx.featureTupleList[0]?.feature, recreatedAt, recreatedAt, "deleter", Action.CREATE)
        assertEquals("last", sameTx.featureTupleList[0]?.feature?.properties?.getRaw("state"))
      }
    }
  }

  @Test
  fun `mixed batch create delete recreate of one ID keeps only the final transaction state`() = withWriter { fixture, writer, maps ->
    val collection = createCollection(writer, maps)
    writer.commit()
    val result = write(writer,
      Write().createFeature(collection, feature("same", "first")),
      Write().createFeature(collection, feature("neighbor", "untouched")),
      Write().deleteFeatureById(collection, "same"),
      Write().createFeature(collection, feature("same", "last")),
      Write().upsertFeature(collection, feature("neighbor", "updated")))
    assertEquals(5, result.length)
    val created = checkNotNull(result.featureTupleList[0]).tupleNumber
    val deleted = checkNotNull(result.featureTupleList[2]).tupleNumber
    val recreated = checkNotNull(result.featureTupleList[3]).tupleNumber
    assertEquals(created, recreated)
    assertEquals(created.featureNumber, deleted.featureNumber)
    assertEquals(Action.DELETE, deleted.action)
    assertEquals(Action.CREATE, recreated.action)
    assertEquals(2, writer.useTransaction().featuresModified)
    assertEquals("first", result.featureTupleList[0]?.feature?.properties?.getRaw("state"))
    assertEquals("last", read(writer, collection, "same").featureTupleList[0]?.feature?.properties?.getRaw("state"))
    writer.commit()
    fixture.storage.newReadSession().use { reader ->
      val history = read(reader, collection, "same", history = true, deleted = true)
      assertEquals(1, history.length)
      assertEquals(recreated, history.featureTupleList[0]?.tupleNumber)
      assertEquals("updated", read(reader, collection, "neighbor").featureTupleList[0]?.feature?.properties?.getRaw("state"))
    }
  }

  @Test
  fun `rolled back update identities conflict while the restored committed identity remains valid`() = withWriter { fixture, writer, maps ->
    val collection = createCollection(writer, maps)
    val committed = checkNotNull(write(writer, Write().createFeature(collection, feature("atomic", "committed"))).featureTupleList[0]).tupleNumber
    writer.commit()
    val tentative = checkNotNull(write(writer, Write().updateFeature(collection, feature("atomic", "tentative"), false)).featureTupleList[0]).tupleNumber
    writer.rollback()
    assertNotEquals(committed, tentative)
    for (operation in arrayOf(
      Write().deleteFeatureById(collection, "atomic", Version(tentative.version)),
      Write().updateFeature(collection, feature("atomic", "wrong"), false).withAtomic(true).withTupleNumber(tentative))) {
      val failure = assertThrows(NakshaException::class.java) { write(writer, operation) }
      assertEquals(NakshaError.CONFLICT, failure.error.code)
      assertEquals(0, writer.useTransaction().featuresModified)
      assertEquals(committed, read(writer, collection, "atomic").featureTupleList[0]?.tupleNumber)
    }
    write(writer, Write().updateFeature(collection, feature("atomic", "accepted"), false).withAtomic(true).withTupleNumber(committed))
    writer.commit()
    fixture.storage.newReadSession().use { reader ->
      assertEquals(2, read(reader, collection, "atomic", history = true).length)
      assertEquals("accepted", read(reader, collection, "atomic").featureTupleList[0]?.feature?.properties?.getRaw("state"))
    }
  }

  @Test
  fun `quoted Unicode and canonically distinct IDs stay isolated through mixed writes`() = withWriter { fixture, writer, maps ->
    val collection = createCollection(writer, maps)
    val ids = arrayOf("\u00e9", "e\u0301", "\u96ea\ud83d\ude80'\"\\; --\n\t", "00123")
    for (i in ids.indices) write(writer, Write().createFeature(collection, feature(ids[i], "value-$i")))
    writer.commit()
    write(writer,
      Write().updateFeature(collection, feature(ids[0], "changed"), false),
      Write().deleteFeatureById(collection, ids[1]),
      Write().purgeFeatureById(collection, ids[2]))
    writer.rollback()
    for (i in ids.indices) assertEquals("value-$i", read(writer, collection, ids[i]).featureTupleList[0]?.feature?.properties?.getRaw("state"))
    write(writer,
      Write().updateFeature(collection, feature(ids[0], "changed"), false),
      Write().deleteFeatureById(collection, ids[1]),
      Write().purgeFeatureById(collection, ids[2]))
    writer.commit()
    fixture.storage.newReadSession().use { reader ->
      assertEquals("changed", read(reader, collection, ids[0]).featureTupleList[0]?.feature?.properties?.getRaw("state"))
      assertEquals(0, read(reader, collection, ids[1]).length)
      assertEquals(ids[1], read(reader, collection, ids[1], deleted = true).featureTupleList[0]?.feature?.id)
      assertEquals(0, read(reader, collection, ids[2], history = true, deleted = true).length)
      assertEquals("value-3", read(reader, collection, ids[3]).featureTupleList[0]?.feature?.properties?.getRaw("state"))
    }
  }

  @Test
  fun `large nested properties survive point null and empty geometry collection revisions`() = withWriter { fixture, writer, maps ->
    val collection = createCollection(writer, maps)
    val text = "quote'\"\\\n\u96ea".repeat(12000)
    val nested = Platform.fromJSON("""{"array":[null,true,{"deep":{"values":[1,2,3],"empty":[]}}],"object":{},"fraction":0.125}""")
    val input = NakshaFeature("payload").withGeometry(SpPoint(12.25, 34.5, 6.75)).apply {
      properties.setRaw("text", text)
      properties.setRaw("nested", nested)
    }
    val original = Platform.toJSON(input)
    write(writer, Write().createFeature(collection, input))
    assertEquals(original, Platform.toJSON(input))
    writer.commit()
    for (geometry in arrayOf<Any?>(null, Platform.fromJSON("""{"type":"GeometryCollection","geometries":[]}"""))) {
      input.setRaw("geometry", geometry)
      val before = Platform.toJSON(input)
      write(writer, Write().updateFeature(collection, input, false))
      assertEquals(before, Platform.toJSON(input))
      writer.commit()
      fixture.storage.newReadSession().use { reader ->
        val loaded = checkNotNull(read(reader, collection, input.id).featureTupleList[0]?.feature)
        assertEquals(text, loaded.properties.getRaw("text"))
        fixture.connection.prepareStatement("SELECT ?::jsonb = ?::jsonb").use {
          it.setString(1, Platform.toJSON(nested))
          it.setString(2, Platform.toJSON(loaded.properties.getRaw("nested")))
          it.executeQuery().use { rows -> assertTrue(rows.next()); assertTrue(rows.getBoolean(1), "Nested JSON content changed") }
        }
        if (geometry == null) assertNull(loaded.geometry) else {
          assertEquals("GeometryCollection", loaded.geometry?.getRaw("type"))
          assertEquals("[]", Platform.toJSON(loaded.geometry?.getRaw("geometries")))
        }
      }
    }
    val history = read(writer, collection, input.id, history = true)
    assertEquals(3, history.length)
    var points = 0
    var nulls = 0
    var collections = 0
    for (entry in history.featureTupleList) {
      val loaded = checkNotNull(entry?.feature)
      assertEquals(text, loaded.properties.getRaw("text"))
      when (loaded.geometry?.getRaw("type")) {
        "Point" -> { points++; assertEquals(6.75, loaded.geometry?.asPoint()?.z) }
        "GeometryCollection" -> collections++
        null -> nulls++
      }
    }
    assertEquals(listOf(1, 1, 1), listOf(points, nulls, collections))
  }

  @Test
  fun `forced retention metadata changes and history only purge roll back atomically`() = withWriter { fixture, writer, maps ->
    val collection = createCollection(writer, maps).apply {
      members = MemberList(XyzMembers.ALL).apply { add(Member("custom")) }
    }
    write(writer, Write().updateCollection(collection, false))
    for (id in arrayOf("live", "gone")) write(writer, Write().createFeature(collection, feature(id, "first")))
    writer.commit()
    write(writer, Write().updateFeature(collection, feature("live", "second"), false), Write().deleteFeatureById(collection, "gone"))
    writer.commit()
    val changed = NakshaCollection(collection.id, collection.catalogId, storeHistory = StoreMode.OFF, storeDeleted = StoreMode.OFF).apply {
      members = MemberList(XyzMembers.ALL)
      indices = IndexList()
    }
    assertEquals(NakshaError.ILLEGAL_ARGUMENT, assertThrows(NakshaException::class.java) {
      write(writer, Write().updateCollection(changed, false))
    }.error.code)
    write(writer, Write().updateCollection(changed, false).withForce(true))
    assertEquals(1, read(writer, collection, "live", history = true).length)
    assertEquals(0, read(writer, collection, "gone", history = true, deleted = true).length)
    writer.rollback()
    assertEquals(StoreMode.ON, writer.getCollectionById(checkNotNull(writer.getCatalogById(checkNotNull(collection.catalogId))), collection.id)?.storeHistory)
    assertEquals(2, read(writer, collection, "live", history = true).length)
    assertEquals(2, read(writer, collection, "gone", history = true, deleted = true).length)
    changed.storeHistory = StoreMode.ON
    write(writer, Write().updateCollection(changed, false).withForce(true))
    writer.commit()
    assertEquals(0, read(writer, collection, "gone").length)
    val retained = checkNotNull(read(writer, collection, "gone", history = true).featureTupleList[0]).tupleNumber
    write(writer, Write().purgeFeatureById(collection, "gone").withAtomic(true).withTupleNumber(retained))
    assertEquals(0, read(writer, collection, "gone", history = true, deleted = true).length)
    writer.rollback()
    assertEquals(retained, read(writer, collection, "gone", history = true).featureTupleList[0]?.tupleNumber)
    write(writer, Write().purgeFeatureById(collection, "gone").withAtomic(true).withTupleNumber(retained))
    writer.commit()
    fixture.storage.newReadSession().use { reader ->
      assertEquals(0, read(reader, collection, "gone", history = true, deleted = true).length)
      assertEquals(2, read(reader, collection, "live", history = true).length)
    }
  }

  @Test
  fun `boolean and numeric custom members retain boundary precision and reject coercions`() = withWriter { fixture, writer, maps ->
    val collection = createCollection(writer, maps).apply {
      members = MemberList(XyzMembers.ALL).apply {
        add(Member("enabled", MemberType.BOOLEAN))
        add(Member("integer", MemberType.INT64))
        add(Member("single", MemberType.FLOAT32))
        add(Member("double", MemberType.FLOAT64))
      }
      indices = IndexList()
    }
    write(writer, Write().updateCollection(collection, false))
    val integers = longArrayOf(Long.MIN_VALUE, 9007199254740993L, Long.MAX_VALUE)
    val singles = floatArrayOf(Float.MIN_VALUE, 0.1f, Float.MAX_VALUE)
    val doubles = doubleArrayOf(Double.MIN_VALUE, 1.0000000000000002, Double.MAX_VALUE)
    val checks = ArrayList<Executable>()
    for (i in integers.indices) {
      val input = NakshaFeature("number-$i").apply {
        properties.setRaw("enabled", i != 1)
        properties.setRaw("integer", integers[i])
        properties.setRaw("single", singles[i])
        properties.setRaw("double", doubles[i])
      }
      val result = write(writer, Write().createFeature(collection, input))
      val book = checkNotNull(result.featureTupleList[0]?.tuple?.membersBook)
      assertEquals(i != 1, book.get("enabled"))
      assertEquals(integers[i], book.get("integer"))
      checks.add(Executable { assertEquals(singles[i], book.get("single"), "Write FLOAT32 number-$i") })
      checks.add(Executable { assertEquals(doubles[i], book.get("double"), "Write FLOAT64 number-$i") })
    }
    writer.commit()
    for (invalid in arrayOf<Any>("true", 1, "false")) {
      assertEquals(NakshaError.ILLEGAL_ARGUMENT, assertThrows(NakshaException::class.java) {
        write(writer, Write().createFeature(collection, NakshaFeature("invalid").apply { properties.setRaw("enabled", invalid) }))
      }.error.code)
      assertEquals(0, read(writer, collection, "invalid").length)
    }
    writer.rollback()
    fixture.storage.newReadSession().use { reader ->
      for (i in integers.indices) {
        val book = checkNotNull(read(reader, collection, "number-$i").featureTupleList[0]?.tuple?.membersBook)
        assertEquals(i != 1, book.get("enabled"))
        assertEquals(integers[i], book.get("integer"))
        checks.add(Executable { assertEquals(singles[i], book.get("single"), "Reload FLOAT32 number-$i") })
        checks.add(Executable { assertEquals(doubles[i], book.get("double"), "Reload FLOAT64 number-$i") })
      }
    }
    assertAll(checks)
  }

  @Test
  fun `session captures context author and fallback app ID across later context mutations`() = withWriter { fixture, writer, maps ->
    val collection = createCollection(writer, maps)
    writer.commit()
    val context = NakshaContext.currentContext()
    val previousAuthor = context.author
    val previousApp = context.appId
    try {
      for (author in arrayOf<String?>("captured'\u96ea", null)) {
        context.author = author
        context.appId = "captured-app"
        fixture.storage.newWriteSession().use { captured ->
          context.author = "later-author"
          context.appId = "later-app"
          val id = if (author == null) "fallback" else "author"
          val actor = author ?: "captured-app"
          for (state in arrayOf("first", "second")) {
            val input = feature(id, state).apply { properties.xyz.setRaw("author", "spoofed") }
            val operation = if (state == "first") Write().createFeature(collection, input) else Write().updateFeature(collection, input, false)
            assertEquals(actor, write(captured, operation).featureTupleList[0]?.feature?.properties?.xyz?.getRaw("author"))
            assertEquals("spoofed", input.properties.xyz.getRaw("author"))
            captured.commit()
          }
          fixture.storage.newReadSession().use { reader ->
            val history = read(reader, collection, id, history = true)
            assertEquals(2, history.length)
            for (entry in history.featureTupleList) assertEquals(actor, entry?.feature?.properties?.xyz?.getRaw("author"))
          }
        }
      }
    } finally {
      context.author = previousAuthor
      context.appId = previousApp
    }
  }

  @Test
  fun `overlapping transaction lock leases release independently and at every boundary`() = withWriter { fixture, writer, _ ->
    val session = writer.acquireSessionLock("persistent")
    for (commit in arrayOf(true, false)) {
      val first = writer.acquireTransactionLock("shared")
      val duplicate = writer.acquireTransactionLock("shared")
      val other = writer.acquireTransactionLock("other")
      assertFalse(tryLock(fixture, "shared"))
      assertFalse(tryLock(fixture, "other"))
      first.close()
      first.close()
      assertTrue(first.isClosed())
      assertFalse(duplicate.isClosed())
      assertFalse(tryLock(fixture, "shared"))
      other.close()
      assertTrue(tryLock(fixture, "other"))
      val reacquired = writer.acquireTransactionLock("other")
      assertFalse(tryLock(fixture, "other"))
      if (commit) writer.commit() else writer.rollback()
      assertTrue(duplicate.isClosed())
      assertTrue(reacquired.isClosed())
      duplicate.close()
      assertTrue(tryLock(fixture, "shared"))
      assertTrue(tryLock(fixture, "other"))
      assertFalse(session.isClosed())
      assertFalse(tryLock(fixture, "persistent"))
    }
    session.close()
    assertTrue(tryLock(fixture, "persistent"))
  }

  private fun feature(id: String, state: String) = NakshaFeature(id).apply { properties.setRaw("state", state) }

  private fun write(session: ImlWriteSession, vararg operations: Write): SuccessResponse =
    session.executeWrite(WriteRequest().apply {
      returnResults = true
      strictOrder = true
      for (operation in operations) add(operation)
    }) as SuccessResponse

  private fun read(session: IReadSession, collection: NakshaCollection, id: String, history: Boolean = false, deleted: Boolean = false): SuccessResponse {
    val response = session.executeRead(ReadFeatures().apply {
      catalogId = collection.catalogId
      collectionId = collection.id
      featureIds.add(id)
      queryHistory = history
      queryDeleted = deleted
      if (history) versions = 100
    }) as SuccessResponse
    session.loadTuples(response.featureTupleList)
    return response
  }

  private fun createCollection(writer: ImlWriteSession, maps: MutableList<String>, id: String = "items"): NakshaCollection {
    val map = "imlx_${UUID.randomUUID().toString().replace("-", "")}"
    maps.add(map)
    val collection = NakshaCollection(id, map)
    write(writer, Write().createMap(NakshaCatalog(map)), Write().createCollection(collection))
    return collection
  }

  private fun tryLock(fixture: ImlTestFixture, id: String): Boolean {
    val key = "iml-lock:${fixture.metadataSchema}:$id"
    val acquired = fixture.connection.prepareStatement("SELECT pg_try_advisory_lock(hashtextextended(?,0))").use {
      it.setString(1, key)
      it.executeQuery().use { rows -> assertTrue(rows.next()); rows.getBoolean(1) }
    }
    if (acquired) fixture.connection.prepareStatement("SELECT pg_advisory_unlock(hashtextextended(?,0))").use {
      it.setString(1, key)
      it.executeQuery().use { rows -> assertTrue(rows.next()); assertTrue(rows.getBoolean(1)) }
    }
    return acquired
  }

  private fun withWriter(block: (ImlTestFixture, ImlWriteSession, MutableList<String>) -> Unit) = ImlTestFixture.withFixture { fixture ->
    fixture.config.writeEnabled = true
    val context = NakshaContext.currentContext()
    val previous = context.su
    val maps = ArrayList<String>()
    context.su = true
    try {
      fixture.storage.newWriteSession(SessionOptions(appId = "iml-exotic-test", author = "exotic")).use { writer ->
        try { block(fixture, writer, maps) } finally {
          writer.rollback()
          for (map in maps) {
            check(map.startsWith("imlx_"))
            if (writer.getCatalogById(map) != null) write(writer, Write().deleteMapById(map))
          }
          writer.commit()
          for (map in maps) assertNull(writer.getCatalogById(map))
        }
      }
    } finally { context.su = previous }
  }
}
