package naksha.iml

import naksha.base.NakshaException
import naksha.base.Platform
import naksha.geo.PointCoord
import naksha.geo.SpPoint
import naksha.model.NakshaContext
import naksha.model.TagNormalizer
import naksha.model.objects.XyzMembers
import naksha.model.request.ReadFeatures
import naksha.model.request.ops.*
import naksha.model.request.query.TagValueIsString
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test

/** Expanded compiler tests; database cases are opt-in and use only the existing local fixture seed. @since 3.0 */
@Suppress("DEPRECATION")
class ImlAdvancedQueriesTest {
  @BeforeEach
  fun context() {
    NakshaContext.currentContext().appId = "iml-advanced-query-test"
  }

  private fun compile(op: Op) = ImlQuery(ReadFeatures().apply { queryMembers = op }, 100)

  @Test
  fun `scalar ranges use fixed XYZ paths guarded by JSON types`() {
    val ops = listOf(Gt("updated_at", 1), Gte("created_at", 2), Lt("cv0", 3.5), Lte("here_tile", 4))
    for (op in ops) {
      val query = compile(op)
      assertTrue(query.predicate.contains("CASE WHEN jsonb_typeof("))
      assertTrue(query.predicate.contains("::numeric END"))
      assertEquals(2, query.parameters.size)
    }
    assertTrue(compile(Gt("id", "a")).predicate.contains("id > ?"))
    assertTrue(compile(Equals(XyzMembers.XyzFeatureType, "road")).predicate.contains("jsondata->'properties'->'featureType'"))
    assertTrue(compile(IsTrue("cv0")).predicate.endsWith("NULL::boolean IS TRUE"))
    assertTrue(compile(IsFalse("author")).predicate.endsWith("NULL::boolean IS FALSE"))
    for (op in listOf(Equals("unknown", 1), Gt("id; DROP TABLE x", "x"), Gt("cv0", Double.NaN), Equals("id", 1))) {
      assertThrows(NakshaException::class.java) { compile(op) }
    }
  }

  @Test
  fun `all tag map operations are compiled with bound values and last write wins`() {
    val ops = listOf(
      TagMapHasKey("tags", "x"), TagMapHasAnyOf("tags", "x", "y"), TagMapHasAllOf("tags", "x", "y"),
      TagEquals("tags", "x", "a' OR TRUE --"), TagEquals("tags", "x", true), TagEquals("tags", "x", 1),
      TagIsNull("tags", "x"), TagGt("tags", "x", 1), TagGte("tags", "x", 1), TagLt("tags", "x", 1),
      TagLte("tags", "x", 1), TagStartsWith("tags", "x", "a%_!"), TagMatches("tags", "x", "^a.*")
    )
    for (op in ops) {
      val query = compile(op)
      assertTrue(query.predicate.contains("WITH ORDINALITY"))
      assertTrue(query.predicate.contains("ORDER BY ord DESC LIMIT 1"))
      assertFalse(query.predicate.contains("a' OR TRUE --"))
    }
    assertTrue(compile(TagMapHasAllOf("tags")).predicate.endsWith(" AND TRUE"))
    assertTrue(compile(TagMapHasAnyOf("tags")).predicate.endsWith(" AND FALSE"))
    assertEquals(listOf(Long.MAX_VALUE, "v", "k"), compile(TagEquals("tags", "k", "v")).parameters)
    val legacy = ImlQuery(ReadFeatures().apply { query.tags = TagValueIsString("k", "v") }, 100)
    assertEquals(compile(TagEquals("tags", "k", "v")).predicate, legacy.predicate)
  }

  @Test
  fun `buffers bind meters and styles and recognize deserialized shape`() {
    val point = SpPoint(PointCoord(1.0, 2.0))
    val buffer = SpBuffer(250.0, quadSegments = 12, joinStyle = SpJoinStyle.BEVEL, joinLimit = 2.0,
      endCap = SpEndCap.BUTT, side = SpSide.LEFT)
    val query = compile(Intersects("geo", point, buffer, SpBuffer(-1.0, false)))
    assertTrue(query.predicate.contains("::geography"))
    assertTrue(query.predicate.contains("::geometry"))
    assertEquals(listOf(Long.MAX_VALUE, Platform.toJSON(point), 250.0,
      "quad_segs=12 mitre_limit=2.0 join=bevel endcap=flat side=left", -1.0, ""), query.parameters)
    val raw = SpTransformation().apply { setRaw("distance", 250.0); setRaw("geography", true); setRaw("endCap", "flat") }
    assertTrue(compile(Intersects("geo", point, raw)).parameters.contains("endcap=flat"))
    for (bad in listOf(SpBuffer(Double.NaN), SpBuffer(1.0, quadSegments = 0),
      SpBuffer(1.0).apply { setRaw("joinStyle", "round; DROP TABLE x") }, SpTransformation())) {
      assertThrows(NakshaException::class.java) { compile(Intersects("geo", point, bad)) }
    }
    val legacyBuffer = naksha.model.request.query.SpBuffer(10.0)
    legacyBuffer.childTransformation = legacyBuffer
    assertThrows(NakshaException::class.java) {
      ImlQuery(ReadFeatures().apply { this.query.spatial = naksha.model.request.query.SpIntersects(point, legacyBuffer) }, 100)
    }
  }

