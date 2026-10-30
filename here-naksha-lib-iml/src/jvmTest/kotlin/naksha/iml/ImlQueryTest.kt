package naksha.iml

import java.lang.reflect.Proxy
import java.sql.PreparedStatement
import naksha.base.NakshaError
import naksha.base.NakshaException
import naksha.base.Platform
import naksha.base.Version
import naksha.geo.PointCoord
import naksha.geo.SpBoundingBox
import naksha.geo.SpPoint
import naksha.model.FETCH_ALL
import naksha.model.NO_CACHE_BIT
import naksha.model.NakshaContext
import naksha.model.objects.StandardMembers
import naksha.model.objects.XyzMembers
import naksha.model.request.OrderBy
import naksha.model.request.ReadFeatures
import naksha.model.request.ops.And
import naksha.model.request.ops.Equals
import naksha.model.request.ops.Gt
import naksha.model.request.ops.Intersects
import naksha.model.request.ops.IsAnyOf
import naksha.model.request.ops.IsNull
import naksha.model.request.ops.Not
import naksha.model.request.ops.Op
import naksha.model.request.ops.Or
import naksha.model.request.ops.SpBuffer
import naksha.model.request.ops.StartsWith
import naksha.model.request.ops.TagEquals
import naksha.model.request.ops.TagListContains
import naksha.model.request.ops.TagListContainsAllOf
import naksha.model.request.ops.TagListContainsAnyOf
import naksha.model.request.ops.TagMapHasKey
import naksha.model.request.query.SortOrder
import naksha.model.request.query.SpAnd
import naksha.model.request.query.SpIntersects
import naksha.model.request.query.SpNot
import naksha.model.request.query.TagExists
import naksha.model.request.query.TagAnd
import naksha.model.request.query.TagNot
import naksha.model.request.query.TagOr
import naksha.model.request.query.TagSetContains
import naksha.model.request.query.TagValueIsString
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@Suppress("DEPRECATION")
class ImlQueryTest {
  @BeforeEach
  fun context() {
    NakshaContext.currentContext().appId = "iml-query-test"
  }

  @Test
  fun `default query selects nondeleted HEAD with bounded limit and deterministic ordering`() {
    val query = ImlQuery(ReadFeatures(), 100)
    assertEquals("next_version = ? AND operation != 'D'", query.predicate)
    assertEquals(listOf(Long.MAX_VALUE), query.parameters)
    assertEquals("i ASC", query.orderBy)
    assertEquals(100, query.limit)

    val request = ReadFeatures().apply {
      version = Version.HEAD.number
      queryDeleted = true
      limit = 1000
      fetchMode = FETCH_ALL or NO_CACHE_BIT
    }
    val deleted = ImlQuery(request, 100)
    assertEquals("next_version = ?", deleted.predicate)
    assertEquals(100, deleted.limit)
    request.limit = 3
    assertEquals(3, ImlQuery(request, 100).limit)
  }

  @Test
  fun `modern text operations retain grouping and bind numeric looking IDs as strings`() {
    val request = ReadFeatures().apply {
      queryMembers = And(
        Equals(StandardMembers.Id, "00123"),
        Or(IsAnyOf(XyzMembers.XyzAuthor, "alice", "bob"), IsNull("author")),
        Not(StartsWith("id", "a!%_'\\"))
      )
    }
    val query = ImlQuery(request, 10)
    assertEquals(listOf(Long.MAX_VALUE, "00123", "alice", "bob", "a!!!%!_'\\%"), query.parameters)
    assertEquals(
      "next_version = ? AND operation != 'D' AND ((id = ? AND id IS NOT NULL) AND ((author IN (?, ?) AND author IS NOT NULL) OR author IS NULL) AND (NOT (id LIKE ? ESCAPE '!' AND id IS NOT NULL)))",
      query.predicate
    )
    assertFalse(query.predicate.contains("00123"))
    assertFalse(query.predicate.contains("alice"))
  }

  @Test
  fun `null and empty operations have explicit two valued semantics`() {
    val cases = listOf(
      Equals("author", null) to "author IS NULL",
      IsNull("id") to "id IS NULL",
      IsAnyOf("author") to "FALSE",
      And() to "TRUE",
      Or() to "FALSE"
    )
    for ((op, sql) in cases) {
      val query = ImlQuery(ReadFeatures().apply { queryMembers = op }, 10)
      assertTrue(query.predicate.endsWith(" AND $sql"))
      assertEquals(listOf(Long.MAX_VALUE), query.parameters)
    }
    val any = IsAnyOf("author", "a").apply { items.add(null) }
    val query = ImlQuery(ReadFeatures().apply { queryMembers = Not(any) }, 10)
    assertEquals(
      "next_version = ? AND operation != 'D' AND (NOT ((author IN (?) AND author IS NOT NULL) OR author IS NULL))",
      query.predicate
    )
  }

