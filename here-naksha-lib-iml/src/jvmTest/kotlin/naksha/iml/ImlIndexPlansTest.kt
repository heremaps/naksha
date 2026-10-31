@file:Suppress("OPT_IN_USAGE", "DEPRECATION")
@file:OptIn(naksha.model.v30_experimental::class)

package naksha.iml

import java.sql.Connection
import java.sql.PreparedStatement
import naksha.base.AnyList
import naksha.base.AnyMap
import naksha.base.Platform
import naksha.base.PlatformList
import naksha.base.proxy
import naksha.geo.SpBoundingBox
import naksha.model.FETCH_ALL
import naksha.model.NO_CACHE_BIT
import naksha.model.NakshaContext
import naksha.model.objects.IndexList
import naksha.model.objects.NakshaCollection
import naksha.model.objects.StandardMembers
import naksha.model.objects.XyzIndices
import naksha.model.objects.XyzMembers
import naksha.model.request.ReadFeatures
import naksha.model.request.SuccessResponse
import naksha.model.request.Write
import naksha.model.request.WriteRequest
import naksha.model.request.ops.Equals
import naksha.model.request.ops.Intersects
import naksha.model.request.ops.Op
import naksha.model.request.ops.TagListContains
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ImlIndexPlansTest {
  @Test
  fun `empty indices retain mandatory indexes and default planner selects rare rows before and after update`() = withWriter { fixture, writer ->
    val collection = NakshaCollection("empty-index-plans", fixture.schema).apply { indices = IndexList() }
    write(writer, Write().createCollection(collection))
    val layer = checkNotNull(writer.sessionMetadata.getLayer(fixture.schema, collection.id))
    writer.commit()
    assertMandatoryIndexes(fixture.connection, layer, "${layer.table}_id_next_version_key", "${layer.table}_pkey")
    seed(fixture.connection, layer, 20000, 10001)
    verifyPlansAndReads(fixture, layer, "created")

    write(writer, Write().updateCollection(collection, false))
    writer.commit()
    assertMandatoryIndexes(fixture.connection, layer, "${layer.table}_id_next_version_key", "${layer.table}_pkey")
    verifyPlansAndReads(fixture, layer, "updated")
  }

  @Test
  fun `explicit null members and indices default to XYZ descriptors without redundant identity indexes`() = withWriter { fixture, writer ->
    val collection = NakshaCollection("null-index-defaults", fixture.schema).apply {
      members = null
      indices = null
    }
    assertNull(collection.members)
    assertNull(collection.indices)
    val defaults = collection.copy<NakshaCollection>(recursive = true)
    val members = defaults.useMembers()
    val indices = checkNotNull(defaults.indices)
    assertEquals(XyzMembers.ALL.size, members.size)
    assertEquals(XyzIndices.ALL.size, indices.size)
    for (member in XyzMembers.ALL) assertNotNull(members.get(member.name))
    val defaultNames = HashSet<String>()
    for (index in indices) defaultNames.add(checkNotNull(index).name)
    for (index in XyzIndices.ALL) assertTrue(index.name in defaultNames, index.name)
    assertNull(collection.members)
    assertNull(collection.indices)
    write(writer, Write().createCollection(collection))
    val layer = checkNotNull(writer.sessionMetadata.getLayer(fixture.schema, collection.id))
    writer.commit()
    assertMandatoryIndexes(fixture.connection, layer, "${layer.table}_id_next_version_key", "${layer.table}_pkey")
    val persisted = checkNotNull(fixture.storage.metadata.getLayer(fixture.schema, collection.id)).collection
    assertEquals(members.size, persisted.useMembers().size)
    assertEquals(indices.size, checkNotNull(persisted.indices).size)
  }

  @Test
  fun `empty index update removes optional owned indexes but protects mandatory indexes and external indexes`() = withWriter { fixture, writer ->
    val collection = NakshaCollection("index-cleanup", fixture.schema).apply { indices = IndexList(XyzIndices.ALL) }
    write(writer, Write().createCollection(collection))
    val layer = checkNotNull(writer.sessionMetadata.getLayer(fixture.schema, collection.id))
    writer.commit()
    val before = indexOids(fixture.connection, layer)
    val external = "${layer.table}_xyz_external"
    fixture.connection.createStatement().use {
      it.execute("CREATE INDEX ${ImlMetadata.quote(external)} ON ${layer.sqlTable} (operation)")
    }
    collection.indices = IndexList()
    write(writer, Write().updateCollection(collection, false))
    writer.commit()
    val after = indexOids(fixture.connection, layer)
    for (name in arrayOf("${layer.table}_pkey", "${layer.table}_id_next_version_key",
      "${layer.table}_xyz_geo", "${layer.table}_xyz_tags")) {
      assertNotNull(before[name], name)
      assertEquals(before[name], after[name], "Mandatory index was dropped or recreated: $name")
    }
    assertTrue(external in after)
    for (index in XyzIndices.ALL) {
      if (index.name != XyzMembers.XyzGeometry.name && index.name != XyzIndices.XyzTags.name) {
        assertFalse("${layer.table}_xyz_${index.name}" in after, index.name)
      }
    }
    assertMandatoryIndexes(fixture.connection, layer, "${layer.table}_id_next_version_key", "${layer.table}_pkey")
  }

  @Test
  fun `updating an external legacy collection repairs absent identity indexes with empty descriptors`() = withWriter { fixture, writer ->
    val layer = checkNotNull(writer.sessionMetadata.getLayer(fixture.schema, fixture.layer))
    assertTrue(indexOids(fixture.connection, layer).isEmpty())
    val collection = NakshaCollection(fixture.layer, fixture.schema).apply {
      members = null
      indices = IndexList()
    }
    write(writer, Write().updateCollection(collection, false))
    writer.commit()
    val idIndex = "${layer.table}_required_id"
    val numberIndex = "${layer.table}_required_i"
    assertMandatoryIndexes(fixture.connection, layer, idIndex, numberIndex)
    // A modest legacy data set is enough to exercise identity probes with the default planner.
    seed(fixture.connection, layer, 1000, 501)
    for ((name, op, expected) in listOf(
      Triple("legacy-id", Equals(StandardMembers.Id, "f501"), idIndex to "id ="),
      Triple("legacy-fn", Equals(StandardMembers.FeatureNumber, 501L), numberIndex to "i ="))) {
      val request = request(fixture, layer, op)
      val query = ImlQuery(request, fixture.storage.hardCap, collection.useMembers())
      assertPlan(fixture.connection, name,
        query.history.select(layer.sqlTable, query.predicate, query.orderBy) + " LIMIT ?", expected.first,
        expected.second, 1, if (name == "legacy-id") "${layer.table}_version" else null) { statement ->
        statement.setInt(query.bind(statement), query.limit)
      }
      assertRead(fixture, request, 501)
    }
    val before = indexOids(fixture.connection, layer)
    write(writer, Write().updateCollection(collection, false))
    writer.commit()
    assertEquals(before, indexOids(fixture.connection, layer))
  }

  private fun verifyPlansAndReads(fixture: ImlTestFixture, layer: ImlMetadata.Layer, phase: String) {
    val idIndex = "${layer.table}_id_next_version_key"
    val numberIndex = "${layer.table}_pkey"
    val cases = listOf(
      Triple("id", Equals(StandardMembers.Id, "f10001"), idIndex to "id ="),
      Triple("fn", Equals(StandardMembers.FeatureNumber, 10001L), numberIndex to "i ="),
      Triple("geo", Intersects(XyzMembers.XyzGeometry, SpBoundingBox(10.0005, -0.0005, 10.0015, 0.0005).toPolygon()),
        "${layer.table}_xyz_geo" to "geo &&"),
      Triple("tags", TagListContains(XyzMembers.XyzTags, "needle"), "${layer.table}_xyz_tags" to "@>")
    )
    for ((name, op, expected) in cases) {
      val request = request(fixture, layer, op)
      val query = ImlQuery(request, fixture.storage.hardCap, layer.collection.useMembers())
      assertPlan(fixture.connection, "$phase-$name-select",
        query.history.select(layer.sqlTable, query.predicate, query.orderBy) + " LIMIT ?", expected.first,
        expected.second, 1) { statement -> statement.setInt(query.bind(statement), query.limit) }
      assertPlan(fixture.connection, "$phase-$name-count",
        "SELECT count(*) FROM ${layer.sqlTable} WHERE ${query.predicate}", expected.first, expected.second, 1) {
        query.bind(it)
      }
      fixture.connection.prepareStatement("SELECT count(*) FROM ${layer.sqlTable} WHERE ${query.predicate}").use {
        query.bind(it)
        it.executeQuery().use { rows -> assertTrue(rows.next()); assertEquals(1L, rows.getLong(1)) }
      }
      assertRead(fixture, request, 10001)
    }
    val columns = "id, i, version, next_version, operation, author, jsondata, ST_AsGeoJSON(geo, 15, 0) AS geometry_json"
    for (pairs in 1..2) {
      val predicate = if (pairs == 1) "(i = ? AND version = ?)" else "(i = ? AND version = ?) OR (i = ? AND version = ?)"
      assertPlan(fixture.connection, "$phase-exact-$pairs", "SELECT $columns FROM ${layer.sqlTable} WHERE $predicate",
        numberIndex, "i =", pairs.toLong()) {
        it.setLong(1, 10001); it.setLong(2, 1)
        if (pairs == 2) { it.setLong(3, 10002); it.setLong(4, 1) }
      }
    }
  }

  private fun request(fixture: ImlTestFixture, layer: ImlMetadata.Layer, op: Op) = ReadFeatures().apply {
    catalogId = fixture.schema
    collectionId = layer.id
    queryMembers = op
    limit = 100
    fetchMode = FETCH_ALL or NO_CACHE_BIT
  }

  private fun assertRead(fixture: ImlTestFixture, request: ReadFeatures, number: Int) {
    fixture.storage.newReadSession().use { reader ->
      val response = reader.executeRead(request) as SuccessResponse
      assertEquals(1, response.length)
      val entry = checkNotNull(response.featureTupleList[0])
      assertNotNull(entry.tuple)
      assertEquals(number.toLong(), entry.tupleNumber.featureNumber)
      assertEquals(1L, entry.tupleNumber.version ushr 2)
      val feature = checkNotNull(response.features[0])
      assertEquals("f$number", feature.id)
      assertEquals(listOf("needle"), feature.properties.xyz.tags.toList())
      val point = checkNotNull(feature.geometry).asPoint()
      assertEquals(number / 1000.0, point.longitude)
      assertEquals(0.0, point.latitude)
      assertEquals(3.0, point.z)
    }
  }

  private fun seed(connection: Connection, layer: ImlMetadata.Layer, count: Int, needle: Int) {
    connection.createStatement().use {
      it.executeUpdate("TRUNCATE ${layer.sqlTable} RESTART IDENTITY")
      assertEquals(count, it.executeUpdate("INSERT INTO ${layer.sqlTable} " +
        "(id, i, version, next_version, operation, author, jsondata, geo) OVERRIDING SYSTEM VALUE " +
        "SELECT 'f' || n, n, 1, 9223372036854775807, 'I', 'index-plans', " +
        "jsonb_build_object('type', 'Feature', 'id', 'stale-json-id', " +
        "'geometry', jsonb_build_object('type', 'Point', 'coordinates', jsonb_build_array(99, 80, 1)), " +
        "'properties', jsonb_build_object('@ns:com:here:xyz', " +
        "jsonb_build_object('tags', jsonb_build_array(CASE WHEN n = $needle THEN 'needle' ELSE 'common' END)))), " +
        "ST_SetSRID(ST_MakePoint(n::double precision / 1000, 0, 3), 4326) " +
        "FROM generate_series(1, $count) AS series(n)"))
      it.execute("ANALYZE ${layer.sqlTable}")
    }
  }

  private fun assertPlan(connection: Connection, label: String, sql: String, index: String, condition: String,
    actualRows: Long, alternativeIndex: String? = null, bind: (PreparedStatement) -> Unit) {
    val text = connection.prepareStatement("EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) $sql").use {
      bind(it)
      it.executeQuery().use { rows -> assertTrue(rows.next()); rows.getString(1) }
    }
    val plans = (Platform.fromJSON(text) as PlatformList).proxy(AnyList::class)
    val root = checkNotNull(checkNotNull(plans[0].proxy(AnyMap::class))["Plan"].proxy(AnyMap::class))
    assertEquals(actualRows, (root["Actual Rows"] as Number).toLong(), text)
    val pending = ArrayList<AnyMap>()
    pending.add(root)
    var matched = 0
    var cursor = 0
    while (cursor < pending.size) {
      val node = pending[cursor++]
      assertFalse(node["Node Type"] == "Seq Scan", text)
      val indexName = node["Index Name"] as? String
      if (indexName != null && (indexName == index || indexName == alternativeIndex)) {
        val indexCondition = node["Index Cond"] as? String
        assertTrue(indexCondition?.contains(condition) == true, text)
        val rows = (node["Actual Rows"] as Number).toLong()
        assertTrue(rows > 0, text)
        matched++
        println("default-plan $label: index=$indexName node=${node["Node Type"]} actualRows=$rows resultRows=$actualRows condition=$indexCondition")
      }
      val children = (node.getRaw("Plans") as? PlatformList)?.proxy(AnyList::class)
      if (children != null) for (child in children) pending.add(checkNotNull(child.proxy(AnyMap::class)))
    }
    assertTrue(matched > 0, text)
  }

  private fun assertMandatoryIndexes(connection: Connection, layer: ImlMetadata.Layer, idIndex: String, numberIndex: String) {
    val names = indexOids(connection, layer)
    for (name in arrayOf(idIndex, numberIndex, "${layer.table}_xyz_geo", "${layer.table}_xyz_tags")) assertTrue(name in names, name)
    connection.prepareStatement("SELECT a.attname, count(*) FROM pg_index ix " +
      "JOIN pg_class ic ON ic.oid = ix.indexrelid JOIN pg_am am ON am.oid = ic.relam " +
      "JOIN pg_attribute a ON a.attrelid = ix.indrelid AND a.attnum = ix.indkey[0] " +
      "WHERE ix.indrelid = ?::regclass AND ix.indisvalid AND ix.indisready AND ix.indpred IS NULL " +
      "AND am.amname = 'btree' AND a.attname IN ('id', 'i') GROUP BY a.attname").use {
      it.setString(1, layer.sqlTable)
      it.executeQuery().use { rows ->
        var columns = 0
        while (rows.next()) { assertEquals(1L, rows.getLong(2), "Redundant leading ${rows.getString(1)} index"); columns++ }
        assertEquals(2, columns)
      }
    }
    connection.prepareStatement("SELECT indexname, indexdef FROM pg_indexes WHERE schemaname = ? AND tablename = ?").use {
      it.setString(1, layer.schema); it.setString(2, layer.table)
      it.executeQuery().use { rows ->
        while (rows.next()) {
          val name = rows.getString(1)
          val definition = rows.getString(2)
          if (name == "${layer.table}_xyz_geo") assertTrue(definition.contains("USING gist (geo)"), definition)
          if (name == "${layer.table}_xyz_tags") {
            assertTrue(definition.contains("USING gin"), definition)
            assertTrue(definition.contains(" -> 'properties'::text") && definition.contains(" -> '@ns:com:here:xyz'::text") &&
              definition.contains(" -> 'tags'::text"), definition)
            assertFalse(definition.contains("#>"), definition)
          }
        }
      }
    }
  }

  private fun indexOids(connection: Connection, layer: ImlMetadata.Layer): Map<String, Long> {
    val indices = HashMap<String, Long>()
    connection.prepareStatement("SELECT ic.relname, ic.oid FROM pg_index ix JOIN pg_class ic ON ic.oid = ix.indexrelid WHERE ix.indrelid = ?::regclass").use {
      it.setString(1, layer.sqlTable)
      it.executeQuery().use { rows -> while (rows.next()) indices[rows.getString(1)] = rows.getLong(2) }
    }
    return indices
  }

  private fun write(writer: ImlWriteSession, operation: Write) {
    writer.executeWrite(WriteRequest().add(operation))
  }

  private fun withWriter(block: (ImlTestFixture, ImlWriteSession) -> Unit) = ImlTestFixture.withFixture { fixture ->
    val context = NakshaContext.currentContext()
    val previousSu = context.su
    val previousWriteEnabled = fixture.config.writeEnabled
    fixture.config.writeEnabled = true
    context.su = true
    try {
      (fixture.storage.newWriteSession() as ImlWriteSession).use { writer ->
        try { block(fixture, writer) } finally { writer.rollback() }
      }
    } finally {
      context.su = previousSu
      fixture.config.writeEnabled = previousWriteEnabled
    }
  }
}
