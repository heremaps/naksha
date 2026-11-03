@file:OptIn(naksha.model.v30_experimental::class)

package naksha.iml.parity

import naksha.base.Action
import naksha.base.Platform
import naksha.base.TupleNumber
import naksha.base.Version
import naksha.iml.ImlConfig
import naksha.iml.ImlStorage
import naksha.iml.ImlTuple
import naksha.iml.parity.P5.row
import naksha.iml.parity.Parity.observe
import naksha.model.Naksha
import naksha.model.NakshaContext
import naksha.model.SessionOptions
import naksha.model.objects.NakshaFeature
import naksha.model.objects.StoreMode
import naksha.model.request.SuccessResponse
import naksha.model.request.Write
import naksha.model.request.WriteRequest
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestMethodOrder
import java.sql.DriverManager

/**
 * Phase 5 (code review): evidence for findings that phases 1–4 did not cover, and checks of proposed fixes.
 *
 * Characterization tests: they record today's behaviour (`reports/review/<scenario>.json`, `REVIEW|` rows) and assert only their fixture.
 * Everything runs on scratch schemas ([P5.Scratch]) or read-only on the Phase 1–3 seed layers; no Hub-owned object is locked, dropped or written.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@org.junit.jupiter.api.Timeout(value = 10, unit = java.util.concurrent.TimeUnit.MINUTES)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class P5ReviewTest {
  private lateinit var scratch: P5.Scratch
  private val rnd = P5.rnd()
  private val extraConfigs = ArrayList<ImlConfig>()

  @BeforeAll
  fun setUp() {
    Parity.assumeEnabled()
    NakshaContext.currentContext().withAppId("iml-eval-parity")
    scratch = P5.Scratch("r")
    scratch.addLayer("p5-lock", "t_lock", "ev-v1")
    scratch.addLayer("p5-free", "t_free", "ev-hist")
    scratch.addLayer("p5-hist", "t_hist", "ev-hist")
  }

  @AfterAll
  fun tearDown() {
    for (c in extraConfigs) runCatching { Naksha.removeStorage(c) }
    if (::scratch.isInitialized) scratch.close()
  }

  private fun opts(appName: String, lockTimeout: Int = 30_000) =
    SessionOptions(appId = "iml-eval-parity", appName = appName, lockTimeout = lockTimeout, stmtTimeout = 30_000)

  /**
   * F28 (new). `ImlStorage.newReadSession` and `shutdownStorage` are `@Synchronized` on the storage, and `newReadSession` waits for the
   * storage's single connection permit while holding that monitor (ImlStorage.kt:76-90, 145-158). `Naksha.removeStorage` holds the
   * global `Naksha.lock` while it calls `shutdownStorage` (lib-model Naksha.kt:437-455). So one slow read can block storage registration JVM-wide.
   */
  @Test
  @Order(1)
  fun `a stalled read holds the storage monitor and freezes the global storage registry`() {
    val o = LinkedHashMap<String, Any?>()
    // The URL carries an ApplicationName as well, to see whether the URL or the session option wins (appendix item).
    val (configA, storageA) = scratch.open("iml-eval-p5-stall-$rnd") { jdbcUrl = Parity.dbUrl!! + "&ApplicationName=p5-url-$rnd" }
    val (configB, _) = scratch.open("iml-eval-p5-bystander-$rnd")
    val other = ImlConfig("iml-eval-p5-other-$rnd", Parity.dbUrl!!).also { extraConfigs.add(it) }
    val probes = ArrayList<P5.Probe>()
    val lock = P5.lock(scratch.sqlTable("t_lock"))
    try {
      val reader = P5.Probe("reader") {
        storageA.newReadSession(opts("p5-opt-$rnd")).use { s ->
          val r = s.executeRead(scratch.request("p5-lock") { limit = 5 }) as SuccessResponse
          val n = r.featureTupleList.size
          "selection ok ($n); materialization: " + try { "ok ${r.features.filterNotNull().size}" } catch (e: Throwable) { P3.describe(e) }
        }
      }.start()
      val waitMs = P5.waitFor(10_000) { P5.lockWaiters("t_lock").isNotEmpty() }
      o["readerBlockedOnLockAfterMs"] = waitMs
      o["blockedBackendApplicationName"] = P5.lockWaiters("t_lock").map { it.second }
      o["urlApplicationName"] = "p5-url-$rnd"
      o["sessionOptionAppName"] = "p5-opt-$rnd"
      val second = P5.Probe("newReadSession") { storageA.newReadSession(opts("p5-second-$rnd")).use { "session opened" } }.start()
      Thread.sleep(300)
      val remove = P5.Probe("removeStorage") { "removed=${Naksha.removeStorage(configA) != null}" }.start()
      Thread.sleep(300)
      val unrelated = P5.Probe("useStorage(other)") { "registered ${Naksha.useStorage(other).id}" }.start()
      val bystander = P5.Probe("useStorage(already registered)") { "fast path ${Naksha.useStorage(configB).id}" }.start()
      probes += listOf(reader, second, remove, unrelated, bystander)
      Thread.sleep(5_000)
      o["at5s"] = linkedMapOf(
        "reader" to reader.running, "newReadSession" to second.running, "removeStorage" to remove.running,
        "useStorage(other, not registered)" to unrelated.running, "useStorage(already registered)" to bystander.running
      )
      o["lockHeldMs"] = 5_600 + waitMs
    } finally {
      P5.release(lock)
    }
    val released = System.currentTimeMillis()
    probes.forEach { it.join(60_000) }
    o["afterRelease"] = probes.associate { p -> p.name to (p.summary() + ("finishedAfterReleaseMs" to (p.startedAt + p.elapsedMs - released))) }
    observe("p5-registry-stall", o)
    @Suppress("UNCHECKED_CAST") val at5 = o["at5s"] as Map<String, Boolean>
    row("F28 (new)", "slow read on storage A (table lock 5.6 s); meanwhile newReadSession(A), Naksha.removeStorage(A), Naksha.useStorage(unrelated storage)",
      "only storage A's readers wait (IML: one slow request doesn't block other requests)",
      "still blocked after 5 s: " + at5.filterValues { it }.keys.joinToString() + "; not blocked: " + at5.filterValues { !it }.keys.joinToString(),
      "High: the storage monitor is held across a blocking permit wait, and removeStorage holds the global Naksha.lock")
    row("appendix A6", "ApplicationName both in the JDBC URL and in SessionOptions.appName",
      "session options apply (ImlStorage.kt:163-167 sets them as driver properties)",
      "blocked backend's application_name: ${o["blockedBackendApplicationName"]} (URL p5-url-$rnd, options p5-opt-$rnd)",
      "Low: URL parameters override per-session settings (same mechanism for socketTimeout/connectTimeout, inferred)")
    assertTrue((o["readerBlockedOnLockAfterMs"] as Long) >= 0, "fixture: the reader must block on the table lock")
  }

  /**
   * `ImlStorage.withConnection` calls `connection.rollback()` in a `finally` block (ImlStorage.kt:145-158). If the read failed because the
   * connection died, the rollback throws too, and Kotlin's `finally` replaces the original exception.
   */
  @Test
  @Order(2)
  fun `rollback in withConnection replaces the original error of a killed backend`() {
    val o = LinkedHashMap<String, Any?>()
    val (_, storage) = scratch.open("iml-eval-p5-kill-$rnd")
    val app = "p5-kill-$rnd"
    var thrown: Throwable? = null
    val lock = P5.lock(scratch.sqlTable("t_lock"))
    try {
      val t = Thread {
        NakshaContext.currentContext().withAppId("iml-eval-parity")
        try {
          storage.newReadSession(opts(app)).use { it.executeRead(scratch.request("p5-lock") { limit = 5 }) }
        } catch (e: Throwable) { thrown = e }
      }.apply { start() }
      P5.waitFor(10_000) { P5.lockWaiters("t_lock").any { it.second == app } }
      o["terminated"] = P5.lockWaiters("t_lock").filter { it.second == app }.map { P5.terminate(it.first) }
      t.join(30_000)
    } finally {
      P5.release(lock)
    }
    o["libChain"] = thrown?.let { P5.chain(it) } ?: "no exception"
    // The same statement on a plain JDBC connection, to see which error the driver reports first.
    val rawApp = "p5-raw-$rnd"
    val raw = LinkedHashMap<String, Any?>()
    val lock2 = P5.lock(scratch.sqlTable("t_lock"))
    try {
      val c = DriverManager.getConnection(Parity.dbUrl!! + "&ApplicationName=$rawApp")
      c.autoCommit = false
      val t = Thread {
        try { c.createStatement().use { it.executeQuery("SELECT id FROM ${scratch.sqlTable("t_lock")} LIMIT 1").close() }; raw["query"] = "ok" }
        catch (e: Throwable) { raw["query"] = P5.chain(e) }
        try { c.rollback(); raw["rollback"] = "ok" } catch (e: Throwable) { raw["rollback"] = P5.chain(e) }
        runCatching { c.close() }
      }.apply { start() }
      P5.waitFor(10_000) { P5.lockWaiters("t_lock").any { it.second == rawApp } }
      P5.lockWaiters("t_lock").filter { it.second == rawApp }.forEach { P5.terminate(it.first) }
      t.join(30_000)
    } finally {
      P5.release(lock2)
    }
    o["rawJdbc"] = raw
    observe("p5-masked-error", o)
    val states = thrown?.let { P5.sqlStates(it) } ?: emptySet()
    row("A7 (new, part of F13)", "pg_terminate_backend while a lib-iml read waits; same on a raw JDBC connection",
      "the caller sees the original error (raw JDBC: ${raw["query"]})",
      "lib-iml throws ${o["libChain"]}",
      if (states.contains("57P01")) "OK: original error preserved" else "Medium: the original SQLState is lost; only the rollback's 08003 reaches the caller")
    assertTrue(thrown != null, "fixture: the killed read must fail")
  }

  /**
   * Write path: `ImlMutation.collection()` deletes every non-HEAD row when an update sets `storeHistory = OFF`
   * (ImlMutation.kt:135-138), and `createIndices` adds adapter indexes to the table (ImlMutation.kt:303-336).
   * Run on a scratch copy of the Hub table of `ev-hist`, registered like the Hub registers it (no `nakshaCollection`).
   */
  @Test
  @Order(3)
  fun `collection update with storeHistory OFF purges a Hub-layout table and adds indexes`() {
    val o = LinkedHashMap<String, Any?>()
    val table = scratch.sqlTable("t_hist")
    fun counts() = linkedMapOf(
      "rows" to P3.scalar("SELECT count(*) FROM $table"),
      "historyRows" to P3.scalar("SELECT count(*) FROM $table WHERE next_version <> 9223372036854775807"),
      "indexes" to P3.db { c -> c.prepareStatement("SELECT indexname FROM pg_indexes WHERE schemaname = ? AND tablename = 't_hist' ORDER BY 1").use { st -> st.setString(1, scratch.data); st.executeQuery().use { rs -> generateSequence { if (rs.next()) rs.getString(1) else null }.toList() } } },
      "spaceMetaKeys" to P3.scalar("SELECT string_agg(k, ',') FROM \"${scratch.meta}\".space_meta, jsonb_object_keys(meta) k WHERE id = 'p5-hist'")
    )
    o["before"] = counts()
    val (_, storage) = scratch.open("iml-eval-p5-purge-$rnd") { writeEnabled = true }
    val context = NakshaContext.currentContext()
    val previous = context.su
    context.su = true
    o["outcome"] = try {
      storage.newWriteSession().use { w ->
        val catalog = checkNotNull(w.getCatalogById(scratch.data))
        val collection = checkNotNull(w.getCollectionById(catalog, "p5-hist"))
        o["descriptorBefore"] = mapOf("storeHistory" to collection.storeHistory.toString(), "storeDeleted" to collection.storeDeleted.toString())
        collection.storeHistory = StoreMode.OFF
        w.executeWrite(WriteRequest().add(Write().updateCollection(collection, false)))
        w.commit()
        "committed"
      }
    } catch (e: Throwable) { P3.describe(e) } finally { context.su = previous }
    o["after"] = counts()
    observe("p5-collection-update-purge", o)
    @Suppress("UNCHECKED_CAST") val before = o["before"] as Map<String, Any?>
    @Suppress("UNCHECKED_CAST") val after = o["after"] as Map<String, Any?>
    row("H-W2 (new)", "write session (su, writeEnabled) updates the descriptor of a Hub-registered layer: storeHistory=OFF",
      "IML: a layer's history is changed only by the Hub (versionsToKeep can't go from >1 to 1, SpaceTaskHandler.java:311-344)",
      "${o["outcome"]}; rows ${before["rows"]} -> ${after["rows"]}, history rows ${before["historyRows"]} -> ${after["historyRows"]}; " +
        "indexes ${(before["indexes"] as List<*>).size} -> ${(after["indexes"] as List<*>).size}; space_meta.meta keys ${before["spaceMetaKeys"]} -> ${after["spaceMetaKeys"]}",
      "High (write path): irreversible history loss and DDL on a table the Hub owns, from a metadata update")
    assertTrue((before["historyRows"] as Number).toLong() > 0, "fixture: the ev-hist copy must contain history rows")
  }

  /** Connections per operation (newReadSession validation, selection, exact load, lazy load), and the extra refresh with writeEnabled. */
  @Test
  @Order(4)
  fun `JDBC connections per session and per read`() {
    val o = LinkedHashMap<String, Any?>()
    for (write in listOf(false, true)) {
      val (_, storage) = scratch.open("iml-eval-p5-conn-$write-$rnd") { writeEnabled = write }
      val m = LinkedHashMap<String, Any?>()
      P3.countingConnections { d ->
        var before = d.connects.get()
        val session = storage.newReadSession(opts("p5-conn"))
        m["newReadSession"] = d.connects.get() - before
        before = d.connects.get()
        val r = session.executeRead(scratch.request("p5-free") { limit = 5 }) as SuccessResponse
        m["executeRead (selection)"] = d.connects.get() - before
        before = d.connects.get()
        session.loadTuples(r.featureTupleList, 0, r.featureTupleList.size)
        m["session.loadTuples"] = d.connects.get() - before
        Naksha.cache.clear(storage)
        val r2 = session.executeRead(scratch.request("p5-free") { limit = 5 }) as SuccessResponse
        before = d.connects.get()
        r2.features.size
        m["response.features (lazy, after cache clear)"] = d.connects.get() - before
        session.close()
      }
      o["writeEnabled=$write"] = m
    }
    observe("p5-connections", o)
    row("A8 (new)", "connections for newReadSession + one 5-feature read (selection + loadTuples), writeEnabled=false vs true",
      "IML/Hub: pooled connections, 0 new connections per request",
      o.toString(), "Medium: a connection handshake per step; writeEnabled adds a metadata refresh connection to every read (ImlReadSession.kt:96)")
  }

  // F1 fix check ------------------------------------------------------------------------------------------------

  private val intToken = Regex("""(?<![\w.\-+])(-?\d+)(?![\d.eE])""")

  /** The fix proposed in 01-setup-usability.md F1: every integer token in ST_AsGeoJSON text becomes a double. */
  private fun geometryDoubles(json: String): String = intToken.replace(ImlTuple.jsonNumbers(json)) { it.value + ".0" }

  /** Exactly what ImlTuple.read does with the geometry column (ImlTuple.kt:60-61), then encode → decode as in the exact load. */
  private fun throughTuple(id: String, geometryText: String, fixed: Boolean): Any? {
    val feature = NakshaFeature(id)
    feature.setRaw(NakshaFeature.GEOMETRY, Platform.fromJSON(if (fixed) geometryDoubles(geometryText) else ImlTuple.jsonNumbers(geometryText)))
    val tuple = ImlTuple.encode(feature, TupleNumber(1L, 1, 1, 1L, Version.manual(1, Action.CREATE).number))
    return Platform.fromJSON(Platform.toJSON(tuple.decodeFeature(null).getRaw(NakshaFeature.GEOMETRY)))
  }

  @Test
  @Order(5)
  fun `F1 fix check - integer tokens as doubles through the real tuple encoder`() {
    val o = LinkedHashMap<String, Any?>()
    val perLayer = LinkedHashMap<String, Any?>()
    for (space in listOf("ev-hist", "ev-v1", "ev-p3-geo")) {
      val table = checkNotNull(P3.table(space))
      val ids = P3.db { c -> c.createStatement().use { st -> st.executeQuery("SELECT id FROM public.\"$table\" WHERE next_version = 9223372036854775807 AND geo IS NOT NULL ORDER BY id").use { rs -> generateSequence { if (rs.next()) rs.getString(1) else null }.toList() } } }
      var currentOk = 0
      var fixedOk = 0
      var skipped = 0
      val currentFails = ArrayList<String>()
      val fixedFails = ArrayList<String>()
      for (id in ids) {
        val text = try {
          P3.db { c -> c.prepareStatement("SELECT ST_AsGeoJSON(geo, 15, 0) FROM public.\"$table\" WHERE id = ? AND next_version = 9223372036854775807").use { st -> st.setString(1, id); st.executeQuery().use { rs -> rs.next(); rs.getString(1) } } }
        } catch (e: Throwable) { skipped++; continue } // nested GeometryCollection: ST_AsGeoJSON itself fails (XX000, Phase 3)
        val reference = Platform.fromJSON(text)
        // 1e-7: TWKB keeps 7 decimals (F20), so a correct decode differs by at most 5e-8.
        val current = runCatching { P5.coordinateDiff(reference, throughTuple(id, text, false), 1e-7) }.getOrElse { "error ${P3.describe(it)}" }
        val fixed = runCatching { P5.coordinateDiff(reference, throughTuple(id, text, true), 1e-7) }.getOrElse { "error ${P3.describe(it)}" }
        if (current == null) currentOk++ else currentFails += "$id: $current"
        if (fixed == null) fixedOk++ else fixedFails += "$id: $fixed"
      }
      perLayer[space] = linkedMapOf("features" to ids.size, "skippedStAsGeoJsonError" to skipped, "currentCorrect" to currentOk, "fixedCorrect" to fixedOk,
        "currentFailures" to currentFails.take(12), "fixedFailures" to fixedFails.take(12))
    }
    o["layers"] = perLayer
    // Faithfulness of the replica: lib-iml's real read of ev-hist must give the same geometry as throughTuple(current).
    val (_, storage) = Parity.open("iml-eval-p5-f1-$rnd").also { extraConfigs.add(it.first) }
    val libGeometries = storage.newReadSession().use { s ->
      val r = s.executeRead(Parity.request("ev-hist")) as SuccessResponse
      s.loadTuples(r.featureTupleList, 0, r.featureTupleList.size)
      r.features.filterNotNull().associate { it.id to Platform.fromJSON(Platform.toJSON(it.getRaw(NakshaFeature.GEOMETRY))) }
    }
    val table = checkNotNull(P3.table("ev-hist"))
    var same = 0
    val notSame = ArrayList<String>()
    for ((id, g) in libGeometries) {
      if (g == null) continue
      val text = P3.db { c -> c.prepareStatement("SELECT ST_AsGeoJSON(geo, 15, 0) FROM public.\"$table\" WHERE id = ? AND next_version = 9223372036854775807").use { st -> st.setString(1, id); st.executeQuery().use { rs -> rs.next(); rs.getString(1) } } }
      val d = P5.coordinateDiff(throughTuple(id, text, false), g, 0.0)
      if (d == null) same++ else notSame += "$id: $d"
    }
    o["replicaFaithful"] = linkedMapOf("ev-hist features" to libGeometries.size, "identicalToReplica" to same, "differences" to notSame.take(5))
    observe("p5-f1-fix-check", o)
    @Suppress("UNCHECKED_CAST") val layers = perLayer as Map<String, Map<String, Any?>>
    row("F1", "proposed fix (integer tokens → doubles before Platform.fromJSON) through ImlTuple.encode/decode, vs PostGIS",
      "all geometries equal to PostGIS within 1e-7 (TWKB precision)",
      layers.entries.joinToString("; ") { (k, v) -> "$k: current ${v["currentCorrect"]}/${v["features"]}, fixed ${v["fixedCorrect"]}/${v["features"]}" + if (v["skippedStAsGeoJsonError"] != 0) " (${v["skippedStAsGeoJsonError"]} skipped: ST_AsGeoJSON error)" else "" } +
        "; replica = real lib read for ${same}/${libGeometries.size} ev-hist features",
      "fix verified if 'fixed' = all")
  }

  @Test
  @Order(6)
  fun `F2 fix check - hide operations as tombstones vs the Hub's extension context`() {
    val o = LinkedHashMap<String, Any?>()
    val table = checkNotNull(P3.table("ev-ext"))
    fun ids(where: String): List<String> = P3.db { c -> c.createStatement().use { st -> st.executeQuery("SELECT id FROM public.\"$table\" WHERE $where ORDER BY id").use { rs -> generateSequence { if (rs.next()) rs.getString(1) else null }.toList() } } }
    val current = ids("next_version = 9223372036854775807 AND operation != 'D'") // ImlHistory.kt:58 today
    val fixed = ids("next_version = 9223372036854775807 AND operation NOT IN ('D','H','J')")
    val operations = P3.db { c -> c.createStatement().use { st -> st.executeQuery("SELECT operation, count(*) FROM public.\"$table\" WHERE next_version = 9223372036854775807 GROUP BY 1 ORDER BY 1").use { rs -> generateSequence { if (rs.next()) "${rs.getString(1)}=${rs.getLong(2)}" else null }.toList() } } }
    val (ext, _) = Parity.hubIterateAll("ev-ext", 1000, listOf("context" to "extension"))
    val extLive = ext.filter { ((it["properties"] as Map<*, *>)["@ns:com:here:xyz"] as Map<*, *>)["deleted"] != true }.map { it["id"] as String }.sorted()
    val (composite, _) = Parity.hubIterateAll("ev-ext", 1000)
    val meta = P3.scalar("SELECT meta::text FROM xyz_config.space_meta WHERE id = 'ev-ext'")
    o["headOperations"] = operations
    o["libToday"] = current
    o["libWithFix"] = fixed
    o["hubExtensionContextNotDeleted"] = extLive
    o["hubDefaultContextCount"] = composite.size
    o["spaceMetaMeta"] = meta
    observe("p5-f2-fix-check", o)
    row("F2", "HEAD of ev-ext: operation != 'D' (today) vs NOT IN ('D','H','J') (fix), vs Hub context=extension without deleted",
      "the fix equals the Hub's extension view; the composite view (Hub default, ${composite.size} features) needs composition or an explicit rejection via space_meta.meta.extends ($meta)",
      "today ${current.size} ${current}; fix ${fixed.size}; Hub extension live ${extLive.size}; equal=${fixed == extLive}",
      if (fixed == extLive) "fix verified for the delta view" else "fix does not match")
  }
}
