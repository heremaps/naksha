package naksha.iml.parity

import naksha.base.Guid
import naksha.base.Platform
import naksha.iml.ImlConfig
import naksha.iml.ImlStorage
import naksha.model.Naksha
import naksha.model.SessionOptions
import naksha.model.objects.JsonPath
import naksha.model.objects.Member
import naksha.model.objects.MemberList
import naksha.model.objects.MemberType
import naksha.model.objects.NakshaCollection
import naksha.model.objects.StandardMembers
import naksha.model.objects.XyzMembers
import naksha.model.request.OrderBy
import naksha.model.request.SuccessResponse
import naksha.model.request.ops.Equals
import naksha.model.request.ops.IsAnyOf
import naksha.model.request.ops.StartsWith
import naksha.model.request.ops.TagEquals
import naksha.model.request.ops.TagListContains
import naksha.model.request.ops.TagMapHasKey
import naksha.model.request.ops.TagMatches
import naksha.model.request.query.DoubleOp
import naksha.model.request.query.PQuery
import naksha.model.request.query.Property
import naksha.model.request.query.SortOrder
import naksha.iml.parity.P3.case
import naksha.iml.parity.P3.describe
import naksha.iml.parity.Parity.observe
import naksha.iml.parity.Parity.request
import naksha.iml.parity.Parity.timed
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