  @Test
  fun `packed version and next version members agree with tuple representation`() {
    val version = compile(Equals("version", 29L))
    assertTrue(version.predicate.contains("version::numeric * 4 + CASE operation"))
    val next = compile(Equals("nv", naksha.base.Version.HEAD.number))
    assertTrue(next.predicate.contains("THEN 9007199254740991::numeric"))
    assertEquals(listOf(Long.MAX_VALUE, naksha.base.Version.HEAD.number), next.parameters)
    for (name in listOf("tn", "feature", "base_tn", "geo", "ref_point", "properties.custom")) {
      assertThrows(NakshaException::class.java) { compile(IsNull(name)) }
    }
  }

  @Test
  fun `local fixture executes scalar buffer and seed tag predicates before limit`() = withLocalFixture { fixture ->
    for ((op, expected) in listOf(
      Gte("updated_at", 2000) to 5, Lt("created_at", 1000) to 0,
      TagIsNull("tags", "blue") to 5, TagMapHasAllOf("tags", "blue", "test") to 5,
      TagEquals("tags", "blue", "") to 0,
      Intersects("geo", SpPoint(PointCoord(1.0, 2.0)), SpBuffer(10.0)) to 2
    )) {
      val query = compile(op)
      fixture.connection.prepareStatement("SELECT count(*) FROM ${fixture.sqlTable} WHERE ${query.predicate}").use { statement ->
        query.bind(statement)
        statement.executeQuery().use { rows -> assertTrue(rows.next()); assertEquals(expected, rows.getInt(1), op.op) }
      }
    }
  }

  @Test
  fun `local SQL tag parsing agrees with splitTag edge cases without modifying fixture`() = withLocalFixture { fixture ->
    val tags = mutableListOf("a=first", "a=last", "x=y=z", "flag:=TrUe", "n:=1.25e2", "bad:=abc", " bare ",
      "ref_k=v", "sourceID_k=v", "xyz_source_id_k=v", "=leading", " k := false ", "n:=126f",
      "spaces\u00a0=\u00a0trimmed\u00a0", "zero:=1e-500", "huge:=1e500", "nan:=NaN",
      "hex:=0x1.8p2", "max:=1.7976931348623157e308", "small:=4.9e-324",
      "invalid:=1e+", "invalidhex:=0x1p", "lowernan:=nan", "lowerinf:=infinity",
      "suffix:=1.25D", "sign:=+.5", "empty:=", "dup:=1e500", "dup:=2", "=foo=bar",
      "tiny:=1e-999999999999", "overflow:=1e999999999999", "long:=0." + "0".repeat(2000) + "1",
      "hexzero:=0x1p-999999999999", "hexhuge:=0x1p999999999999", "hexsub:=0x0.0000000000001p-1022",
      "negzero:=-0.0", "nearmax:=1.7976931348623158e308", "abovehalf:=2.4703282292062328e-324",
      "hexoverflow:=0x1p1024", "hexunderflow:=0x1p-1076", "hexhalf:=0x1p-1075",
      "hexnegative:=-0x1.fffffffffffffp1023", "decimalnegative:=-1e999999999999",
      "padded:=1e+0000000000000002", "spacecontrol:=\u0001\u000212\u0003", "notbool:=\u0001true\u0002")
    for (code in listOf(9, 10, 11, 12, 13, 28, 29, 30, 31, 32, 160, 5760, 8192, 8193, 8194, 8195,
      8196, 8197, 8198, 8199, 8200, 8201, 8202, 8232, 8233, 8239, 8287, 12288)) {
      tags.add("${code.toChar()}white$code${code.toChar()}:=${code.toChar()}2${code.toChar()}")
    }
    val cases = listOf(
      TagEquals("tags", "a", "last") to true, TagEquals("tags", "a", "first") to false,
      TagEquals("tags", "x=y", "z") to true, TagEquals("tags", "flag", true) to true,
      TagEquals("tags", "n", 126) to true, TagGt("tags", "n", 125) to true,
      TagEquals("tags", "bad:=abc", "abc") to true, TagIsNull("tags", " bare ") to true,
      TagIsNull("tags", "ref_k=v") to true, TagIsNull("tags", "sourceID_k=v") to true,
      TagIsNull("tags", "xyz_source_id_k=v") to true, TagIsNull("tags", "=leading") to true,
      TagEquals("tags", "k", false) to true, Not(TagEquals("tags", "missing", "x")) to true,
      TagStartsWith("tags", "a", "la") to true, TagMatches("tags", "a", "^la.*t$") to true,
      TagEquals("tags", "spaces", "trimmed") to true, TagEquals("tags", "zero", 0.0) to true,
      TagGt("tags", "huge", Double.MAX_VALUE) to true, TagGt("tags", "nan", 1) to false
    ).toMutableList()
    val expectedMap = LinkedHashMap<String, Any?>()
    for (tag in tags) {
      val (key, value) = TagNormalizer.splitTag(tag)
      expectedMap[key] = value
    }
    for ((key, value) in expectedMap) {
      if (value is Double && !value.isFinite()) continue
      cases.add(TagEquals("tags", key, value) to true)
    }
    cases.add(TagGt("tags", "overflow", Double.MAX_VALUE) to true)
    cases.add(TagGt("tags", "hexhuge", Double.MAX_VALUE) to true)
    cases.add(TagLt("tags", "decimalnegative", -Double.MAX_VALUE) to true)
    cases.add(TagGte("tags", "n", 126) to true)
    cases.add(TagLte("tags", "n", 126) to true)
    cases.add(TagMapHasKey("tags", "invalid:=1e+") to true)
    cases.add(TagMapHasKey("tags", "invalid") to false)
    cases.add(TagGt("tags", "invalid:=1e+", 0) to false)
    cases.add(Not(TagGt("tags", "invalid:=1e+", 0)) to true)
    for ((op, expected) in cases) {
      val query = compile(op)
      val body = Platform.toJSON(mapOf("properties" to mapOf("@ns:com:here:xyz" to mapOf("tags" to tags))))
      fixture.connection.prepareStatement("SELECT ${query.predicate} FROM (SELECT ?::jsonb AS jsondata, " +
        "9223372036854775807::bigint AS next_version, 'I' AS operation) feature").use { statement ->
        val next = query.bind(statement)
        statement.setString(next, body)
        statement.executeQuery().use { rows -> assertTrue(rows.next()); assertEquals(expected, rows.getBoolean(1), Platform.toJSON(op)) }
      }
    }
  }

