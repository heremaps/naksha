package naksha.iml

import naksha.base.Action
import naksha.base.Id
import naksha.base.NakshaError
import naksha.base.NakshaException
import naksha.base.TupleNumber
import naksha.base.Version
import naksha.geo.SpBoundingBox
import naksha.geo.SpPoint
import naksha.model.FETCH_ALL
import naksha.model.Naksha
import naksha.model.objects.NakshaCollection
import naksha.model.objects.NakshaFeature
import naksha.model.objects.StandardMembers
import naksha.model.objects.XyzMembers
import naksha.model.request.FeatureTuple
import naksha.model.request.ReadCollections
import naksha.model.request.ReadFeatures
import naksha.model.request.ReadMaps
import naksha.model.request.ReadRequest
import naksha.model.request.ResultFilter
import naksha.model.request.SuccessResponse
import naksha.model.request.WriteRequest
import naksha.model.request.ops.And
import naksha.model.request.ops.Equals
import naksha.model.request.ops.Intersects
import naksha.model.request.ops.Not
import naksha.model.request.ops.Or
import naksha.model.request.ops.StartsWith
import naksha.model.request.ops.TagListContains
import naksha.model.request.ops.TagListContainsAllOf
import naksha.model.request.ops.TagListContainsAnyOf
import naksha.model.request.query.SpIntersects
import naksha.model.request.query.TagExists
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@Suppress("DEPRECATION")
class ImlReadIntegrationTest {
  @Test
  fun `metadata exposes only the configured catalog and layers by ID and number`() = ImlTestFixture.withFixture { fixture ->
    fixture.storage.newReadSession().use { session ->
      val catalog = checkNotNull(session.getCatalogById(fixture.schema))
      assertEquals(fixture.schema, session.getCatalogByNumber(Id.textToInt(fixture.schema))?.id)
      assertNull(session.getCatalogById("public"))
      assertNull(session.getCatalogById("missing"))
      val collection = checkNotNull(session.getCollectionById(catalog, fixture.layer))
      assertEquals(fixture.layer, session.getCollectionByNumber(catalog, collection.collectionNumber)?.id)
      assertNull(session.getCollectionById(catalog, "ignored-other-schema"))
      val maps = session.executeRead(ReadMaps(fixture.schema)) as SuccessResponse
      assertEquals(setOf(fixture.schema), ids(maps))
      val collections = session.executeRead(ReadCollections().apply {
        mapId = fixture.schema
        collectionIds.addAll(arrayOf(fixture.layer, fixture.otherLayer))
      }) as SuccessResponse
      assertEquals(setOf(fixture.layer, fixture.otherLayer), ids(collections))
    }
  }

  @Test
  fun `HEAD selection excludes history and tombstones but retains unknown operations`() = ImlTestFixture.withFixture { fixture ->
    fixture.storage.newReadSession().use { session ->
      val response = read(session, fixture)
      assertEquals(5, response.length)
      assertEquals(setOf("00123", ImlTestFixture.INJECTION_ID, "far", "null-geo", "unknown"), ids(response))
      val deleted = read(session, fixture) { queryDeleted = true }
      assertEquals(6, deleted.length)
      val tuples = deleted.featureTupleList
      session.loadTuples(tuples)
      assertEquals(Action.DELETE, checkNotNull(tuples[tuples.indexOf("deleted")]).tupleNumber.action)
      assertEquals(Action.VERSION, checkNotNull(tuples[tuples.indexOf("unknown")]).tupleNumber.action)
    }
  }

  @Test
  fun `numeric and SQL looking feature IDs remain literal strings`() = ImlTestFixture.withFixture { fixture ->
    fixture.storage.newReadSession().use { session ->
      for (id in arrayOf("00123", ImlTestFixture.INJECTION_ID)) {
        assertEquals(setOf(id), ids(read(session, fixture) { queryMembers = Equals(StandardMembers.Id, id) }))
      }
      assertEquals(emptySet<String>(), ids(read(session, fixture) { queryMembers = Equals("id", "123") }))
      assertEquals(5, read(session, fixture).length)
    }
  }

  @Test
  fun `bbox intersection uses relational geometry including lines and excludes nulls`() = ImlTestFixture.withFixture { fixture ->
    fixture.storage.newReadSession().use { session ->
      val response = read(session, fixture) { queryMembers = Intersects(XyzMembers.XyzGeometry, bbox()) }
      assertEquals(setOf("00123", ImlTestFixture.INJECTION_ID, "unknown"), ids(response))
    }
  }

