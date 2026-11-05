package naksha.iml.parity

import naksha.iml.ImlConfig
import naksha.iml.ImlStorage
import naksha.model.Naksha
import naksha.model.request.SuccessResponse
import naksha.model.request.query.AnyOp
import naksha.model.request.query.DoubleOp
import naksha.model.request.query.PQuery
import naksha.model.request.query.Property
import naksha.iml.parity.EdgeSupport.case
import naksha.iml.parity.EdgeSupport.describe
import naksha.iml.parity.Parity.observe
import naksha.iml.parity.Parity.request
import naksha.iml.parity.Parity.timed
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

/**
 * Phase 3, item "property search on a layer with 1M rows: correctness (is anything truncated?) and time".
 * Uses `ev-1m` (1 000 000 points, versionsToKeep=1, `scripts/seed-p3.py`): n = 0…999 999, g = n % 10, rare = (n % 250 000 == 7).
 * Slow (minutes); additionally gated by `IML_P3_LARGE=true`.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@org.junit.jupiter.api.Timeout(value = 30, unit = java.util.concurrent.TimeUnit.MINUTES)
class LargeLayerEdgeTest {
  private lateinit var config: ImlConfig
  private lateinit var storage: ImlStorage
  private val space = "ev-1m"

  @BeforeAll
  fun setUp() {
    Parity.assumeEnabled()
    assumeTrue(System.getenv("IML_P3_LARGE") == "true", "Set IML_P3_LARGE=true")
    val (c, s) = Parity.open("iml-eval-p3-1m")
    config = c
    storage = s
  }

  @AfterAll
  fun tearDown() {
    if (::config.isInitialized) Naksha.removeStorage(config)
  }

  private data class Run(val outcome: String, val count: Int, val ms: Long, val heap: String)

  private fun search(storage: ImlStorage, build: naksha.model.request.ReadFeatures.() -> Unit): Run {
    Naksha.cache.clear(storage)
    val heap = EdgeSupport.HeapSampler()
    var count = -1
    val (outcome, ms) = timed {
      try {
        storage.newReadSession().use { s ->
          val r = s.executeRead(request(space, build)) as SuccessResponse
          val f = r.features.filterNotNull()
          count = f.size
          "ok ${f.size}" + if (f.size <= 5) " ${f.map { it.id }.sorted()}" else ""
        }
      } catch (e: Throwable) { describe(e) }
    }
    heap.close()
    Naksha.cache.clear(storage)
    return Run(outcome, count, ms, heap.summary())
  }

  @Test
  fun `property search on 1M rows`() {
    val o = LinkedHashMap<String, Any?>()
    o["dbCount"] = EdgeSupport.scalar("SELECT count(*) FROM public.\"${EdgeSupport.table(space)}\"")
    val (sel, selMs) = timed { storage.newReadSession().use { s -> (s.executeRead(request(space)) as SuccessResponse).featureTupleList.size } }
    o["selectionOnly(all)"] = "$sel tuple numbers in $selMs ms"
    val last = search(storage) { query.properties = PQuery(Property("properties", "n"), DoubleOp.EQ, 999_999) }
    o["p.n=999999"] = last
    val rare = search(storage) { query.properties = PQuery(Property("properties", "rare"), AnyOp.IS_TRUE, null) }
    o["p.rare=true (4 expected)"] = rare
    val tenth = search(storage) { query.properties = PQuery(Property("properties", "g"), DoubleOp.EQ, 7) }
    o["p.g=7 (100 000 expected)"] = tenth
    val firstOnly = search(storage) { limit = 1; query.properties = PQuery(Property("properties", "n"), DoubleOp.EQ, 999_999) }
    o["p.n=999999 limit=1"] = firstOnly
    val early = search(storage) { limit = 10; query.properties = PQuery(Property("properties", "g"), DoubleOp.EQ, 7) }
    o["p.g=7 limit=10"] = early
    // Lower hardCap: silent truncation at 1M scale (H4).
    val (c2, s2) = Parity.open("iml-eval-p3-1m-cap") { hardCap = 500_000 }
    try {
      o["hardCap=500000 p.n=999999"] = search(s2) { query.properties = PQuery(Property("properties", "n"), DoubleOp.EQ, 999_999) }
      o["hardCap=500000 p.rare=true"] = search(s2) { query.properties = PQuery(Property("properties", "rare"), AnyOp.IS_TRUE, null) }
    } finally { Naksha.removeStorage(c2) }
    // Hub: the same searches (big-space rule: > 10 000 rows needs an on-demand index).
    o["hub /search p.n=999999"] = Parity.hub("/spaces/$space/search", raw = "p.n=999999").let { "$it in ${it.millis} ms" }
    o["hub /search p.rare=true"] = Parity.hub("/spaces/$space/search", raw = "p.rare=true").let { "$it in ${it.millis} ms" }
    o["hub /search f.id=m0999999"] = Parity.hub("/spaces/$space/search", raw = "f.id=m0999999").let { "$it in ${it.millis} ms" }
    observe("large-1m-property-search", o)
    fun r(k: String) = (o[k] as Run).let { "${it.outcome} in ${it.ms} ms, ${it.heap}" }
    case("8 1M property search", "p.n=999999 / p.rare=true / p.g=7 on 1 000 000 rows, default hardCap",
      "Hub: ${o["hub /search p.n=999999"]}; ${o["hub /search p.rare=true"]} (no on-demand index; B10: can't be created locally)",
      "n: ${r("p.n=999999")}; rare: ${r("p.rare=true (4 expected)")}; g=7: ${r("p.g=7 (100 000 expected)")}",
      "High (performance): complete and correct, but every search loads and decodes the whole layer")
    case("8 1M property search", "limit=1 / limit=10",
      "Hub: same 400 for unindexed properties",
      "n limit=1: ${r("p.n=999999 limit=1")}; g=7 limit=10: ${r("p.g=7 limit=10")}",
      "Medium: limit only stops the scan once enough matches are found")
    case("8 1M property search", "hardCap=500 000",
      "Hub: explicit 400",
      "n=999999: ${r("hardCap=500000 p.n=999999")}; rare: ${r("hardCap=500000 p.rare=true")}",
      "High: silently incomplete (H4 at 1M scale)")
    assertEquals(1, last.count)
    assertEquals(4, rare.count)
    assertEquals(100_000, tenth.count)
  }

  @Test
  fun `full read of 1M rows - lib vs Hub iterate`() {
    val o = LinkedHashMap<String, Any?>()
    Naksha.cache.clear(storage)
    val heap = EdgeSupport.HeapSampler()
    var selectMs = 0L
    val (count, ms) = timed {
      try {
        storage.newReadSession().use { s ->
          val (r, sel) = timed { s.executeRead(request(space)) as SuccessResponse }
          selectMs = sel
          r.features.count { it != null }.toString()
        }
      } catch (e: Throwable) { describe(e) }
    }
    heap.close()
    Naksha.cache.clear(storage)
    o["libSingleRead"] = "$count features in $ms ms (selection $selectMs ms), ${heap.summary()}"
    val (hub, hubMs) = timed { Parity.hubIterateAll(space, 10_000) }
    o["hubIterate(limit=10000)"] = "${hub.first.size} features, ${hub.second} pages, $hubMs ms"
    // Keyset paging (stateless) keeps the heap bounded.
    val heap2 = EdgeSupport.HeapSampler()
    var pages = 0
    var total = 0
    val (_, keysetMs) = timed {
      storage.newReadSession().use { s ->
        var lastFn = 0L
        while (true) {
          val r = s.executeRead(request(space) {
            queryMembers = naksha.model.request.ops.Gt("fn", lastFn); orderBy = naksha.model.request.OrderBy("fn", naksha.model.request.query.SortOrder.ASCENDING); limit = 10_000
          }) as SuccessResponse
          val t = r.featureTupleList
          if (t.isEmpty()) break
          total += r.features.count { it != null }
          pages++
          lastFn = t.last()!!.tupleNumber.featureNumber
          Naksha.cache.clear(storage)
          if (t.size < 10_000) break
        }
      }
    }
    heap2.close()
    o["libKeyset(limit=10000)"] = "$total features, $pages pages, $keysetMs ms, ${heap2.summary()}"
    observe("large-1m-full-read", o)
    case("8 1M property search", "full read of 1 000 000 rows",
      "Hub: ${o["hubIterate(limit=10000)"]}",
      "lib single read: ${o["libSingleRead"]}; lib keyset: ${o["libKeyset(limit=10000)"]}",
      "High (performance, F15); single read holds the whole layer in heap")
  }
}