/**
 * Phase 3, items "ID edge cases: numeric-looking, %/'/unicode, length > 255" and
 * "a SQL injection attempt through the ID, property path, tag or regex inputs".
 * Uses `ev-p3-ids` and `ev-p3-inj` from `scripts/seed-p3.py`, plus a scratch metadata schema with a canary table (created and dropped here).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@org.junit.jupiter.api.Timeout(value = 10, unit = java.util.concurrent.TimeUnit.MINUTES)
class P3IdInjectionTest {
  private lateinit var config: ImlConfig
  private lateinit var storage: ImlStorage
  private val scratch = "p3inj_" + System.nanoTime().toString(36)
  private val configs = ArrayList<ImlConfig>()

  @BeforeAll
  fun setUp() {
    Parity.assumeEnabled()
    val (c, s) = Parity.open("iml-eval-p3-ids")
    config = c
    storage = s
  }

  @AfterAll
  fun tearDown() {
    for (c in configs) runCatching { Naksha.removeStorage(c) }
    if (::config.isInitialized) Naksha.removeStorage(config)
    runCatching { P3.execute("DROP SCHEMA IF EXISTS \"$scratch\" CASCADE") }
  }

  private fun headIds(space: String): List<String> = P3.db { c ->
    c.createStatement().use { st ->
      st.executeQuery("SELECT id FROM public.\"${P3.table(space)}\" WHERE next_version = 9223372036854775807 ORDER BY i").use { r -> generateSequence { if (r.next()) r.getString(1) else null }.toList() }
    }
  }

  private fun lib(space: String, options: SessionOptions? = null, build: naksha.model.request.ReadFeatures.() -> Unit): String = try {
    storage.newReadSession(options).use { s -> "ok " + (s.executeRead(request(space, build)) as SuccessResponse).features.filterNotNull().map { it.id }.sorted() }
  } catch (e: Throwable) { describe(e) }

  private fun show(id: String) = Platform.toJSON(if (id.length > 24) id.take(12) + "…(${id.length} chars)" else id)

  @Test
  fun `id edge cases - exact lookup, Hub parity, GUID round trip`() {
    val ids = headIds("ev-p3-ids")
    val rows = LinkedHashMap<String, Any?>()
    var libExact = 0
    var hubExact = 0
    var guidOk = 0
    val problems = ArrayList<String>()
    for (id in ids) {
      val lib = Parity.libRead(storage, "ev-p3-ids") { queryMembers = Equals(StandardMembers.Id, id) }
      val libOk = lib.size == 1 && lib[0]["id"] == id
      if (libOk) libExact++
      val byPath = P3.hub("GET", "/spaces/ev-p3-ids/features/${P3.pathId(id)}")
      val byQuery = Parity.hub("/spaces/ev-p3-ids/features", listOf("id" to id))
      val hubPathOk = byPath.status == 200 && (byPath.json as? Map<*, *>)?.get("id") == id
      val hubQueryOk = byQuery.status == 200 && byQuery.features.map { it["id"] } == listOf(id)
      if (hubQueryOk) hubExact++
      val uuid = ((lib.firstOrNull()?.get("properties") as Map<*, *>?)?.get("@ns:com:here:xyz") as Map<*, *>?)?.get("uuid") as String?
      val guid = runCatching { Guid.fromString(uuid!!) }
      val roundTrip = guid.getOrNull()?.id == id
      if (roundTrip) guidOk++
      // Reload through the GUID's tuple number, as a client holding only the uuid would.
      val reload = guid.getOrNull()?.let { g ->
        runCatching {
          storage.newReadSession().use { s ->
            val ft = naksha.model.request.FeatureTuple(g.tupleNumber, null)
            s.loadTuples(listOf(ft)); ft.feature?.id == id
          }
        }.getOrElse { false }
      } ?: false
      if (!libOk || !hubQueryOk || !roundTrip || !reload) problems += show(id)
      rows[show(id)] = linkedMapOf("lib" to if (libOk) "exact" else "got ${lib.map { it["id"] }}", "hubPath" to if (hubPathOk) "exact" else "${byPath.status}",
        "hubQuery" to if (hubQueryOk) "exact" else "${byQuery.status} ${byQuery.features.size}", "guidRoundTrip" to roundTrip, "reloadByGuid" to reload,
        "uuid" to uuid?.take(80))
    }
    // Hub write outcomes from the seed (NUL rejected) and lib with a NUL in the query value.
    rows["lib Equals(id, \"nul\\u0000id\")"] = lib("ev-p3-ids") { queryMembers = Equals(StandardMembers.Id, "nul\u0000id") }
    rows["lib Equals(id, \"7\") vs stored \"007\""] = lib("ev-p3-ids") { queryMembers = Equals(StandardMembers.Id, "7") }
    rows["lib Equals(id, \"1000\") vs stored \"1e3\""] = lib("ev-p3-ids") { queryMembers = Equals(StandardMembers.Id, "1000") }
    rows["lib IsAnyOf(all ${ids.size})"] = lib("ev-p3-ids") { queryMembers = IsAnyOf(StandardMembers.Id, *ids.toTypedArray()) }.let { if (it.startsWith("ok ")) "ok ${it.removePrefix("ok ").count { c -> c == ',' } + 1} ids" else it }
    rows["lib legacy featureIds(all)"] = try {
      storage.newReadSession().use { s -> "ok ${(s.executeRead(request("ev-p3-ids") { featureIds.addAll(ids) }) as SuccessResponse).features.filterNotNull().size}" }
    } catch (e: Throwable) { describe(e) }
    rows["lib StartsWith(id, \"a%\")"] = lib("ev-p3-ids") { queryMembers = StartsWith(StandardMembers.Id, "a%") }
    rows["lib StartsWith(id, \"a_\")"] = lib("ev-p3-ids") { queryMembers = StartsWith(StandardMembers.Id, "a_") }
    rows["lib StartsWith(id, \"%\")"] = lib("ev-p3-ids") { queryMembers = StartsWith(StandardMembers.Id, "%") }
    rows["lib OrderBy(id) first 5"] = try {
      storage.newReadSession().use { s -> (s.executeRead(request("ev-p3-ids") { orderBy = OrderBy("id", SortOrder.ASCENDING); limit = 5 }) as SuccessResponse).features.mapNotNull { it?.id }.map { show(it) } }
    } catch (e: Throwable) { describe(e) }
    rows["hub /iterate first 5 (order by i)"] = Parity.hubOk("/spaces/ev-p3-ids/iterate", listOf("limit" to "5")).features.map { show(it["id"] as String) }
    rows["db collation"] = P3.scalar("SELECT datcollate::text FROM pg_database WHERE datname = current_database()")
    observe("ids-edge-cases", rows)
    case("6 ids", "${ids.size} stored edge-case ids (numeric-looking, quotes, %, _, unicode NFC/NFD, control chars, 256…3 000 chars)",
      "Hub: GET /features?id= exact for $hubExact/${ids.size}",
      "lib Equals(id) exact for $libExact/${ids.size}; GUID round trip $guidOk/${ids.size}; problems: $problems",
      if (problems.isEmpty()) "OK" else "see problems")
    case("6 ids", "no numeric coercion, LIKE escaping in StartsWith",
      "Hub: ids are strings; there is no id prefix search",
      "Equals(\"7\") → ${rows["lib Equals(id, \"7\") vs stored \"007\""]}; StartsWith(\"a%\") → ${rows["lib StartsWith(id, \"a%\")"]}; StartsWith(\"a_\") → ${rows["lib StartsWith(id, \"a_\")"]}",
      "OK")
    case("6 ids", "NUL character in an id",
      "Hub: write rejected with 400 (seed log, #34)",
      "lib query value with NUL: ${rows["lib Equals(id, \"nul\\u0000id\")"]}",
      "Low: raw PSQLException instead of ILLEGAL_ARGUMENT")
    assertEquals(ids.size, libExact)
  }

  @Test
  fun `sql injection probes`() {
    val o = LinkedHashMap<String, Any?>()
    val before = P3.scalar("SELECT count(*) FROM public.\"${P3.table("ev-p3-inj")}\"")
    // IDs and author
    o["Equals(id, ' OR '1'='1) on ev-p3-inj"] = lib("ev-p3-inj") { queryMembers = Equals(StandardMembers.Id, "' OR '1'='1") }
    o["Equals(id, ' OR '1'='1) on ev-p3-ids"] = lib("ev-p3-ids") { queryMembers = Equals(StandardMembers.Id, "' OR '1'='1") }
    o["IsAnyOf(id, x'); DROP TABLE t; --, canary)"] = lib("ev-p3-inj") { queryMembers = IsAnyOf(StandardMembers.Id, "x'); DROP TABLE t; --", "canary") }
    o["featureIds(\"' OR 1=1 --\")"] = lib("ev-p3-inj") { featureIds.add("' OR 1=1 --") }
    o["Equals(author, ' OR 1=1 --)"] = lib("ev-p3-inj") { queryMembers = Equals("author", "' OR 1=1 --") }
    // Member names and ordering are whitelisted
    o["Equals(member \"id' OR '1'='1\")"] = lib("ev-p3-inj") { queryMembers = Equals("id' OR '1'='1", "x") }
    o["OrderBy(\"id; DROP TABLE x\")"] = lib("ev-p3-inj") { orderBy = OrderBy("id; DROP TABLE x", SortOrder.ASCENDING) }
    // Tags (JSONB containment parameter) and the virtual tag map
    o["TagListContains(\"' or '1'='1\")"] = lib("ev-p3-inj") { queryMembers = TagListContains(XyzMembers.XyzTags, "' or '1'='1") }
    o["TagListContains(\"x\\\"]}\")"] = lib("ev-p3-inj") { queryMembers = TagListContains(XyzMembers.XyzTags, "x\"]}") }
    o["TagListContains(\"\\\"]' OR true --\")"] = lib("ev-p3-inj") { queryMembers = TagListContains(XyzMembers.XyzTags, "\"]' OR true --") }
    o["TagEquals(k, \"v'; drop table x; --\")"] = lib("ev-p3-inj") { queryMembers = TagEquals("tags", "k", "v'; drop table x; --") }
    o["TagMapHasKey(\"' OR '1'='1\")"] = lib("ev-p3-inj") { queryMembers = TagMapHasKey("tags", "' OR '1'='1") }
    // Regex: PostgreSQL ARE, bound as a parameter
    o["TagMatches(s, \"') OR TRUE --\")"] = lib("ev-p3-inj") { queryMembers = TagMatches("tags", "s", "') OR TRUE --") }
    o["TagMatches(s, \"^a+!$\")"] = lib("ev-p3-inj") { queryMembers = TagMatches("tags", "s", "^a+!$") }
    o["TagMatches(s, invalid \"(\")"] = lib("ev-p3-inj") { queryMembers = TagMatches("tags", "s", "(") }
    for ((name, regex) in listOf("(a+)+$ (nested quantifier)" to "^(a+)+$", "(a|aa)*c (alternation)" to "^(a|aa)*c$", "(a*)*\\1b (back-reference)" to "^(a*)*\\1b$")) {
      val (r, ms) = timed { lib("ev-p3-inj", SessionOptions(appId = "iml-eval-parity", stmtTimeout = 10_000)) { queryMembers = TagMatches("tags", "s", regex) } }
      o["ReDoS $name"] = "$r in $ms ms"
    }
    // Property paths: legacy PropertyFilter runs client side, no SQL
    o["PQuery(properties.\"a'b\" = 1)"] = lib("ev-p3-inj") { query.properties = PQuery(Property("properties", "a'b"), DoubleOp.EQ, 1) }
    o["PQuery(properties.\"x') OR ('1'='1\" = 6)"] = lib("ev-p3-inj") { query.properties = PQuery(Property("properties", "x') OR ('1'='1"), DoubleOp.EQ, 6) }
    o["queryMembers Equals(\"properties.a'b\")"] = lib("ev-p3-inj") { queryMembers = Equals("properties.a'b", 1) }
    // The same probes against the Hub's SQL property search (IML engine side)
    o["hub p.a'b=1"] = Parity.hub("/spaces/ev-p3-inj/search", raw = "p.a%27b=1").let { "${it.status} ${Parity.ids(it.features)}" }
    o["hub p.x') OR ('1'='1=6"] = Parity.hub("/spaces/ev-p3-inj/search", raw = "p.x%27)%20OR%20(%271%27%3D%271=6").let { "${it.status} ${it.error ?: Parity.ids(it.features)}" }
    o["hub tags=' or '1'='1"] = Parity.hub("/spaces/ev-p3-inj/search", listOf("tags" to "' or '1'='1")).let { "${it.status} ${it.error ?: Parity.ids(it.features)}" }
    o["hub id=' OR '1'='1"] = Parity.hub("/spaces/ev-p3-inj/features", listOf("id" to "' OR '1'='1")).let { "${it.status} ${it.error ?: Parity.ids(it.features)}" }
    o["rowsBefore/After"] = "$before/${P3.scalar("SELECT count(*) FROM public.\"${P3.table("ev-p3-inj")}\"")}"
    observe("injection-probes", o)
    val leaked = o.filterKeys { !it.startsWith("hub") && it != "rowsBefore/After" }.filterValues { v -> v.toString().contains("canary") && !v.toString().contains("inj-") }.keys -
      setOf("IsAnyOf(id, x'); DROP TABLE t; --, canary)")
    case("7 injection", "ID / author / featureIds probes (' OR '1'='1, stacked DROP, comment)",
      "Hub: ids are bound parameters: ${o["hub id=' OR '1'='1"]}",
      "${o["Equals(id, ' OR '1'='1) on ev-p3-inj"]}; on ev-p3-ids (where that id exists): ${o["Equals(id, ' OR '1'='1) on ev-p3-ids"]}; IsAnyOf: ${o["IsAnyOf(id, x'); DROP TABLE t; --, canary)"]}; author: ${o["Equals(author, ' OR 1=1 --)"]}",
      if (leaked.isEmpty()) "OK: values are bound parameters (ImlQuery.kt parameter())" else "CHECK: $leaked")
    case("7 injection", "member names and ordering",
      "Hub: p.* paths are escaped by the Hub (see below)",
      "member: ${o["Equals(member \"id' OR '1'='1\")"]}; orderBy: ${o["OrderBy(\"id; DROP TABLE x\")"]}",
      "OK: whitelist (ImlPredicates.column, ImlQuery orderBy)")
    case("7 injection", "tag list / tag map probes",
      "Hub: ${o["hub tags=' or '1'='1"]}",
      "contains: ${o["TagListContains(\"' or '1'='1\")"]}; JSON-breaking: ${o["TagListContains(\"x\\\"]}\")"]} / ${o["TagListContains(\"\\\"]' OR true --\")"]}; TagEquals: ${o["TagEquals(k, \"v'; drop table x; --\")"]}; key: ${o["TagMapHasKey(\"' OR '1'='1\")"]}",
      "OK")
    case("7 injection", "regex probes (TagMatches: PostgreSQL ARE, bound parameter)",
      "Hub: no regex search",
      "injection text: ${o["TagMatches(s, \"') OR TRUE --\")"]}; invalid '(': ${o["TagMatches(s, invalid \"(\")"]}; ReDoS: ${o.filterKeys { it.startsWith("ReDoS") }.values}",
      "Low: no injection; an invalid regex gives a raw PSQLException; regex cost is bounded by statement_timeout only")
    case("7 injection", "property-path probes",
      "Hub SQL search: p.a'b=1 → ${o["hub p.a'b=1"]}; p.x') OR ('1'='1=6 → ${o["hub p.x') OR ('1'='1=6"]}",
      "PropertyFilter (client side): ${o["PQuery(properties.\"a'b\" = 1)"]} / ${o["PQuery(properties.\"x') OR ('1'='1\" = 6)"]}; queryMembers: ${o["queryMembers Equals(\"properties.a'b\")"]}",
      "OK: lib never puts a property path into SQL")
    assertEquals(before, P3.scalar("SELECT count(*) FROM public.\"${P3.table("ev-p3-inj")}\""))
  }

  @Test
  fun `injection through metadata - table names and custom member paths`() {
    P3.execute("CREATE SCHEMA \"$scratch\"")
    P3.execute("CREATE TABLE \"$scratch\".canary (x int)")
    P3.execute("CREATE TABLE \"$scratch\".space_meta (id text NOT NULL, schem text NOT NULL, h_id text NOT NULL, meta jsonb NOT NULL DEFAULT '{}'::jsonb, PRIMARY KEY(id,schem))")
    val injTable = P3.table("ev-p3-inj")!!
    val collection = NakshaCollection("custom", "public").apply {
      members = MemberList().apply {
        addAll(XyzMembers.ALL)
        add(Member("cm_quote", MemberType.STRING, JsonPath("properties", "nested", "q'", "r\"")))
        add(Member("cm_inj", MemberType.INT64, JsonPath("properties", "x') OR ('1'='1")))
      }
    }
    val meta = Platform.toJSON(mapOf("nakshaCollection" to Platform.fromJSON(Platform.toJSON(collection))))
    P3.execute("INSERT INTO \"$scratch\".space_meta VALUES ('custom', 'public', ?, ?::jsonb)", injTable, meta)
    P3.execute("INSERT INTO \"$scratch\".space_meta VALUES ('evil', 'public', ?, '{}')", "x\"; DROP TABLE \"$scratch\".canary; --")
    val o = LinkedHashMap<String, Any?>()
    val (c, s) = Parity.open("iml-eval-p3-inj-meta") { metadataSchema = scratch }
    configs += c
    fun read(space: String, build: naksha.model.request.ReadFeatures.() -> Unit) = try {
      s.newReadSession().use { session -> "ok " + (session.executeRead(request(space, build)) as SuccessResponse).features.filterNotNull().map { it.id }.sorted() }
    } catch (e: Throwable) { describe(e) }
    o["custom member path with quotes: Equals(cm_quote, deep)"] = read("custom") { queryMembers = Equals("cm_quote", "deep") }
    o["custom member path with injection text: Equals(cm_inj, 6)"] = read("custom") { queryMembers = Equals("cm_inj", 6) }
    o["layer whose h_id is an injection string"] = read("evil") {}
    o["canaryAfter"] = P3.scalar("SELECT to_regclass(?)::text", "\"$scratch\".canary")
    observe("injection-metadata", o)
    case("7 injection", "space_meta h_id = 'x\"; DROP TABLE …; --' and custom member paths with quotes (scratch metadata schema)",
      "Hub: table names are generated hashes; no member paths",
      "h_id: ${o["layer whose h_id is an injection string"]}; canary table after: ${o["canaryAfter"]}; quoted path: ${o["custom member path with quotes: Equals(cm_quote, deep)"]}; injection path: ${o["custom member path with injection text: Equals(cm_inj, 6)"]}",
      "OK: identifiers are quoted (ImlMetadata.quote), member path keys are escaped E'' literals (ImlPredicates.json)")
  }
}