  @Test
  fun `author equality prefix and wildcard escaping use relational values`() = ImlTestFixture.withFixture { fixture ->
    fixture.storage.newReadSession().use { session ->
      assertEquals(setOf("00123"), ids(read(session, fixture) { queryMembers = Equals(XyzMembers.XyzAuthor, "alice") }))
      assertEquals(setOf("00123", ImlTestFixture.INJECTION_ID, "unknown"), ids(read(session, fixture) {
        queryMembers = StartsWith(XyzMembers.XyzAuthor, "al")
      }))
      assertEquals(setOf(ImlTestFixture.INJECTION_ID), ids(read(session, fixture) {
        queryMembers = StartsWith("author", "al_%")
      }))
      assertEquals(setOf("null-geo"), ids(read(session, fixture) { queryMembers = Equals("author", null) }))
    }
  }

  @Test
  fun `logical combinations and legacy IDs plus spatial agree`() = ImlTestFixture.withFixture { fixture ->
    fixture.storage.newReadSession().use { session ->
      val modern = read(session, fixture) {
        queryMembers = And(Intersects(XyzMembers.XyzGeometry, bbox()),
          Or(Equals("id", "00123"), Equals("id", ImlTestFixture.INJECTION_ID)), Not(Equals("author", "nobody")))
      }
      val legacy = read(session, fixture) {
        featureIds.addAll(arrayOf("00123", ImlTestFixture.INJECTION_ID, "far"))
        query.spatial = SpIntersects(bbox())
      }
      assertEquals(setOf("00123", ImlTestFixture.INJECTION_ID), ids(modern))
      assertEquals(ids(modern), ids(legacy))
    }
  }

  @Test
  fun `selection returns unloaded tuple identities and explicit load preserves XYZ metadata`() = ImlTestFixture.withFixture { fixture ->
    fixture.storage.newReadSession().use { session ->
      val response = read(session, fixture) { featureIds.add("00123") }
      val tuples = response.featureTupleList
      assertEquals(1, tuples.size)
      val selected = checkNotNull(tuples[0])
      assertNull(selected.tuple)
      assertEquals(tupleNumber(fixture, 11, 29), selected.tupleNumber)
      session.loadTuples(tuples)
      val tuple = checkNotNull(selected.tuple)
      assertEquals(Version.HEAD.number, tuple.nextVersion)
      assertEquals("alice", tuple.getString(XyzMembers.XyzAuthor))
      val feature = checkNotNull(selected.feature)
      assertPreserved(feature)
      val point = feature.geometry as SpPoint
      assertEquals(1.0, point.longitude)
      assertEquals(2.0, point.latitude)
      assertEquals(3.0, point.z)
    }
  }

  @Test
  fun `response features automatically load tuples without losing unknown XYZ fields`() = ImlTestFixture.withFixture { fixture ->
    fixture.storage.newReadSession().use { session ->
      val response = read(session, fixture) { featureIds.add("00123") }
      assertNull(checkNotNull(response.featureTupleList[0]).tuple)
      val feature = checkNotNull(response.features[0])
      assertEquals("00123", feature.id)
      assertPreserved(feature)
      val nullFeature = checkNotNull(read(session, fixture) { featureIds.add("null-geo") }.features[0])
      assertNull(nullFeature.geometry)
      assertNull(nullFeature.properties.xyz.getRaw("author"))
    }
  }

  @Test
  fun `exact loading honors range nulls duplicate identities and storage collection version isolation`() = ImlTestFixture.withFixture { fixture ->
    fixture.storage.newReadSession().use { session ->
      val tn = tupleNumber(fixture, 11, 29)
      val entries = listOf(
        FeatureTuple(tn, null), null, FeatureTuple(tn, null), FeatureTuple(tn, null),
        FeatureTuple(TupleNumber.copy(tn, storageNumber = tn.databaseNumber + 1), null),
        FeatureTuple(TupleNumber.copy(tn, version = 28), null),
        FeatureTuple(TupleNumber.copy(tn, featureNumber = 9999), null),
        FeatureTuple(tupleNumber(fixture, 11, 4, fixture.otherLayer), null), FeatureTuple(tn, null)
      )
      session.loadTuples(entries, 1, 8)
      assertNull(entries[0]?.tuple)
      assertNotNull(entries[2]?.tuple)
      assertNotNull(entries[3]?.tuple)
      assertNull(entries[4]?.tuple)
      assertNull(entries[5]?.tuple)
      assertNull(entries[6]?.tuple)
      assertEquals("second-layer", entries[7]?.id)
      assertNull(entries[8]?.tuple)
    }
  }

