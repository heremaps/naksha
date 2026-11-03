package naksha.iml.parity

import naksha.iml.ImlConfig
import naksha.iml.ImlStorage
import naksha.model.Naksha
import naksha.model.objects.StandardMembers
import naksha.model.objects.XyzMembers
import naksha.model.request.ReadFeatures
import naksha.model.request.ops.And
import naksha.model.request.ops.Equals
import naksha.model.request.ops.Gt
import naksha.model.request.ops.IsAnyOf
import naksha.model.request.ops.TagEquals
import naksha.model.request.ops.TagListContains
import naksha.model.request.ops.TagListContainsAllOf
import naksha.model.request.ops.TagListContainsAnyOf
import naksha.model.request.query.AnyOp
import naksha.model.request.query.DoubleOp
import naksha.model.request.query.IPropertyQuery
import naksha.model.request.query.PNot
import naksha.model.request.query.POr
import naksha.model.request.query.PAnd
import naksha.model.request.query.PQuery
import naksha.model.request.query.Property
import naksha.model.request.query.StringOp
import naksha.iml.parity.Parity.diff
import naksha.iml.parity.Parity.hubOk
import naksha.iml.parity.Parity.ids
import naksha.iml.parity.Parity.libRead
import naksha.iml.parity.Parity.outcome
import naksha.iml.parity.Parity.record
import naksha.iml.parity.Parity.row
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