  @Test
  fun `parsed generic operations are detected`() {
    val op = Op().apply {
      this.op = Op.EQ
      at = "id"
      set("value", "parsed")
    }
    val query = ImlQuery(ReadFeatures().apply { queryMembers = op }, 10)
    assertEquals(listOf(Long.MAX_VALUE, "parsed"), query.parameters)
  }

  @Test
  fun `tags use parameterized JSONB containment of complete string elements`() {
    val source = "jsondata->'properties'->'@ns:com:here:xyz'->'tags'"
    val elements = listOf("foo=bar", "flag:=true", "00123", "", "x' OR TRUE --", "quote\"slash\\\n")
    for (element in elements) {
      val query = ImlQuery(ReadFeatures().apply {
        queryMembers = TagListContains(XyzMembers.XyzTags, element)
      }, 10)
      assertEquals("next_version = ? AND operation != 'D' AND (($source @> ?::jsonb) AND $source IS NOT NULL)", query.predicate)
      assertEquals(listOf(Long.MAX_VALUE, Platform.toJSON(listOf(element))), query.parameters)
    }
    val generic = Op().apply {
      op = Op.TAGLIST_CONTAINS
      at = XyzMembers.XyzTags.name
      set("item", "foo=bar")
    }
    assertEquals(listOf(Long.MAX_VALUE, "[\"foo=bar\"]"),
      ImlQuery(ReadFeatures().apply { queryMembers = generic }, 10).parameters)
  }

  @Test
  fun `tag all any and negation retain grouping and two valued missing path semantics`() {
    val source = "jsondata->'properties'->'@ns:com:here:xyz'->'tags'"
    val query = ImlQuery(ReadFeatures().apply {
      queryMembers = And(
        TagListContainsAllOf(XyzMembers.XyzTags, "a", "b", "a"),
        Or(TagListContainsAnyOf(XyzMembers.XyzTags, "c", "d"), Not(TagListContains("tags", "e"))),
        Equals("id", "123")
      )
    }, 10)
    assertEquals(listOf(Long.MAX_VALUE, "[\"a\",\"b\",\"a\"]", "[\"c\"]", "[\"d\"]", "[\"e\"]", "123"), query.parameters)
    assertTrue(query.predicate.contains("(($source @> ?::jsonb OR $source @> ?::jsonb) AND $source IS NOT NULL)"))
    assertTrue(query.predicate.contains("(NOT (($source @> ?::jsonb) AND $source IS NOT NULL))"))
    for ((op, sql) in listOf(
      TagListContainsAllOf("tags") to "TRUE",
      TagListContainsAnyOf("tags") to "FALSE",
      Not(TagListContainsAllOf("tags")) to "(NOT TRUE)",
      Not(TagListContainsAnyOf("tags")) to "(NOT FALSE)"
    )) {
      val empty = ImlQuery(ReadFeatures().apply { queryMembers = op }, 10)
      assertTrue(empty.predicate.endsWith(" AND $sql"))
      assertEquals(listOf(Long.MAX_VALUE), empty.parameters)
    }
  }

  @Test
  fun `legacy tag composition matches modern operations and combines with spatial and IDs`() {
    val legacy = TagOr(TagAnd(TagExists("foo=bar"), TagSetContains("flag:=true")), TagNot(TagExists("blocked")))
    val modern = Or(And(TagListContains("tags", "foo=bar"), TagListContains("tags", "flag:=true")), Not(TagListContains("tags", "blocked")))
    val converted = ImlQuery(ReadFeatures().apply { query.tags = legacy }, 10)
    val direct = ImlQuery(ReadFeatures().apply { queryMembers = modern }, 10)
    assertEquals(direct.predicate, converted.predicate)
    assertEquals(direct.parameters, converted.parameters)
    val point = SpPoint(PointCoord(1.0, 2.0))
    val combined = ImlQuery(ReadFeatures().apply {
      query.tags = legacy
      query.spatial = SpIntersects(point)
      featureIds.add("123")
    }, 10)
    assertEquals(direct.parameters + listOf(Platform.toJSON(point), "123"), combined.parameters)
    assertTrue(combined.predicate.contains("ST_Intersects"))
    assertTrue(combined.predicate.endsWith("AND (id IN (?) AND id IS NOT NULL)"))
    for ((tags, sql) in listOf(TagAnd() to "TRUE", TagOr() to "FALSE")) {
      assertTrue(ImlQuery(ReadFeatures().apply { query.tags = tags }, 10).predicate.endsWith(" AND $sql"))
    }
  }