  @Test
  fun `exact historical state loads and selected HEAD remains loadable after becoming history`() = ImlTestFixture.withFixture { fixture ->
    fixture.storage.newReadSession().use { session ->
      val selected = read(session, fixture) { featureIds.add("00123") }.featureTupleList
      fixture.connection.createStatement().use { it.executeUpdate("UPDATE ${fixture.sqlTable} SET next_version = 13 WHERE i = 11") }
      fixture.insert("00123", 13, "U", "new-author", "POINT Z (20 20 20)", 17)
      val old = FeatureTuple(tupleNumber(fixture, 1, 4), null)
      session.loadTuples(listOf(old))
      assertEquals("old-author", old.tuple?.getString(XyzMembers.XyzAuthor))
      session.loadTuples(selected)
      assertEquals("alice", checkNotNull(selected[0]).tuple?.getString(XyzMembers.XyzAuthor))
      assertEquals(29L, checkNotNull(selected[0]).tupleNumber.version)
      assertEquals(1, read(session, fixture) { featureIds.add("00123") }.length)
    }
  }

  @Test
  fun `exact loading uses cached tuples across sessions without requiring the source row`() = ImlTestFixture.withFixture { fixture ->
    val storage = fixture.storage
    val tn = tupleNumber(fixture, 11, 29)
    storage.newReadSession().use { session ->
      val entry = FeatureTuple(tn, null)
      session.loadTuples(listOf(entry))
      Naksha.cache.store(checkNotNull(entry.tuple))
    }
    fixture.connection.createStatement().use { it.executeUpdate("DELETE FROM ${fixture.sqlTable} WHERE i = 11") }
    storage.newReadSession().use { session ->
      val entry = FeatureTuple(tn, null)
      session.loadTuples(listOf(entry))
      assertEquals("00123", entry.id)
      assertPreserved(checkNotNull(entry.feature))
    }
  }

  @Test
  fun `unbounded request returns all HEAD rows while limit and hard cap bound results`() = ImlTestFixture.withFixture { fixture ->
    fixture.storage.newReadSession().use { session ->
      assertEquals(5, read(session, fixture).length)
      assertEquals(2, read(session, fixture) { limit = 2 }.length)
    }
    Naksha.removeStorage(fixture.config)
    fixture.config.hardCap = 3
    fixture.storage.newReadSession().use { session ->
      assertEquals(3, read(session, fixture).length)
      assertEquals(3, read(session, fixture) { limit = 100 }.length)
      assertEquals(1, read(session, fixture) { limit = 1 }.length)
    }
  }

  @Test
  fun `metadata snapshot is fixed until storage is recreated and identifiers are quoted`() = ImlTestFixture.withFixture(quotedNames = true) { fixture ->
    val storage = fixture.storage
    fixture.addLayer("late-layer", fixture.otherTable)
    storage.newReadSession().use { session ->
      val catalog = checkNotNull(session.getCatalogById(fixture.schema))
      assertNull(session.getCollectionById(catalog, "late-layer"))
      assertEquals(setOf("second-layer"), ids(read(session, fixture, fixture.otherLayer)))
      assertEquals(setOf(ImlTestFixture.INJECTION_ID), ids(read(session, fixture) {
        queryMembers = Equals("id", ImlTestFixture.INJECTION_ID)
      }))
    }
    Naksha.removeStorage(fixture.config)
    fixture.storage.newReadSession().use { session ->
      val catalog = checkNotNull(session.getCatalogById(fixture.schema))
      assertNotNull(session.getCollectionById(catalog, "late-layer"))
    }
  }

  @Test
  fun `result filters historical queries and write requests are rejected`() = ImlTestFixture.withFixture { fixture ->
    fixture.storage.newReadSession().use { session ->
      val filtered = request(fixture).apply {
        resultFilters.add(object : ResultFilter {
          override fun filter(featureTuple: FeatureTuple): FeatureTuple = featureTuple
        })
      }
      val filterError = assertThrows(NakshaException::class.java) { session.executeRead(filtered) }
      assertEquals(NakshaError.UNSUPPORTED_OPERATION, filterError.error.code)
      val historyError = assertThrows(NakshaException::class.java) {
        read(session, fixture) { queryHistory = true }
      }
      assertEquals(NakshaError.UNSUPPORTED_OPERATION, historyError.error.code)
      val writeError = assertThrows(NakshaException::class.java) { session.execute(WriteRequest()) }
      assertEquals(NakshaError.UNSUPPORTED_OPERATION, writeError.error.code)
    }
  }