  @Test
  fun `local numeric tag comparisons match JVM Double across decimal and hex exponents`() = withLocalFixture { fixture ->
    val values = ArrayList<String>()
    for (exponent in listOf(-2000, -1075, -1074, -1022, -500, -324, -323, -308, -1, 0, 1, 308, 309, 500, 1023, 1024, 2000)) {
      values.add("-1.234567890123456789e$exponent")
      values.add("0x1.abcdef123456789p$exponent")
    }
    values.addAll(listOf("0x0p999999999999", "-0x0p999999999999", "-0e999999999999",
      "1.7976931348623159e308", "2.4703282292062327e-324", "0x1.00000000000008p0",
      "1_000", "1,5", "1e", "0x", ".", "+", "NaNf", "InfinityD"))
    for (raw in values) {
      val tag = "number:=$raw"
      val (key, value) = TagNormalizer.splitTag(tag)
      val op = if (value is Double && !value.isFinite()) {
        if (value > 0) TagGt("tags", key, Double.MAX_VALUE) else TagLt("tags", key, -Double.MAX_VALUE)
      } else TagEquals("tags", key, value)
      assertTag(fixture, listOf(tag, "unrelated:=0x1p999999999999", "invalid:=1e+"), op, true)
    }
  }

  @Test
  fun `local tag parsing safely handles malformed JSON shapes and nonstring array elements`() = withLocalFixture { fixture ->
    for (tags in listOf(null, 1, true, "not-an-array", mapOf("x" to "y"),
      listOf(null, 1, false, mapOf("x" to "y")), listOf(null, "x:=1", false))) {
      val expected = tags is List<*> && tags.contains("x:=1")
      assertTag(fixture, tags, TagEquals("tags", "x", 1), expected)
      assertTag(fixture, tags, Not(TagEquals("tags", "x", 1)), !expected)
    }
  }

  private fun assertTag(fixture: ImlTestFixture, tags: Any?, op: Op, expected: Boolean) {
    val query = compile(op)
    val body = Platform.toJSON(mapOf("properties" to mapOf("@ns:com:here:xyz" to mapOf("tags" to tags))))
    fixture.connection.prepareStatement("SELECT ${query.predicate} FROM (SELECT ?::jsonb AS jsondata, " +
      "9223372036854775807::bigint AS next_version, 'I' AS operation) feature").use { statement ->
      statement.setString(query.bind(statement), body)
      statement.executeQuery().use { rows ->
        assertTrue(rows.next())
        assertEquals(expected, rows.getBoolean(1), "${Platform.toJSON(op)} in $body")
      }
    }
  }

  private fun withLocalFixture(block: (ImlTestFixture) -> Unit) {
    val url = System.getenv("NAKSHA_TEST_IML_DB_URL").orEmpty()
    assumeTrue(Regex("^jdbc:postgresql://(localhost|127[.]0[.]0[.]1|\\[::1\\])(:[0-9]+)?/[^?]*(\\?.*)?$").matches(url),
      "Advanced query fixtures require an explicit loopback NAKSHA_TEST_IML_DB_URL")
    ImlTestFixture.withFixture(block = block)
  }
}
