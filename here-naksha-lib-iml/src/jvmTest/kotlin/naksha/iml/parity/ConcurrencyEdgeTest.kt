package naksha.iml.parity

import naksha.base.Version
import naksha.iml.ImlConfig
import naksha.iml.ImlStorage
import naksha.model.Naksha
import naksha.model.request.OrderBy
import naksha.model.request.SuccessResponse
import naksha.model.request.ops.Gt
import naksha.model.request.query.SortOrder
import naksha.iml.parity.EdgeSupport.case
import naksha.iml.parity.Parity.observe
import naksha.iml.parity.Parity.request
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Phase 3, item "concurrency: Hub writes while lib-iml reads (two-phase selection vs materialization; the selected exact state moves to history)".
 * Creates its own spaces `ev-p3-conc-v1` (versionsToKeep=1) and `ev-p3-conc-h` (versionsToKeep=100) with 1 000 points each.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@org.junit.jupiter.api.Timeout(value = 10, unit = java.util.concurrent.TimeUnit.MINUTES)
class ConcurrencyEdgeTest {
  private lateinit var config: ImlConfig
  private lateinit var storage: ImlStorage
  private val v1 = "ev-p3-conc-v1"
  private val hist = "ev-p3-conc-h"
  private val n = 1000

  @BeforeAll
  fun setUp() {
    Parity.assumeEnabled()
    EdgeSupport.recreateSpace(v1, 1)
    EdgeSupport.recreateSpace(hist, 100)
    EdgeSupport.putFeatures(v1, EdgeSupport.grid("c", n, 70.0, 10.0))
    EdgeSupport.putFeatures(hist, EdgeSupport.grid("c", n, 70.0, 10.0))
    // Storage opened after the spaces exist (the metadata cache is filled at init, see LifecycleEdgeTest).
    val (c, s) = Parity.open("iml-eval-p3-concurrency")
    config = c
    storage = s
  }

  @AfterAll
  fun tearDown() {
    if (::config.isInitialized) Naksha.removeStorage(config)
    EdgeSupport.deleteSpace(v1)
    EdgeSupport.deleteSpace(hist)
  }

  private fun reseed(space: String) {
    EdgeSupport.putFeatures(space, EdgeSupport.grid("c", n, 70.0, 10.0) { mapOf("n" to it, "name" to "c $it", "rev" to 0) })
    Naksha.cache.clear(storage)
  }

  /** Selection now, Hub writes in between, materialization afterwards (lazy `response.features`). */
  private fun selectWriteMaterialize(space: String): Map<String, Any?> {
    reseed(space)
    val hubBefore = Parity.hubIterateAll(space, 10_000).first.size
    return storage.newReadSession().use { session ->
      val response = session.executeRead(request(space)) as SuccessResponse
      val selected = response.featureTupleList.size
      val preloaded = response.featureTupleList.count { it?.tuple != null }
      // 100 updates and 50 deletes between selection and materialization.
      EdgeSupport.putFeatures(space, (0 until 100).map { EdgeSupport.point("c%05d".format(it), 70.0 + (it % 100) * 0.001, 10.0, mapOf("n" to it, "name" to "updated", "rev" to 1)) })
      EdgeSupport.deleteFeatures(space, (100 until 150).map { "c%05d".format(it) })
      val features = response.features
      val nonNull = features.filterNotNull()
      val nulls = features.size - nonNull.size
      val updatedSeenOld = nonNull.count { (it.properties["name"] as String?) != "updated" && (it.id < "c00100") }
      val updatedSeenNew = nonNull.count { (it.properties["name"] as String?) == "updated" }
      val deletedSeen = nonNull.count { it.id >= "c00100" && it.id < "c00150" }
      val nextVersions = nonNull.filter { it.id < "c00150" }.map { (it.properties.xyz.getRaw("nextVersion") as Number?)?.toLong() }
      val hubAfter = Parity.hubIterateAll(space, 10_000).first
      linkedMapOf(
        "hubBefore" to hubBefore, "selected" to selected, "preloadedFromCache" to preloaded, "featuresListSize" to features.size,
        "nonNull" to nonNull.size, "nullEntries" to nulls, "updatedSeenAsOldState" to updatedSeenOld, "updatedSeenAsNewState" to updatedSeenNew,
        "deletedStillReturned" to deletedSeen, "changedWithNonHeadNextVersion" to nextVersions.count { it != null && it != Version.HEAD.number },
        "hubAfter" to hubAfter.size, "hubAfterUpdated" to hubAfter.count { (it["properties"] as Map<*, *>)["name"] == "updated" }
      )
    }
  }