  @Test
  fun `removed selected state stays missing rather than materializing a newer state`() = ImlTestFixture.withFixture { fixture ->
    fixture.storage.newReadSession().use { session ->
      val response = read(session, fixture) { featureIds.add("00123") }
      val selected = checkNotNull(response.featureTupleList[0])
      assertNull(selected.tuple)
      fixture.connection.createStatement().use { it.executeUpdate("DELETE FROM ${fixture.sqlTable} WHERE i = 11") }
      fixture.insert("00123", 13, "U", "replacement-author", null, 11)
      session.loadTuples(response.featureTupleList)
      assertNull(selected.tuple)
      assertNull(selected.feature)
      assertEquals(29L, selected.tupleNumber.version)
      val current = checkNotNull(read(session, fixture) { featureIds.add("00123") }.features[0])
      assertEquals("replacement-author", current.properties.xyz.getRaw("author"))
    }
  }

  @Test
  fun `config replacement clears cached tuples when a layer maps to another physical table`() = ImlTestFixture.withFixture { fixture ->
    val original = fixture.storage
    val tn = tupleNumber(fixture, 11, 29)
    original.newReadSession().use { session ->
      val selected = FeatureTuple(tn, null)
      session.loadTuples(listOf(selected))
      assertEquals("00123", selected.id)
      Naksha.cache.store(checkNotNull(selected.tuple))
      assertNotNull(Naksha.cache[tn])
    }
    fixture.insert("replacement-data", 7, "U", "different-author", null, 11, target = fixture.otherTable)
    fixture.connection.prepareStatement("UPDATE ${fixture.sqlMetadata} SET h_id = ? WHERE id = ? AND schem = ?").use {
      it.setString(1, fixture.otherTable)
      it.setString(2, fixture.layer)
      it.setString(3, fixture.schema)
      assertEquals(1, it.executeUpdate())
    }
    val changed = fixture.config.copy<ImlConfig>(recursive = true)
    changed.jdbcUrl += (if (changed.jdbcUrl.contains('?')) "&" else "?") + "ApplicationName=iml-regression-replacement"
    val replacement = Naksha.useStorage(changed) as ImlStorage
    try {
      assertNotSame(original, replacement)
      assertEquals(original.id, replacement.id)
      assertEquals(original.number, replacement.number)
      assertNull(Naksha.cache[tn])
      replacement.newReadSession().use { session ->
        val selected = FeatureTuple(tn, null)
        session.loadTuples(listOf(selected))
        assertEquals("replacement-data", selected.id)
        assertEquals("different-author", selected.tuple?.getString(XyzMembers.XyzAuthor))
        assertNull(selected.feature?.geometry)
      }
    } finally {
      Naksha.removeStorage(changed)
    }
  }

  @Test
  fun `synthetic collections retain collection feature type after tuple decoding`() = ImlTestFixture.withFixture { fixture ->
    fixture.storage.newReadSession().use { session ->
      val catalog = checkNotNull(session.getCatalogById(fixture.schema))
      for (id in arrayOf(fixture.layer, fixture.otherLayer, Id.COLLECTIONS_COL_TEXT)) {
        val collection = checkNotNull(session.getCollectionById(catalog, id))
        assertEquals(NakshaCollection.FEATURE_TYPE, collection.featureType)
        val entry = FeatureTuple(checkNotNull(collection.tupleNumber), null)
        session.loadTuples(listOf(entry))
        assertEquals(NakshaCollection.FEATURE_TYPE, checkNotNull(entry.feature).featureType)
        assertEquals(NakshaCollection.FEATURE_TYPE, checkNotNull(entry.tuple).decodeFeature(null).featureType)
      }
      val admin = checkNotNull(session.getCatalogById(Id.ADMIN_CATALOG_TEXT))
      val catalogs = checkNotNull(session.getCollectionById(admin, Id.CATALOGS_COL_TEXT))
      assertEquals(NakshaCollection.FEATURE_TYPE, catalogs.featureType)
      val response = session.executeRead(ReadCollections().withMapId(fixture.schema)) as SuccessResponse
      for (feature in response.features) {
        assertEquals(NakshaCollection.FEATURE_TYPE, checkNotNull(feature).featureType)
      }
    }
  }