/**
 * Phase 2 parity, matrix item "/search": `p.*` property queries, tags and author, on `ev-p2` (scripts/seed-p2.py) and `ev-hist`.
 *
 * lib-iml has two ways to filter on properties:
 * - `queryMembers` (SQL) only on fixed members (id, author, timestamps, tags …); arbitrary `properties.*` paths are rejected.
 * - legacy `query.properties` (deprecated), evaluated client-side by the model's `PropertyFilter` after loading every candidate.
 * Every case maps the hub query to the closest lib-iml request a translating client would build, and records the outcome.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class P2SearchParityTest {
  private lateinit var config: ImlConfig
  private lateinit var storage: ImlStorage
  private val results = LinkedHashMap<String, String>()

  @BeforeAll
  fun setUp() {
    Parity.assumeEnabled()
    val (c, s) = Parity.open("iml-eval-p2-search")
    config = c
    storage = s
  }

  @AfterAll
  fun tearDown() {
    if (::config.isInitialized) Naksha.removeStorage(config)
  }

  private fun p(vararg path: String) = Property("properties", *path)

  /** Runs one case: hub `/search?<raw>` vs a lib-iml read; records the diff and a matrix row; returns the result string. */
  private fun case(name: String, space: String, raw: String, lib: String, build: ReadFeatures.() -> Unit): String {
    val hub = hubOk("/spaces/$space/search", raw = "$raw&limit=1000").features
    var libFeatures: List<Map<*, *>>? = null
    val out = outcome({ l: List<Map<*, *>> -> ids(l).toString() }) { libRead(storage, space, build).also { libFeatures = it } }
    val result = if (libFeatures == null) {
      Parity.observe("search-$name", mapOf("hub" to ids(hub), "lib" to out))
      "error"
    } else {
      val d = record(diff("search-$name", hub, libFeatures!!), mapOf("hubIds" to ids(hub), "libIds" to ids(libFeatures!!)))
      d.result
    }
    results[name] = result
    row("/search", "GET /search?$raw", lib, result, "hub=${ids(hub)} lib=$out")
    return result
  }

  private fun props(q: IPropertyQuery): ReadFeatures.() -> Unit = { query.properties = q }

  @Test
  fun `property queries on ev-p2`() {
    case("eq-string", "ev-p2", "p.name=alpha", "PQuery(p.name, StringOp.EQUALS, \"alpha\")", props(PQuery(p("name"), StringOp.EQUALS, "alpha")))
    case("eq-string-case", "ev-p2", "p.name=ALPHA", "StringOp.EQUALS \"ALPHA\"", props(PQuery(p("name"), StringOp.EQUALS, "ALPHA")))
    case("eq-number", "ev-p2", "p.n=1", "PQuery(p.n, DoubleOp.EQ, 1)", props(PQuery(p("n"), DoubleOp.EQ, 1)))
    case("eq-number-10", "ev-p2", "p.n=10", "DoubleOp.EQ 10 (s4 has the string \"10\")", props(PQuery(p("n"), DoubleOp.EQ, 10)))
    case("eq-quoted-string", "ev-p2", "p.n='10'", "StringOp.EQUALS \"10\"", props(PQuery(p("n"), StringOp.EQUALS, "10")))
    case("eq-or", "ev-p2", "p.n=1,2", "POr(DoubleOp.EQ 1, DoubleOp.EQ 2)", props(POr(PQuery(p("n"), DoubleOp.EQ, 1), PQuery(p("n"), DoubleOp.EQ, 2))))
    case("ne-number", "ev-p2", "p.n!=1", "PQuery(p.n, DoubleOp.NE, 1)", props(PQuery(p("n"), DoubleOp.NE, 1)))
    case("ne-as-not-eq", "ev-p2", "p.n!=1", "PNot(DoubleOp.EQ 1) (workaround)", props(PNot(PQuery(p("n"), DoubleOp.EQ, 1))))
    case("ne-string", "ev-p2", "p.name!=alpha", "PQuery(p.name, StringOp.NOT_EQUALS, \"alpha\")", props(PQuery(p("name"), StringOp.NOT_EQUALS, "alpha")))
    case("gt", "ev-p2", "p.n=gt=5", "DoubleOp.GT 5", props(PQuery(p("n"), DoubleOp.GT, 5)))
    case("gt-symbol", "ev-p2", "p.n%3E5", "DoubleOp.GT 5", props(PQuery(p("n"), DoubleOp.GT, 5)))
    case("lt-mixed-types", "ev-p2", "p.n%3C2", "DoubleOp.LT 2 (s4 has n=\"10\")", props(PQuery(p("n"), DoubleOp.LT, 2)))
    case("gte-float", "ev-p2", "p.f=gte=0", "DoubleOp.GTE 0", props(PQuery(p("f"), DoubleOp.GTE, 0)))
    case("and-range", "ev-p2", "p.n=gt=1&p.n=lt=11", "PAnd(GT 1, LT 11)", props(PAnd(PQuery(p("n"), DoubleOp.GT, 1), PQuery(p("n"), DoubleOp.LT, 11))))
    case("string-gt", "ev-p2", "p.s=gt=a", "no string range op in PropertyFilter (DoubleOp.GT on a string)", props(PQuery(p("s"), DoubleOp.GT, "a")))
    case("numeric-literal-vs-string", "ev-p2", "p.s=10", "DoubleOp.EQ 10 (hub converts 10 to a number)", props(PQuery(p("s"), DoubleOp.EQ, 10)))
    case("null-explicit", "ev-p2", "p.nul=.null", "AnyOp.IS_NULL", props(PQuery(p("nul"), AnyOp.IS_NULL)))
    case("null-missing", "ev-p2", "p.missing=.null", "AnyOp.IS_NULL on a missing property", props(PQuery(p("missing"), AnyOp.IS_NULL)))
    case("null-missing-or-null", "ev-p2", "p.nul=.null", "POr(IS_NULL, PNot(EXISTS)) (workaround)", props(POr(PQuery(p("nul"), AnyOp.IS_NULL), PNot(PQuery(p("nul"), AnyOp.EXISTS)))))
    case("not-null", "ev-p2", "p.nul!=.null", "AnyOp.IS_NOT_NULL", props(PQuery(p("nul"), AnyOp.IS_NOT_NULL)))
    case("bool-true", "ev-p2", "p.b=true", "AnyOp.IS_TRUE", props(PQuery(p("b"), AnyOp.IS_TRUE)))
    case("bool-false", "ev-p2", "p.b=false", "AnyOp.IS_FALSE", props(PQuery(p("b"), AnyOp.IS_FALSE)))
    case("nested", "ev-p2", "p.obj.x.y=deep", "PQuery(p.obj.x.y, StringOp.EQUALS)", props(PQuery(p("obj", "x", "y"), StringOp.EQUALS, "deep")))
    case("nested-null", "ev-p2", "p.obj.x=.null", "IS_NULL on p.obj.x", props(PQuery(p("obj", "x"), AnyOp.IS_NULL)))
    case("array-contains", "ev-p2", "p.arr%40%3E2", "AnyOp.CONTAINS 2", props(PQuery(p("arr"), AnyOp.CONTAINS, 2)))
    case("array-contains-cs", "ev-p2", "p.arr=cs=10", "AnyOp.CONTAINS 10", props(PQuery(p("arr"), AnyOp.CONTAINS, 10)))
    case("array-contains-string", "ev-p2", "p.sarr%40%3Eb", "AnyOp.CONTAINS \"b\"", props(PQuery(p("sarr"), AnyOp.CONTAINS, "b")))
    case("array-contains-or", "ev-p2", "p.arr%40%3E3,4", "POr(CONTAINS 3, CONTAINS 4)", props(POr(PQuery(p("arr"), AnyOp.CONTAINS, 3), PQuery(p("arr"), AnyOp.CONTAINS, 4))))
    case("array-eq-element", "ev-p2", "p.arr=1", "DoubleOp.EQ 1 on an array", props(PQuery(p("arr"), DoubleOp.EQ, 1)))
    case("array-index", "ev-p2", "p.oarr.k=1", "PQuery(p.oarr.0.k, EQ 1) (index path; hub has no index syntax)", props(PQuery(p("oarr", "0", "k"), DoubleOp.EQ, 1)))
    case("unicode", "ev-p2", "p.uni=Gr%C3%BC%C3%9Fe%20%E6%9D%B1%E4%BA%AC", "StringOp.EQUALS \"Grüße 東京\"", props(PQuery(p("uni"), StringOp.EQUALS, "Grüße 東京")))
    case("percent-underscore", "ev-p2", "p.name=al%25ha_", "StringOp.EQUALS \"al%ha_\"", props(PQuery(p("name"), StringOp.EQUALS, "al%ha_")))
    case("prefix", "ev-p2", "p.name=al*", "StringOp.STARTS_WITH \"al\" (hub has no prefix op except deprecated ^=)", props(PQuery(p("name"), StringOp.STARTS_WITH, "al")))
    // f.* root properties map to SQL members.
    case("f-id", "ev-p2", "f.id=s1,s3", "queryMembers IsAnyOf(Id)", { queryMembers = IsAnyOf(StandardMembers.Id, "s1", "s3") })
    case("f-createdAt", "ev-p2", "f.createdAt=gt=0", "queryMembers Gt(created_at, 0)", { queryMembers = Gt("created_at", 0) })
    case("f-id-legacy-property", "ev-p2", "f.id=s1", "PQuery(Property(\"id\"), StringOp.EQUALS)", props(PQuery(Property("id"), StringOp.EQUALS, "s1")))
  }

  @Test
  fun `tags and author`() {
    case("tags-one", "ev-p2", "tags=t1", "TagListContains(tags, \"t1\")", { queryMembers = TagListContains(XyzMembers.XyzTags, "t1") })
    case("tags-upper", "ev-p2", "tags=T1", "TagListContains \"T1\" (hub lower-cases tags on write)", { queryMembers = TagListContains(XyzMembers.XyzTags, "T1") })
    case("tags-or", "ev-p2", "tags=t1,t2", "TagListContainsAnyOf(t1, t2)", { queryMembers = TagListContainsAnyOf(XyzMembers.XyzTags, "t1", "t2") })
    case("tags-and", "ev-p2", "f.tags=cs=t1&f.tags=cs=t2", "TagListContainsAllOf(t1, t2)", { queryMembers = TagListContainsAllOf(XyzMembers.XyzTags, "t1", "t2") })
    case("tags-kv", "ev-p2", "tags=k%3Dv", "TagListContains \"k=v\"", { queryMembers = TagListContains(XyzMembers.XyzTags, "k=v") })
    case("tags-kv-map", "ev-p2", "tags=k%3Dv", "TagEquals(tags, \"k\", \"v\") (virtual tag map)", { queryMembers = TagEquals("tags", "k", "v") })
    case("tags-typed", "ev-p2", "tags=num:%3D3", "TagEquals(tags, \"num\", 3)", { queryMembers = TagEquals("tags", "num", 3) })
    case("tags-hist-or", "ev-hist", "tags=red,road", "TagListContainsAnyOf(red, road)", { queryMembers = TagListContainsAnyOf(XyzMembers.XyzTags, "red", "road") })
    // The documented IML AND form `tags=a+b` does not work on this hub (PropertiesQuery.transformLegacyTags + URL decoding): recorded, compared with AllOf.
    case("tags-plus-and", "ev-hist", "tags=road+lanes:%3D2", "TagListContainsAllOf(road, lanes:=2)", { queryMembers = TagListContainsAllOf(XyzMembers.XyzTags, "road", "lanes:=2") })
    case("author", "ev-p2", "author=ANONYMOUS", "queryMembers Equals(author, \"ANONYMOUS\")", { queryMembers = Equals("author", "ANONYMOUS") })
    case("author-none", "ev-p2", "author=nobody", "Equals(author, \"nobody\")", { queryMembers = Equals("author", "nobody") })
    // Author combined with a property query: queryMembers + legacy properties are rejected, so the author goes into the property query.
    case("author-and-property-mixed", "ev-p2", "author=ANONYMOUS&p.n=1", "queryMembers Equals(author) + query.properties", {
      queryMembers = Equals("author", "ANONYMOUS"); query.properties = PQuery(p("n"), DoubleOp.EQ, 1)
    })
    case("author-and-property", "ev-p2", "author=ANONYMOUS&p.n=1", "PAnd(PQuery(p.@ns.author), PQuery(p.n)) (workaround)", props(
      PAnd(PQuery(p("@ns:com:here:xyz", "author"), StringOp.EQUALS, "ANONYMOUS"), PQuery(p("n"), DoubleOp.EQ, 1))
    ))
    case("tags-and-property", "ev-p2", "tags=t1&p.n=gt=0", "legacy query.tags is converted; with properties → ?", {
      query.tags = naksha.model.request.query.TagSetContains("t1"); query.properties = PQuery(p("n"), DoubleOp.GT, 0)
    })
  }

  /** Why IS_NULL never matches an explicit JSON null in lib-iml: record what the materialized feature holds for `nul`. */
  @Test
  fun `explicit null representation`() {
    val feature = storage.newReadSession().use { s ->
      (s.executeRead(Parity.request("ev-p2") { queryMembers = Equals(StandardMembers.Id, "s1") }) as naksha.model.request.SuccessResponse).features.single()!!
    }
    val raw = feature.properties.getRaw("nul")
    val nested = (feature.properties.getRaw("obj") as? naksha.base.AnyObject)?.let { (it.getRaw("x") as? naksha.base.AnyObject)?.getRaw("y") }
    Parity.observe("search-null-representation", mapOf(
      "rawClass" to (raw?.let { it::class.qualifiedName } ?: "kotlin null"), "rawToString" to raw.toString(),
      "containsKey" to feature.properties.containsKey("nul"), "nestedY" to nested.toString(),
      // Which layer drops it: the model's JSON parser, or lib-iml?
      "Platform.fromJSON keeps null" to (naksha.base.Platform.fromJSON("""{"a":null,"b":1}""") as Map<*, *>).containsKey("a"),
      "NakshaFeature.fromJson keeps null" to naksha.model.objects.NakshaFeature.fromJson("""{"id":"x","properties":{"a":null}}""").properties.containsKey("a"),
      "hub body has \"nul\":null" to Parity.hubOk("/spaces/ev-p2/features/s1").body.contains("\"nul\":null"),
      "db jsondata has nul" to java.sql.DriverManager.getConnection(Parity.dbUrl!!).use { c ->
        c.prepareStatement("SELECT h_id FROM xyz_config.space_meta WHERE id = 'ev-p2'").use { st -> st.executeQuery().use { it.next(); it.getString(1) } }.let { t ->
          c.createStatement().use { st -> st.executeQuery("SELECT jsondata->'properties' ? 'nul' FROM public.\"$t\" WHERE id = 's1' AND next_version = 9223372036854775807").use { it.next(); it.getBoolean(1) } }
        }
      }
    ))
    row("/search .null", "p.x=.null matches missing OR null; the hub strips null properties on write (jsondata has no key)", "AnyOp.IS_NULL (explicit null only)",
      "diff (IS_NULL never matches hub-written data; PNot(EXISTS) does)", "s1.nul in lib: containsKey=${feature.properties.containsKey("nul")}")
  }

  @Test
  fun `limit and ordering`() {
    val hub = hubOk("/spaces/ev-p2/search", raw = "p.n=gt=0&limit=2").features
    val lib = libRead(storage, "ev-p2") { limit = 2; query.properties = PQuery(p("n"), DoubleOp.GT, 0) }
    val d = record(diff("search-limit-order", hub, lib), mapOf("hubOrder" to hub.map { it["id"] }, "libOrder" to lib.map { it["id"] }))
    row("/search limit", "GET /search?p.n=gt=0&limit=2 → ${hub.map { it["id"] }}", "limit=2 + PropertyFilter (refills to limit)", if (hub.map { it["id"] } == lib.map { it["id"] }) "same (incl. order)" else d.result, d.summary())
    // Search on the 120 000-feature layer. The hub refuses p.* on layers > 10 000 rows without an index on the property
    // (xyz-psql-connector GetIndexList BIG_SPACE_THRESHOLD, SearchForFeatures.canSearchFor); lib-iml loads every candidate and filters in the JVM.
    val (hubBig, hubMs) = Parity.timed { Parity.hub("/spaces/ev-big/search", raw = "p.n=119999") }
    val (hubBbox, hubBboxMs) = Parity.timed { Parity.hub("/spaces/ev-big/bbox", raw = "west=-180&south=-90&east=180&north=90&p.n=119999") }
    val (hubIter, hubIterMs) = Parity.timed { Parity.hub("/spaces/ev-big/iterate", raw = "p.n=119999&limit=3") }
    val (hubId, hubIdMs) = Parity.timed { Parity.hub("/spaces/ev-big/search", raw = "f.id=b119999") }
    val (libBig, libMs) = Parity.timed { libRead(storage, "ev-big") { query.properties = PQuery(p("n"), DoubleOp.EQ, 119999) } }
    Parity.observe("search-big-property", mapOf(
      "hubSearch" to "$hubBig", "hubSearchMs" to hubMs, "hubBboxWithP" to "$hubBbox", "hubBboxMs" to hubBboxMs,
      "hubIterateWithP" to "${hubIter.status} ids=${ids(hubIter.features)}", "hubIterateMs" to hubIterMs,
      "hubSearchFid" to "${hubId.status} ids=${ids(hubId.features)}", "hubFidMs" to hubIdMs,
      "lib" to ids(libBig), "libMs" to libMs
    ))
    assertEquals(listOf("b119999"), ids(libBig))
    row("/search on 120k", "GET /spaces/ev-big/search?p.n=119999 → $hubBig ($hubMs ms); /bbox&p.n → ${hubBbox.status}; /iterate&p.n ignores the filter (${ids(hubIter.features)})",
      "PropertyFilter over all 120 000 candidates → ${ids(libBig)} ($libMs ms)", "diff (hub refuses, lib answers by full scan)", "f.id search on hub: ${hubId.status} in $hubIdMs ms")
  }
}