  @Test
  fun `v1 layer - writes between selection and materialization`() {
    val o = selectWriteMaterialize(v1)
    observe("conc-v1-select-write-load", o)
    assertEquals(n, o["selected"])
    case("1 concurrency", "versionsToKeep=1: 100 updates + 50 deletes between selection and lazy materialization",
      "Hub reads are one statement: a consistent snapshot, either before (1000) or after (950) the writes",
      "selected ${o["selected"]}, response.features has ${o["featuresListSize"]} entries of which ${o["nullEntries"]} are null; " +
        "${o["nonNull"]} features returned, updated ones as old state=${o["updatedSeenAsOldState"]}, as new state=${o["updatedSeenAsNewState"]}, deleted still returned=${o["deletedStillReturned"]}",
      "High: silent loss of every feature touched in between (null entries, no error); a mix that matches neither IML snapshot")
  }

  @Test
  fun `history layer - selected exact state moves to history`() {
    val o = selectWriteMaterialize(hist)
    observe("conc-hist-select-write-load", o)
    assertEquals(n, o["selected"])
    case("1 concurrency", "versionsToKeep=100: 100 updates + 50 deletes between selection and lazy materialization",
      "Hub: consistent snapshot (1000 before or 950 after)",
      "${o["nonNull"]} of ${o["selected"]} materialized, null=${o["nullEntries"]}; updated ones returned as old state=${o["updatedSeenAsOldState"]}, deleted ones still returned=${o["deletedStillReturned"]}; " +
        "changed ones carry nextVersion≠HEAD: ${o["changedWithNonHeadNextVersion"]}",
      "OK: exact (i, version) states are found after they move to history, so the result is the selection-time snapshot")
  }

  @Test
  fun `history layer - cached tuple keeps a stale nextVersion`() {
    reseed(hist)
    val id = "c00007"
    val (headNv, oldVersion) = storage.newReadSession().use { s ->
      val f = (s.executeRead(request(hist) { queryMembers = naksha.model.request.ops.Equals("id", id) }) as SuccessResponse).features.single()!!
      (f.properties.xyz.getRaw("nextVersion") as Number).toLong() to (f.properties.xyz.getRaw("version") as Number).toLong()
    }
    EdgeSupport.putFeatures(hist, listOf(EdgeSupport.point(id, 70.007, 10.0, mapOf("n" to 7, "name" to "after", "rev" to 2))))
    val dbNext = EdgeSupport.scalar("SELECT next_version FROM public.\"${EdgeSupport.table(hist)}\" WHERE id = ? AND version = ?", id, oldVersion ushr 2) as Long
    // Snapshot read at the old version: the selection finds the old row, the tuple comes from the cache filled by the HEAD read.
    val cachedNv = storage.newReadSession().use { s ->
      val f = (s.executeRead(request(hist) {
        queryMembers = naksha.model.request.ops.Equals("id", id); queryHistory = true; version = Version.manual(oldVersion ushr 2, naksha.base.Action.VERSION).number
      }) as SuccessResponse).features.single()!!
      (f.properties.xyz.getRaw("nextVersion") as Number).toLong()
    }
    Naksha.cache.clear(storage)
    val freshNv = storage.newReadSession().use { s ->
      val f = (s.executeRead(request(hist) {
        queryMembers = naksha.model.request.ops.Equals("id", id); queryHistory = true; version = Version.manual(oldVersion ushr 2, naksha.base.Action.VERSION).number
      }) as SuccessResponse).features.single()!!
      (f.properties.xyz.getRaw("nextVersion") as Number).toLong()
    }
    val o = linkedMapOf("headReadNextVersion" to headNv, "dbNextVersionAfterUpdate" to dbNext, "cachedSnapshotNextVersion" to cachedNv, "freshSnapshotNextVersion" to freshNv,
      "freshUnpacked" to (freshNv ushr 2), "stale" to (cachedNv == Version.HEAD.number))
    observe("conc-cache-stale-next-version", o)
    case("1 concurrency", "tuple cache after the state moved to history (snapshot read of the old version)",
      "Hub: no client cache; nextVersion is not exposed at all",
      "cached tuple nextVersion=${if (cachedNv == Version.HEAD.number) "HEAD" else cachedNv} vs DB next_version=$dbNext; after cache clear: ${freshNv ushr 2}",
      "Low: stale nextVersion (and nuuid-derived logic) from the cache; the body is correct")
  }

  @Test
  fun `lazy materialization in a worker thread`() {
    // The session gets explicit options; the thread has no NakshaContext appId.
    var outcome = "?"
    val t = Thread {
      outcome = try {
        storage.newReadSession(naksha.model.SessionOptions(appId = "iml-eval-parity")).use { s ->
          val r = s.executeRead(request(v1) { limit = 10 }) as SuccessResponse
          val selected = r.featureTupleList.size
          try { "selection ok ($selected), materialization ok (${r.features.filterNotNull().size})" } catch (e: Throwable) { "selection ok ($selected), materialization: ${EdgeSupport.describe(e)}" }
        }
      } catch (e: Throwable) { "selection: ${EdgeSupport.describe(e)}" }
    }
    Naksha.cache.clear(storage)
    t.start(); t.join(60_000)
    observe("conc-worker-thread-lazy-load", mapOf("outcome" to outcome))
    case("1 concurrency", "read in a worker thread with explicit SessionOptions(appId), then response.features",
      "Hub: n/a (stateless HTTP)",
      outcome,
      "Medium: lazy loading opens its own session from the thread's NakshaContext (lib-model TupleCache.kt:206 newReadSession()), ignoring the caller's options")
  }