  @Test
  fun `ReadCollections and converted ReadFeatures list and select the same synthetic collection IDs`() = ImlTestFixture.withFixture { fixture ->
    fixture.storage.newReadSession().use { session ->
      val all = setOf(fixture.layer, fixture.otherLayer, Id.COLLECTIONS_COL_TEXT)
      val selections = listOf(emptyList(), listOf(Id.COLLECTIONS_COL_TEXT), listOf(fixture.layer, "missing"))
      for (selection in selections) {
        val request = ReadCollections().withMapId(fixture.schema).apply { collectionIds.addAll(selection) }
        val direct = session.executeRead(request) as SuccessResponse
        val converted = session.executeRead(request.toReadFeatures()) as SuccessResponse
        assertEquals(if (selection.isEmpty()) all else all.intersect(selection.toSet()), ids(direct))
        assertEquals(ids(direct), ids(converted))
        assertEquals(direct.length, converted.length)
      }
    }
  }

  @Test
  fun `all metadata request forms reject incomplete fetch modes`() = ImlTestFixture.withFixture { fixture ->
    fixture.storage.newReadSession().use { session ->
      val collections = ReadCollections().withMapId(fixture.schema)
      val requests = listOf<ReadRequest>(ReadMaps(fixture.schema), ReadMaps(fixture.schema).toReadFeatures(),
        collections, collections.toReadFeatures())
      for (request in requests) {
        for (mode in 0 until FETCH_ALL) {
          request.fetchMode = mode
          val error = assertThrows(NakshaException::class.java) { session.executeRead(request) }
          assertEquals(NakshaError.UNSUPPORTED_OPERATION, error.error.code)
        }
        request.fetchMode = FETCH_ALL
        assertTrue(session.executeRead(request).length > 0)
      }
    }
  }

  @Test
  fun `one thousand selected IDs load completely across exact tuple batch boundaries`() = ImlTestFixture.withFixture { fixture ->
    fixture.config.hardCap = 1200
    fixture.connection.createStatement().use {
      assertEquals(1000, it.executeUpdate("INSERT INTO ${fixture.sqlTable} " +
        "(id, version, next_version, operation, author, jsondata, geo, i) " +
        "SELECT 'batch-' || n, 20, 9223372036854775807, 'I', 'batch-author', " +
        "jsonb_build_object('type', 'Feature', 'id', 'batch-' || n, 'properties', '{}'::jsonb), NULL, 1000 + n " +
        "FROM generate_series(1, 1000) AS series(n)"))
    }
    fixture.storage.newReadSession().use { session ->
      val response = read(session, fixture) {
        for (number in 1..1000) featureIds.add("batch-$number")
      }
      assertEquals(1000, response.length)
      val selected = response.featureTupleList
      for (entry in selected) assertNull(checkNotNull(entry).tuple)
      session.loadTuples(selected)
      for (index in 0 until selected.size) {
        val entry = checkNotNull(selected[index])
        assertEquals(tupleNumber(fixture, 1001L + index, 80), entry.tupleNumber)
        assertEquals("batch-${index + 1}", entry.id)
        assertEquals("batch-author", checkNotNull(entry.tuple).getString(XyzMembers.XyzAuthor))
        assertNull(checkNotNull(entry.feature).geometry)
      }
      assertEquals(1000, response.features.size)
    }
  }

  @Test
  fun `modern tag containment combinations negation and legacy TagExists query real JSON tags`() = ImlTestFixture.withFixture { fixture ->
    val special = "red' OR TRUE --"
    fixture.connection.prepareStatement("UPDATE ${fixture.sqlTable} SET jsondata = " +
      "jsonb_set(jsondata, '{properties,@ns:com:here:xyz,tags}', ?::jsonb) WHERE id = ?").use { statement ->
      for ((id, tags) in listOf("far" to "[\"$special\"]", "unknown" to "[\"test\"]", "null-geo" to "[]")) {
        statement.setString(1, tags)
        statement.setString(2, id)
        statement.executeUpdate()
      }
    }
    fixture.storage.newReadSession().use { session ->
      val blue = setOf("00123", ImlTestFixture.INJECTION_ID)
      assertEquals(blue, ids(read(session, fixture) { queryMembers = TagListContains(XyzMembers.XyzTags, "blue") }))
      assertEquals(blue, ids(read(session, fixture) {
        queryMembers = TagListContainsAllOf(XyzMembers.XyzTags, "blue", "test")
      }))
      assertEquals(blue + "far", ids(read(session, fixture) {
        queryMembers = TagListContainsAnyOf(XyzMembers.XyzTags, "blue", special)
      }))
      assertEquals(setOf("far", "null-geo", "unknown"), ids(read(session, fixture) {
        queryMembers = Not(TagListContains(XyzMembers.XyzTags, "blue"))
      }))
      assertEquals(setOf("unknown"), ids(read(session, fixture) {
        queryMembers = And(TagListContains(XyzMembers.XyzTags, "test"), Not(TagListContains(XyzMembers.XyzTags, "blue")))
      }))
      assertEquals(blue, ids(read(session, fixture) { query.tags = TagExists("blue") }))
      assertEquals(setOf("far"), ids(read(session, fixture) { query.tags = TagExists(special) }))
      assertEquals(emptySet<String>(), ids(read(session, fixture) {
        queryMembers = TagListContainsAnyOf(XyzMembers.XyzTags, "missing")
      }))
    }
  }