  @Test
  fun `unsupported tag members map operations and nonstring elements fail closed`() {
    for (op in listOf(
      TagListContains("properties.tags", "a"), TagListContainsAllOf("id"), TagListContainsAnyOf("author"),
      TagEquals(XyzMembers.XyzTags, "foo", "bar"), TagMapHasKey(XyzMembers.XyzTags, "foo"),
      Equals(XyzMembers.XyzTags, "foo=bar"), IsNull("tags"), IsAnyOf("tags", "a"), StartsWith("tags", "a")
    )) rejects(ReadFeatures().apply { queryMembers = op }, NakshaError.UNSUPPORTED_OPERATION)
    for (value in listOf(123, true, listOf("a"))) {
      for (op in listOf(TagListContains("tags", value), TagListContainsAllOf("tags", value), TagListContainsAnyOf("tags", value))) {
        rejects(ReadFeatures().apply { queryMembers = op }, NakshaError.ILLEGAL_ARGUMENT)
      }
      rejects(ReadFeatures().apply { query.tags = TagSetContains(value) }, NakshaError.ILLEGAL_ARGUMENT)
    }
    for (op in listOf(
      TagListContainsAllOf("tags").apply { items.add(null) },
      TagListContainsAnyOf("tags").apply { items.add(null) }
    )) rejects(ReadFeatures().apply { queryMembers = op }, NakshaError.ILLEGAL_ARGUMENT)
    rejects(ReadFeatures().apply { query.tags = TagSetContains(null) }, NakshaError.ILLEGAL_ARGUMENT)
    rejects(ReadFeatures().apply { query.tags = TagValueIsString("foo", "bar") }, NakshaError.UNSUPPORTED_OPERATION)
  }

  @Test
  fun `geometry is a GeoJSON string parameter using only standard PostGIS`() {
    val geometry = SpPoint(PointCoord(12.0, 34.0, 56.0))
    val query = ImlQuery(ReadFeatures().apply { queryMembers = Intersects("geo", geometry) }, 10)
    assertEquals(
      "next_version = ? AND operation != 'D' AND (ST_Intersects(geo, ST_SetSRID(ST_GeomFromGeoJSON(?),4326)) AND geo IS NOT NULL)",
      query.predicate
    )
    assertEquals(listOf(Long.MAX_VALUE, Platform.toJSON(geometry)), query.parameters)
    assertFalse(query.predicate.contains("12.0"))
  }

  @Test
  fun `legacy IDs and bbox are AND combined through query conversion`() {
    val polygon = SpBoundingBox(1.0, 2.0, 3.0, 4.0).toPolygon()
    val request = ReadFeatures().apply {
      featureIds.add("123")
      featureIds.add("x' OR TRUE --")
      query.spatial = SpIntersects(polygon)
    }
    val query = ImlQuery(request, 10)
    assertEquals(listOf(Long.MAX_VALUE, Platform.toJSON(polygon), "123", "x' OR TRUE --"), query.parameters)
    assertEquals(
      "next_version = ? AND operation != 'D' AND (ST_Intersects(geo, ST_SetSRID(ST_GeomFromGeoJSON(?),4326)) AND geo IS NOT NULL) AND (id IN (?, ?) AND id IS NOT NULL)",
      query.predicate
    )
    assertFalse(query.predicate.contains("OR TRUE"))
  }

  @Test
  fun `legacy spatial logical operations are converted`() {
    val point = SpPoint(PointCoord(1.0, 2.0))
    val request = ReadFeatures().apply {
      query.spatial = SpAnd(SpIntersects(point), SpNot(SpIntersects(point)))
    }
    val query = ImlQuery(request, 10)
    assertEquals(listOf(Long.MAX_VALUE, Platform.toJSON(point), Platform.toJSON(point)), query.parameters)
    assertEquals(
      "next_version = ? AND operation != 'D' AND ((ST_Intersects(geo, ST_SetSRID(ST_GeomFromGeoJSON(?),4326)) AND geo IS NOT NULL) AND (NOT (ST_Intersects(geo, ST_SetSRID(ST_GeomFromGeoJSON(?),4326)) AND geo IS NOT NULL)))",
      query.predicate
    )
  }