  @Test
  fun `continuous Hub writes during repeated lib reads`() {
    reseed(v1)
    reseed(hist)
    val stop = AtomicBoolean(false)
    val writes = AtomicInteger()
    val writeMs = ArrayList<Long>()
    val writer = Thread {
      var rev = 10
      while (!stop.get()) {
        rev++
        val batch = (0 until 50).map { k -> val i = (rev * 37 + k * 19) % n; EdgeSupport.point("c%05d".format(i), 70.0 + (i % 100) * 0.001, 10.0 + (i / 100) * 0.001, mapOf("n" to i, "name" to "c $i", "rev" to rev)) }
        for (space in listOf(v1, hist)) {
          val r = EdgeSupport.putFeatures(space, batch)
          synchronized(writeMs) { writeMs.add(r.millis) }
        }
        writes.addAndGet(2)
        Thread.sleep(50)
      }
    }.apply { isDaemon = true; start() }
    val results = LinkedHashMap<String, Any?>()
    try {
      for (space in listOf(v1, hist)) {
        var nulls = 0
        var short = 0
        var errors = 0
        var dupes = 0
        val rounds = 20
        for (round in 0 until rounds) {
          Naksha.cache.clear(storage)
          try {
            storage.newReadSession().use { s ->
              val r = s.executeRead(request(space)) as SuccessResponse
              val f = r.features
              val missing = f.count { it == null }
              nulls += missing
              if (missing > 0) short++
              val ids = f.filterNotNull().map { it.id }
              dupes += ids.size - ids.toSet().size
            }
          } catch (e: Throwable) {
            errors++
            results["$space error"] = EdgeSupport.describe(e)
          }
        }
        results[space] = mapOf("rounds" to rounds, "roundsWithMissing" to short, "missingTotal" to nulls, "duplicates" to dupes, "errors" to errors)
      }
      // Keyset paging (Phase 2 workaround) while writes continue: on a history layer an update gets a new i and is emitted again.
      for (space in listOf(v1, hist)) {
        val ids = ArrayList<String>()
        storage.newReadSession().use { s ->
          var last = 0L
          var pages = 0
          while (pages++ < 200) {
            val r = s.executeRead(request(space) { queryMembers = Gt("fn", last); orderBy = OrderBy("fn", SortOrder.ASCENDING); limit = 100 }) as SuccessResponse
            val t = r.featureTupleList
            if (t.isEmpty()) break
            ids += r.features.filterNotNull().map { it.id }
            last = t.last()!!.tupleNumber.featureNumber
            Thread.sleep(20)
          }
        }
        val hubIds = ArrayList<String>()
        var token: String? = null
        do {
          val q = arrayListOf("limit" to "100").apply { if (token != null) add("handle" to token!!) }
          val r = Parity.hubOk("/spaces/$space/iterate", q)
          hubIds += r.features.map { it["id"] as String }
          token = r.field("nextPageToken") as String?
          Thread.sleep(20)
        } while (token != null && hubIds.size < 20_000)
        results["$space keyset"] = mapOf("libRows" to ids.size, "libDistinct" to ids.toSet().size, "libStoppedAtCap(200 pages)" to (ids.size >= 20_000),
          "hubRows" to hubIds.size, "hubDistinct" to hubIds.toSet().size, "hubStoppedAtCap" to (hubIds.size >= 20_000))
      }
    } finally {
      stop.set(true)
      writer.join(30_000)
    }
    results["hubWrites"] = writes.get()
    results["hubWriteMs(median,max)"] = synchronized(writeMs) { writeMs.sorted().let { listOf(it[it.size / 2], it.last()) } }
    observe("conc-continuous", results)
    val v1r = results[v1] as Map<*, *>
    val hr = results[hist] as Map<*, *>
    case("1 concurrency", "20 full reads (1000 features) while the Hub writes 50-feature batches continuously",
      "Hub: every read is a consistent snapshot, no gaps",
      "v1: ${v1r["roundsWithMissing"]}/20 reads had null entries (${v1r["missingTotal"]} total), errors=${v1r["errors"]}; history: ${hr["roundsWithMissing"]}/20 with nulls, errors=${hr["errors"]}; duplicates: v1=${v1r["duplicates"]}, hist=${hr["duplicates"]}",
      "High for v1 layers (silent gaps), none for history layers")
    case("1 concurrency", "keyset paging over fn (limit 100) while the Hub writes",
      "Hub /iterate (same i keyset): may re-emit features updated during the iteration on history layers (internals doc §5.5, inferred)",
      "lib: ${results["$v1 keyset"]} (v1), ${results["$hist keyset"]} (history)",
      "Medium: same behaviour as IML; callers must de-duplicate by id or pin a version")
    assertTrue((results["hubWrites"] as Int) > 0)
  }
}