  private fun request(fixture: ImlTestFixture, layer: String = fixture.layer) = ReadFeatures().apply {
    catalogId = fixture.schema
    collectionId = layer
  }

  @Test
  fun `no cache fetch mode refreshes materialized contents`() = ImlTestFixture.withFixture { fixture ->
    fixture.storage.newReadSession().use { session ->
      assertEquals("alice", read(session, fixture) { featureIds.add("00123") }.features[0]?.properties?.xyz?.getRaw("author"))
      fixture.connection.createStatement().use { it.executeUpdate("UPDATE ${fixture.sqlTable} SET author = 'refreshed' WHERE i = 11") }
      val response = read(session, fixture) {
        featureIds.add("00123")
        fetchMode = naksha.model.FETCH_ALL or naksha.model.NO_CACHE_BIT
      }
      assertNotNull(response.featureTupleList[0]?.tuple)
      assertEquals("refreshed", response.features[0]?.properties?.xyz?.getRaw("author"))
    }
  }

  @Test
  fun `bodyless tombstone materializes but missing live body fails explicitly`() = ImlTestFixture.withFixture { fixture ->
    fixture.connection.createStatement().use { it.executeUpdate("UPDATE ${fixture.sqlTable} SET jsondata = NULL, geo = NULL WHERE id = 'deleted'") }
    fixture.storage.newReadSession().use { session ->
      val response = read(session, fixture) {
        featureIds.add("deleted")
        queryDeleted = true
      }
      val entry = checkNotNull(response.featureTupleList[0])
      session.loadTuples(listOf(entry))
      assertEquals("deleted", entry.feature?.id)
      assertEquals(Action.DELETE.toString(), entry.feature?.properties?.xyz?.getRaw("action"))
      assertNull(entry.feature?.geometry)
      fixture.connection.createStatement().use { it.executeUpdate("UPDATE ${fixture.sqlTable} SET jsondata = NULL WHERE id = 'far'") }
      val error = assertThrows(NakshaException::class.java) {
        read(session, fixture) { featureIds.add("far") }.features
      }
      assertEquals(NakshaError.ILLEGAL_ARGUMENT, error.error.code)
    }
  }

  private fun read(session: ImlReadSession, fixture: ImlTestFixture, layer: String = fixture.layer,
    configure: ReadFeatures.() -> Unit = {}): SuccessResponse =
    session.executeRead(request(fixture, layer).apply(configure)) as SuccessResponse

  private fun ids(response: SuccessResponse): Set<String> {
    val result = mutableSetOf<String>()
    for (feature in response.features) result.add(checkNotNull(feature?.id))
    return result
  }

  private fun tupleNumber(fixture: ImlTestFixture, number: Long, version: Long, layer: String = fixture.layer) =
    TupleNumber(fixture.storage.number, Id.textToInt(fixture.schema), Id.textToInt(layer), number, version)

  private fun bbox() = SpBoundingBox(0.5, 1.5, 2.5, 2.5).toPolygon()

  private fun assertPreserved(feature: NakshaFeature) {
    val xyz = feature.properties.xyz
    assertEquals("keep-me", xyz.getRaw("unrecognized"))
    assertNotNull(xyz.getRaw("nestedUnknown"))
    assertEquals(1000L, (xyz.getRaw("createdAt") as Number).toLong())
    assertEquals(2000L, (xyz.getRaw("updatedAt") as Number).toLong())
    assertEquals(1500L, (xyz.getRaw("authorTimestamp") as Number).toLong())
    assertEquals(listOf("blue", "test"), feature.properties.xyz.tags.toList())
    assertEquals("kept", feature.properties.getRaw("custom"))
    assertTrue(xyz.getRaw("uuid") is String)
  }
}