  @Test
  fun `ordering uses whitelist source version and final tuple tiebreaker`() {
    val request = ReadFeatures().apply {
      orderBy = OrderBy("id", SortOrder.DESCENDING, OrderBy("author", next = OrderBy("v")))
    }
    assertEquals(
      "id DESC, author ASC, version ASC, CASE operation WHEN 'I' THEN 0 WHEN 'U' THEN 1 WHEN 'D' THEN 2 ELSE 3 END ASC, i ASC",
      ImlQuery(request, 10).orderBy
    )
    for (member in listOf("fn", "tn")) {
      request.orderBy = OrderBy(member, SortOrder.DESCENDING)
      assertEquals("i DESC", ImlQuery(request, 10).orderBy)
    }
    request.orderBy = OrderBy.id()
    assertTrue(ImlQuery(request, 10).orderBy.startsWith("id ASC, version ASC,"))
    assertEquals("version", StandardMembers.FeatureVersion.name)
    for (member in listOf("v", StandardMembers.FeatureVersion.name)) {
      request.orderBy = OrderBy(member)
      assertEquals(
        "version ASC, CASE operation WHEN 'I' THEN 0 WHEN 'U' THEN 1 WHEN 'D' THEN 2 ELSE 3 END ASC, i ASC",
        ImlQuery(request, 10).orderBy
      )
    }
    request.orderBy = OrderBy.deterministic()
    assertEquals("i ASC", ImlQuery(request, 10).orderBy)
    request.orderBy = OrderBy("id; DROP TABLE features")
    rejects(request, NakshaError.UNSUPPORTED_OPERATION)
    request.orderBy = OrderBy().apply { next = OrderBy("id") }
    rejects(request, NakshaError.ILLEGAL_ARGUMENT)
  }

  @Test
  fun `unsupported operations members values and transformations are rejected`() {
    val point = SpPoint(PointCoord(1.0, 2.0))
    val unsupported = listOf(
      Gt("id", "a"), Equals("tags", "a"), Equals("id; SELECT 1", "a"),
      Intersects("id", point), Intersects("geo", point, SpBuffer(1.0))
    )
    for (op in unsupported) rejects(ReadFeatures().apply { queryMembers = op }, NakshaError.UNSUPPORTED_OPERATION)
    for (value in listOf(123L, true, listOf("a"))) {
      rejects(ReadFeatures().apply { queryMembers = Equals("id", value) }, NakshaError.ILLEGAL_ARGUMENT)
    }
    rejects(ReadFeatures().apply { queryMembers = IsAnyOf("author", "a", 12) }, NakshaError.ILLEGAL_ARGUMENT)
    rejects(ReadFeatures().apply { query.refTiles.add(123) }, NakshaError.UNSUPPORTED_OPERATION)
    rejects(ReadFeatures().apply {
      query.spatial = SpIntersects(point, naksha.model.request.query.SpBuffer(1.0))
    }, NakshaError.UNSUPPORTED_OPERATION)
  }

  @Test
  fun `mixed modern and legacy queries are rejected`() {
    rejects(ReadFeatures().apply {
      queryMembers = Equals("id", "a")
      featureIds.add("b")
    }, NakshaError.ILLEGAL_ARGUMENT)
    rejects(ReadFeatures().apply {
      queryMembers = Equals("id", "a")
      query.spatial = SpIntersects(SpPoint(PointCoord(1.0, 2.0)))
    }, NakshaError.ILLEGAL_ARGUMENT)
    rejects(ReadFeatures().apply {
      queryMembers = TagListContains("tags", "a")
      query.tags = TagExists("a")
    }, NakshaError.ILLEGAL_ARGUMENT)
  }

