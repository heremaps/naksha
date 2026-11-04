package naksha.iml.parity

import naksha.base.Platform
import naksha.iml.ImlTuple
import naksha.iml.parity.P3.hub
import naksha.iml.parity.P3.pathId
import naksha.iml.parity.Parity.diff
import naksha.iml.parity.Parity.observe
import naksha.iml.parity.Parity.record
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestMethodOrder
import java.sql.Connection
import java.util.UUID

/**
 * Phase 6 spike: the core of a read-only, branch-aware lib-iml read, written as plain SQL next to lib-iml (not through it).
 *
 * It answers three questions for the effort estimate in `reports/06-suitability.md`:
 * 1. Can a JDBC-only reader resolve a branch's table path from PostgreSQL alone, given the branch's node ID?
 *    (The Hub's psql connector does exactly that, `BranchManager.branchPath`, from `pg_tables`.)
 * 2. Does one SQL statement per page (anti-join per branch level, version rebase, like `GetFeatures.wrapAndBranch`)
 *    return the Hub's result for every branch ref of a nested branch, including bbox and by-id filters?
 * 3. What does such a read cost on 120 000 features, next to the Hub (gated by `IML_P3_LARGE=true`)?
 *
 * Fixture: a fresh history space `ev-p6-<rnd>` with branch `ba` on main and branch `bb` on `ba` (nested, like an IML fork of a fork,
 * geospace `LayersApi.createFork`). Branch edits of inherited features need the Hub's new write path, so the test only runs when
 * `IML_P6_BRANCH=true` (set by `iml-naksha-eval/scripts/p6-branch-spike.sh`, which recreates the Hub with
 * `USE_WRITE_FEATURES_EVENT=true` and back). Everything it creates is deleted in [tearDown], including the branch tables the Hub
 * leaves behind when a space is deleted (Phase 1 observation).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class P6BranchReadSpikeTest {
  private val rnd = UUID.randomUUID().toString().replace("-", "").take(6)
  private val sid = "ev-p6-$rnd"
  private val bigSid = "ev-p6big-$rnd"
  private val v1Sid = "ev-p6v1-$rnd"
  private val created = ArrayList<String>()
  private val xyz = "@ns:com:here:xyz"
  private val log = LinkedHashMap<String, Any?>()

  @BeforeAll
  fun setUp() {
    Parity.assumeEnabled()
    assumeTrue(System.getenv("IML_P6_BRANCH") == "true", "Set IML_P6_BRANCH=true (needs the Hub on USE_WRITE_FEATURES_EVENT=true)")
    createSpace(sid, 100)
    // main: v1 insert, v2 update
    put(sid, listOf(
      pt("m1", 10.0, 50.0, "m1 v1"),
      line("m2", listOf(listOf(10.0, 50.0), listOf(11.0, 51.0)), "m2 v1"),
      poly("m3", listOf(listOf(10.0, 50.0), listOf(11.0, 50.0), listOf(11.0, 51.0), listOf(10.0, 50.0)), "m3 v1"),
      pt("m4", 12.5, 52.5, "m4 v1"), pt("m5", 13.0, 53.0, "m5 v1"), pt("m6", 14.0, 54.0, "m6 v1")
    ))
    put(sid, listOf(line("m2", listOf(listOf(10.0, 50.0), listOf(11.0, 51.0)), "m2 v2 main")))
    branch(sid, "ba", "main:2")
    // ba: update inherited, delete inherited, insert, move an inherited feature out of the test bbox
    put(sid, listOf(pt("m1", 10.0, 50.0, "m1 ba")), "ba")
    delete(sid, "m3", "ba")
    put(sid, listOf(pt("a1", 15.0, 55.0, "a1 ba")), "ba")
    put(sid, listOf(pt("m5", 100.0, -50.0, "m5 moved out in ba")), "ba")
    // main diverges after the branch point
    put(sid, listOf(pt("m1", 10.5, 50.5, "m1 main after ba")))
    delete(sid, "m4")
    put(sid, listOf(pt("m7", 16.0, 56.0, "m7 main")))
    // bb on ba:4 (after the delete of m3, before a1)
    branch(sid, "bb", "ba:4")
    put(sid, listOf(line("m2", listOf(listOf(10.0, 50.0), listOf(12.0, 52.0)), "m2 bb")), "bb")
    delete(sid, "m1", "bb")
    put(sid, listOf(pt("b1", 17.0, 57.0, "b1 bb")), "bb")
    // ba continues after bb was created
    put(sid, listOf(pt("m6", 14.0, 54.0, "m6 ba after bb")), "ba")
    observe("p6-branch-fixture", log)
  }

  @AfterAll
  fun tearDown() {
    for (space in created.reversed()) {
      val root = runCatching { P3.table(space) }.getOrNull()
      for (b in listOf("bb", "ba", "p6probe")) runCatching { hub("DELETE", "/spaces/$space/branches/$b") }
      runCatching { hub("DELETE", "/spaces/$space") }
      if (root != null) dropBranchTables(root)
    }
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Fixture helpers (Hub REST)

  private fun createSpace(id: String, versionsToKeep: Int) {
    P3.recreateSpace(id, versionsToKeep)
    created += id
  }

  private fun feature(id: String, geometry: Map<String, Any?>, name: String) =
    linkedMapOf("type" to "Feature", "id" to id, "geometry" to geometry, "properties" to mapOf("name" to name))

  private fun pt(id: String, lon: Double, lat: Double, name: String) = feature(id, mapOf("type" to "Point", "coordinates" to listOf(lon, lat)), name)
  private fun line(id: String, c: List<List<Double>>, name: String) = feature(id, mapOf("type" to "LineString", "coordinates" to c), name)
  private fun poly(id: String, ring: List<List<Double>>, name: String) = feature(id, mapOf("type" to "Polygon", "coordinates" to listOf(ring)), name)

  private fun put(space: String, features: List<Map<String, Any?>>, ref: String? = null) {
    val r = P3.putFeatures(space, features, if (ref == null) emptyList() else listOf("versionRef" to ref))
    log["put ${features.map { it["id"] }}${ref?.let { "@$it" } ?: ""}"] = r.features.map { (it["properties"] as Map<*, *>)[xyz].let { n -> (n as Map<*, *>)["version"] } }
  }

  private fun delete(space: String, id: String, ref: String? = null) {
    val r = hub("DELETE", "/spaces/$space/features/${pathId(id)}", query = if (ref == null) emptyList() else listOf("versionRef" to ref))
    log["delete $id${ref?.let { "@$it" } ?: ""}"] = r.status
    check(r.status in 200..299) { "DELETE $id @$ref -> ${r.status}: ${r.body.take(300)} (branch edits of inherited features need USE_WRITE_FEATURES_EVENT=true)" }
  }

  private fun branch(space: String, id: String, baseRef: String) {
    val r = hub("POST", "/spaces/$space/branches", Platform.toJSON(mapOf("id" to id, "baseRef" to baseRef)))
    log["branch $id on $baseRef"] = r.status
    check(r.status == 200) { "create branch $id -> ${r.status}: ${r.body.take(300)}" }
  }

  private fun dropBranchTables(root: String) {
    P3.db { c ->
      val tables = c.prepareStatement("SELECT tablename FROM pg_tables WHERE schemaname = 'public' AND tablename ~ ?").use { st ->
        st.setString(1, "^" + root + "_[0-9]+_[0-9]+_[0-9]+$")
        st.executeQuery().use { rs -> generateSequence { if (rs.next()) rs.getString(1) else null }.toList() }
      }
      c.createStatement().use { st -> for (t in tables) st.execute("DROP TABLE IF EXISTS public.\"$t\" CASCADE") }
      log["dropped leftover branch tables of $root"] = tables.size
    }
  }

  /** The Hub's internal branch view (admin route, `BranchApi.activateAdminRoutes`): node ID and branch path. Stands in for the DynamoDB lookup. */
  private fun adminBranch(space: String, id: String): Map<*, *> =
    (hub("GET", "/admin/spaces/$space/branches/$id").json as Map<*, *>?) ?: error("admin branch $id not found")

  // ---------------------------------------------------------------------------------------------------------------
  // The spike: branch path resolution and a single-statement composed read

  /** One level of a branch path: the physical table, its node ID and its base version (absolute, in the base branch's numbering). */
  data class Level(val table: String, val nodeId: Int, val base: Long)

  /** Resolves the levels from main (node 0) to [nodeId] from `pg_tables` only, like `BranchManager.branchPath` / `baseRef(nodeId)`. */
  fun resolvePath(c: Connection, schema: String, root: String, nodeId: Int): List<Level> {
    if (nodeId == 0) return listOf(Level(root, 0, 0))
    val table = c.prepareStatement("SELECT tablename FROM pg_tables WHERE schemaname = ? AND tablename ~ ?").use { st ->
      st.setString(1, schema)
      st.setString(2, "^" + root + "_[0-9]+_[0-9]+_" + nodeId + "$") // root is a 32-hex table name, no regex metacharacters
      st.executeQuery().use { rs -> generateSequence { if (rs.next()) rs.getString(1) else null }.toList() }
    }.singleOrNull() ?: error("no unique branch table for node $nodeId of $root")
    val parts = table.removePrefix(root + "_").split("_")
    return resolvePath(c, schema, root, parts[0].toInt()) + Level(table, nodeId, parts[1].toLong())
  }

  /**
   * One statement for a branch ref (`version == null` = HEAD): every level contributes its rows valid at its bound, unless a higher level
   * has any state of the same id at its bound (including a delete). Filters apply to the selected rows only, never to the anti-joins,
   * so a feature moved out of a bbox in a branch hides its main state. Versions are rebased to the Hub's absolute numbers.
   * Keyset paging over `(lvl, i)` like the Hub's `(dataset, i)` iterate key (`IterateFeatures.java:66,91`).
   */
  fun composedSql(schema: String, levels: List<Level>, version: Long?, filter: String, page: Boolean, history: Boolean = true): String {
    val n = levels.size - 1
    fun bound(k: Int): Long? {
      if (!history) return null
      val upper: Long? = if (k == n) version else minOf(levels[k + 1].base, version ?: Long.MAX_VALUE)
      return upper?.let { it - levels[k].base }
    }
    fun valid(alias: String, k: Int) = bound(k)?.let { "$alias.version <= $it AND $alias.next_version > $it" } ?: "$alias.next_version = 9223372036854775807"
    val selects = levels.indices.map { k ->
      val antiJoins = (k + 1..n).joinToString("") { j ->
        " AND NOT EXISTS (SELECT 1 FROM \"$schema\".\"${levels[j].table}\" x$j WHERE x$j.id = t.id AND ${valid("x$j", j)})"
      }
      "SELECT $k AS lvl, t.i, t.id, t.version + ${levels[k].base} AS version, t.operation, t.jsondata::text AS jsondata, ST_AsGeoJSON(t.geo, 8) AS geojson " +
        "FROM \"$schema\".\"${levels[k].table}\" t WHERE ${valid("t", k)} AND t.operation NOT IN ('D','H','J') AND ($filter)$antiJoins"
    }
    val union = selects.joinToString(" UNION ALL ") { "($it)" }
    return if (page) "SELECT * FROM ($union) c WHERE (c.lvl, c.i) > (?, ?) ORDER BY c.lvl, c.i LIMIT ?" else "SELECT * FROM ($union) c ORDER BY c.lvl, c.i"
  }

  /** Shapes a row like a lib-iml feature map (packed version), so [Parity.diff] can compare it with the Hub. */
  @Suppress("UNCHECKED_CAST")
  private fun toFeature(id: String, version: Long, operation: String, jsondata: String, geojson: String?): Map<*, *> {
    val f = LinkedHashMap(Platform.fromJSON(ImlTuple.jsonNumbers(jsondata)) as Map<Any?, Any?>)
    f["id"] = id
    f["geometry"] = geojson?.let { Platform.fromJSON(ImlTuple.jsonNumbers(it)) }
    val props = LinkedHashMap((f["properties"] as Map<Any?, Any?>?) ?: emptyMap())
    val ns = LinkedHashMap((props[xyz] as Map<Any?, Any?>?) ?: emptyMap())
    ns["version"] = (version shl 2) or (if (operation == "U") 1L else 0L)
    props[xyz] = ns
    f["properties"] = props
    return f
  }

  private fun read(c: Connection, levels: List<Level>, version: Long?, filter: String = "TRUE", params: List<Any> = emptyList()): List<Map<*, *>> =
    c.prepareStatement(composedSql("public", levels, version, filter, false)).use { st ->
      var p = 1
      repeat(levels.size) { for (v in params) st.setObject(p++, v) }
      st.executeQuery().use { rs ->
        generateSequence { if (rs.next()) toFeature(rs.getString("id"), rs.getLong("version"), rs.getString("operation"), rs.getString("jsondata"), rs.getString("geojson")) else null }.toList()
      }
    }

  // ---------------------------------------------------------------------------------------------------------------
  // Tests

  @Test
  @Order(1)
  fun `branch path from pg_tables equals the Hub's branch path`() {
    val root = checkNotNull(P3.table(sid))
    val out = LinkedHashMap<String, Any?>()
    P3.db { c ->
      for (b in listOf("ba", "bb")) {
        val admin = adminBranch(sid, b)
        val nodeId = (admin["nodeId"] as Number).toInt()
        val levels = resolvePath(c, "public", root, nodeId)
        val fromTables = levels.drop(1).map { "~${levels[levels.indexOf(it) - 1].nodeId}:${it.base}" }
        out[b] = mapOf("hubNodeId" to nodeId, "hubBranchPath" to admin["branchPath"], "pgTablesPath" to fromTables, "tables" to levels.map { it.table.removePrefix(root) })
        assertEquals((admin["branchPath"] as List<*>).map { it.toString() }, fromTables, "branch path of $b")
      }
    }
    // Branch metadata the Hub doesn't publish: the public route has no nodeId/branchPath.
    out["publicRouteFields"] = ((hub("GET", "/spaces/$sid/branches").json as List<*>).first() as Map<*, *>).keys.map { it.toString() }.sorted()
    observe("p6-branch-path", out)
  }

  @Test
  @Order(2)
  fun `single-statement composed read equals the Hub for every ref of a nested branch`() {
    val root = checkNotNull(P3.table(sid))
    val results = LinkedHashMap<String, String>()
    val hubErrors = LinkedHashMap<String, String>()
    P3.db { c ->
      val main = resolvePath(c, "public", root, 0)
      val ba = resolvePath(c, "public", root, (adminBranch(sid, "ba")["nodeId"] as Number).toInt())
      val bb = resolvePath(c, "public", root, (adminBranch(sid, "bb")["nodeId"] as Number).toInt())
      val refs = ArrayList<Triple<String, List<Level>, Long?>>()
      refs += Triple("main:HEAD", main, null)
      refs += Triple("ba", ba, null); for (v in 1L..7L) refs += Triple("ba:$v", ba, v)
      refs += Triple("bb", bb, null); for (v in 3L..7L) refs += Triple("bb:$v", bb, v)
      for ((ref, levels, version) in refs) {
        val spike = read(c, levels, version)
        val h = Parity.hub("/spaces/$sid/iterate", listOf("limit" to "1000", "versionRef" to ref))
        if (h.status != 200) {
          // A ref the Hub rejects (e.g. below the branch's base version) is recorded, not compared.
          hubErrors[ref] = "hub ${h.status} ${h.error}; spike ${spike.map { it["id"].toString() }.sorted()}"
          continue
        }
        val d = record(diff("p6-branch-${ref.replace(":", "_")}", h.features, spike))
        results[ref] = d.summary()
      }
      // Filters: bbox (m5 moved out in ba must not fall back to main's state) and by id.
      val bbox = "ST_Intersects(t.geo, ST_MakeEnvelope(?, ?, ?, ?, 4326))"
      for ((ref, levels) in listOf("ba" to ba, "bb" to bb)) {
        val hub = Parity.hubOk("/spaces/$sid/bbox", listOf("west" to "9", "south" to "49", "east" to "18", "north" to "58", "versionRef" to ref)).features
        val spike = read(c, levels, null, bbox, listOf(9.0, 49.0, 18.0, 58.0))
        results["bbox@$ref"] = record(diff("p6-branch-bbox-$ref", hub, spike)).summary()
        val ids = listOf("m1", "m3", "m5", "a1", "b1")
        val hubIds = Parity.hubOk("/spaces/$sid/features", ids.map { "id" to it } + ("versionRef" to ref)).features
        val spikeIds = read(c, levels, null, "t.id = ANY(?)", listOf(c.createArrayOf("text", ids.toTypedArray())))
        results["ids@$ref"] = record(diff("p6-branch-ids-$ref", hubIds, spikeIds)).summary()
      }
    }
    observe("p6-branch-reads", results + hubErrors.mapKeys { "hubError ${it.key}" })
    val bad = results.filterValues { !it.contains("onlyHub=[] onlyLib=[] fieldDiffs={}") }
    assertTrue(bad.isEmpty(), "refs that differ from the Hub: $bad")
  }

  @Test
  @Order(3)
  fun `keyset paging over (level, i) through a nested branch has no gaps or repeats`() {
    val root = checkNotNull(P3.table(sid))
    val out = LinkedHashMap<String, Any?>()
    P3.db { c ->
      val bb = resolvePath(c, "public", root, (adminBranch(sid, "bb")["nodeId"] as Number).toInt())
      val all = read(c, bb, null).map { it["id"] as String }
      val paged = ArrayList<String>()
      var last = -1 to -1L
      var pages = 0
      c.prepareStatement(composedSql("public", bb, null, "TRUE", true)).use { st ->
        while (true) {
          st.setInt(1, last.first); st.setLong(2, last.second); st.setInt(3, 2)
          val page = st.executeQuery().use { rs -> generateSequence { if (rs.next()) Triple(rs.getInt("lvl"), rs.getLong("i"), rs.getString("id")) else null }.toList() }
          if (page.isEmpty()) break
          pages++
          paged += page.map { it.third }
          last = page.last().first to page.last().second
        }
      }
      // The Hub's iterate on the same branch, 2 per page, for comparison of order and completeness.
      val (hubAll, hubPages) = Parity.hubIterateAll(sid, 2, listOf("versionRef" to "bb"))
      out["spikePages"] = pages; out["spikeOrder"] = paged; out["unpaged"] = all
      out["hubPages"] = hubPages; out["hubOrder"] = hubAll.map { it["id"] }
      assertEquals(all, paged, "paged == unpaged")
      assertEquals(paged.size, paged.toSet().size, "no repeats")
      assertEquals(hubAll.map { it["id"] as String }.sorted(), paged.sorted(), "same set as the Hub's iterate")
    }
    observe("p6-branch-paging", out)
  }

  /**
   * On `versionsToKeep=1` layers the Hub adds no version bounds (`GetFeatures.buildVersionComparison`/`buildNextVersionFragment` return ""
   * without history), so a branch would see main's later changes. This test checks that against the Hub and against the spike in
   * "no history" mode (every level at HEAD).
   */
  @Test
  @Order(4)
  fun `a branch on a versionsToKeep=1 layer`() {
    createSpace(v1Sid, 1)
    P3.putFeatures(v1Sid, listOf(pt("v1", 1.0, 1.0, "v1 main"), pt("v2", 2.0, 2.0, "v2 main"), pt("v3", 3.0, 3.0, "v3 main")))
    val r = hub("POST", "/spaces/$v1Sid/branches", Platform.toJSON(mapOf("id" to "p6probe", "baseRef" to "main:HEAD")))
    val out = linkedMapOf<String, Any?>("createStatus" to r.status, "createBody" to r.body.take(300))
    if (r.status == 200) {
      out["branchInsert"] = P3.hub("PUT", "/spaces/$v1Sid/features", Platform.toJSON(mapOf("type" to "FeatureCollection", "features" to listOf(pt("vb", 4.0, 4.0, "vb branch")))), "application/geo+json", listOf("versionRef" to "p6probe")).status
      out["branchDeleteInherited"] = hub("DELETE", "/spaces/$v1Sid/features/v3", query = listOf("versionRef" to "p6probe")).status
      // main changes after the branch point
      P3.putFeatures(v1Sid, listOf(pt("v1", 1.5, 1.5, "v1 main after branch")))
      out["mainDelete"] = hub("DELETE", "/spaces/$v1Sid/features/v2").status
      val hubBranch = Parity.hubOk("/spaces/$v1Sid/iterate", listOf("limit" to "100", "versionRef" to "p6probe")).features
      out["hubBranch"] = hubBranch.map { "${it["id"]}:${((it["properties"] as Map<*, *>)["name"])}" }.sorted()
      val root = checkNotNull(P3.table(v1Sid))
      P3.db { c ->
        val levels = resolvePath(c, "public", root, (adminBranch(v1Sid, "p6probe")["nodeId"] as Number).toInt())
        out["branchTableRows"] = c.createStatement().use { st ->
          st.executeQuery("SELECT id, version, next_version, operation FROM public.\"${levels.last().table}\" ORDER BY i").use { rs ->
            generateSequence { if (rs.next()) "${rs.getString(1)}:${rs.getLong(2)}:${if (rs.getLong(3) == Long.MAX_VALUE) "MAX" else rs.getLong(3)}:${rs.getString(4)}" else null }.toList()
          }
        }
        val spikeHistory = read(c, levels, null)
        val spikeHead = c.prepareStatement(composedSql("public", levels, null, "TRUE", false, history = false)).use { st ->
          st.executeQuery().use { rs -> generateSequence { if (rs.next()) toFeature(rs.getString("id"), rs.getLong("version"), rs.getString("operation"), rs.getString("jsondata"), rs.getString("geojson")) else null }.toList() }
        }
        out["spikeHistoryMode"] = record(diff("p6-branch-v1-history-mode", hubBranch, spikeHistory)).summary()
        out["spikeHeadMode"] = record(diff("p6-branch-v1-head-mode", hubBranch, spikeHead)).summary()
      }
    }
    observe("p6-branch-on-v1", out)
  }

  @Test
  @Order(5)
  fun `branch read on 120 000 features - single statement vs Hub iterate`() {
    assumeTrue(System.getenv("IML_P3_LARGE") == "true", "Set IML_P3_LARGE=true for the 120k timing")
    createSpace(bigSid, 100)
    val n = 120_000
    for (chunk in (0 until n).chunked(10_000)) {
      P3.putFeatures(bigSid, chunk.map { pt("g%06d".format(it), 8.0 + (it % 400) * 0.001, 50.0 + (it / 400) * 0.001, "g $it") })
    }
    branch(bigSid, "bx", "main:HEAD")
    P3.putFeatures(bigSid, (0 until 100).map { pt("g%06d".format(it * 997), 9.0, 51.0, "updated in bx") }, listOf("versionRef" to "bx"))
    for (k in 0 until 10) delete(bigSid, "g%06d".format(k * 7919 + 3), "bx")
    P3.putFeatures(bigSid, (0 until 10).map { pt("bx%02d".format(it), 9.5, 51.5, "bx only") }, listOf("versionRef" to "bx"))
    val root = checkNotNull(P3.table(bigSid))
    val out = LinkedHashMap<String, Any?>()
    P3.db { c ->
      val levels = resolvePath(c, "public", root, (adminBranch(bigSid, "bx")["nodeId"] as Number).toInt())
      val sql = composedSql("public", levels, null, "TRUE", false)
      fun spikeStrings(): Pair<Int, Long> = Parity.timed {
        c.autoCommit = false
        try {
          c.prepareStatement(sql).use { st ->
            st.fetchSize = 10_000
            st.executeQuery().use { rs -> var k = 0; while (rs.next()) { rs.getString("id"); rs.getString("jsondata"); rs.getString("geojson"); k++ }; k }
          }
        } finally { c.autoCommit = true }
      }
      fun spikeParsed(): Pair<List<Map<*, *>>, Long> = Parity.timed {
        c.autoCommit = false
        try {
          c.prepareStatement(sql).use { st ->
            st.fetchSize = 10_000
            st.executeQuery().use { rs -> generateSequence { if (rs.next()) toFeature(rs.getString("id"), rs.getLong("version"), rs.getString("operation"), rs.getString("jsondata"), rs.getString("geojson")) else null }.toList() }
          }
        } finally { c.autoCommit = true }
      }
      val runs = (1..3).map {
        val (count, sMs) = spikeStrings()
        val (parsed, pMs) = spikeParsed()
        val (hub, hMs) = Parity.timed { Parity.hubIterateAll(bigSid, 10_000, listOf("versionRef" to "bx")).first }
        Triple(Triple(count, sMs, pMs), hMs, Pair(hub, parsed))
      }
      val (hub, parsed) = runs.last().third
      val d = record(diff("p6-branch-120k", hub, parsed))
      out["rows"] = runs.map { it.first.first }
      out["spikeStringsMs"] = runs.map { it.first.second }
      out["spikeParsedMs"] = runs.map { it.first.third }
      out["hubIterateMs"] = runs.map { it.second }
      out["diff"] = d.summary()
      out["explain"] = c.createStatement().use { st -> st.executeQuery("EXPLAIN (ANALYZE, TIMING OFF, SUMMARY ON) $sql").use { rs -> generateSequence { if (rs.next()) rs.getString(1) else null }.toList() } }
      assertTrue(d.sameKeys && d.fields.isEmpty(), d.summary())
    }
    observe("p6-branch-120k-timing", out)
  }
}