  @Test
  fun `history handles unsupported fetch modes and invalid limits are rejected`() {
    val requests = listOf(
      ReadFeatures().apply { queryHistory = true },
      ReadFeatures().apply { minVersion = Version.MIN.number },
      ReadFeatures().apply { version = 4L },
      ReadFeatures().apply { versions = 2 },
      ReadFeatures().apply { returnHandle = true },
      ReadFeatures().apply { fetchMode = 0 },
      ReadFeatures().apply { fetchMode = FETCH_ALL or 32 }
    )
    for (request in requests) rejects(request, NakshaError.UNSUPPORTED_OPERATION)
    for (limit in listOf(0, -1)) {
      rejects(ReadFeatures().apply { this.limit = limit }, NakshaError.ILLEGAL_ARGUMENT)
      assertThrows(NakshaException::class.java) { ImlQuery(ReadFeatures(), limit) }
    }
  }

  @Test
  fun `query depth cycles node count order cycles and parameter count are bounded`() {
    var deep: Op = Equals("id", "a")
    repeat(66) { deep = Not(deep) }
    rejects(ReadFeatures().apply { queryMembers = deep }, NakshaError.ILLEGAL_ARGUMENT)
    val cycle = Not(Equals("id", "a"))
    cycle.child = cycle
    rejects(ReadFeatures().apply { queryMembers = cycle }, NakshaError.ILLEGAL_ARGUMENT)
    val many = And()
    repeat(1025) { many.children.add(IsNull("author")) }
    rejects(ReadFeatures().apply { queryMembers = many }, NakshaError.ILLEGAL_ARGUMENT)
    val order = OrderBy("id")
    order.next = order
    rejects(ReadFeatures().apply { orderBy = order }, NakshaError.ILLEGAL_ARGUMENT)
    val legacy = SpNot(SpIntersects(SpPoint(PointCoord(1.0, 2.0))))
    legacy.query = legacy
    rejects(ReadFeatures().apply { query.spatial = legacy }, NakshaError.ILLEGAL_ARGUMENT)
    val huge = IsAnyOf("id")
    repeat(10000) { huge.items.add("$it") }
    rejects(ReadFeatures().apply { queryMembers = huge }, NakshaError.ILLEGAL_ARGUMENT)
    var deepTags: naksha.model.request.query.ITagQuery = TagExists("a")
    repeat(66) { deepTags = TagNot(deepTags) }
    rejects(ReadFeatures().apply { query.tags = deepTags }, NakshaError.ILLEGAL_ARGUMENT)
    val tagCycle = TagNot(TagExists("a"))
    tagCycle.query = tagCycle
    rejects(ReadFeatures().apply { query.tags = tagCycle }, NakshaError.ILLEGAL_ARGUMENT)
    val tagListCycle = TagAnd()
    tagListCycle.add(tagListCycle)
    rejects(ReadFeatures().apply { query.tags = tagListCycle }, NakshaError.ILLEGAL_ARGUMENT)
    val nullTag = TagOr().apply { add(null) }
    rejects(ReadFeatures().apply { query.tags = nullTag }, NakshaError.ILLEGAL_ARGUMENT)
    val manyTags = TagAnd()
    repeat(1025) { manyTags.add(TagExists("a")) }
    rejects(ReadFeatures().apply { query.tags = manyTags }, NakshaError.ILLEGAL_ARGUMENT)
    val hugeTags = TagListContainsAnyOf("tags")
    repeat(10000) { hugeTags.items.add("$it") }
    rejects(ReadFeatures().apply { queryMembers = hugeTags }, NakshaError.ILLEGAL_ARGUMENT)
  }

  @Test
  fun `bind uses primitive parameters and returns next index without binding pagination`() {
    val query = ImlQuery(ReadFeatures().apply { queryMembers = Equals("id", "123") }, 10)
    val calls = ArrayList<List<Any?>>()
    val statement = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(PreparedStatement::class.java)) { _, method, args ->
      if (method.name != "setObject") throw AssertionError("Unexpected JDBC method ${method.name}")
      calls.add(args!!.toList())
      null
    } as PreparedStatement
    assertEquals(3, query.bind(statement))
    assertEquals(listOf(listOf<Any?>(1, Long.MAX_VALUE), listOf(2, "123")), calls)
    calls.clear()
    assertEquals(6, query.bind(statement, 4))
    assertEquals(listOf(listOf<Any?>(4, Long.MAX_VALUE), listOf(5, "123")), calls)
    assertThrows(NakshaException::class.java) { query.bind(statement, 0) }
    assertThrows(NakshaException::class.java) { query.bind(statement, Int.MAX_VALUE) }
  }

  private fun rejects(request: ReadFeatures, code: String) {
    val error = assertThrows(NakshaException::class.java) { ImlQuery(request, 100) }
    assertEquals(code, error.error.code)
  }
}
