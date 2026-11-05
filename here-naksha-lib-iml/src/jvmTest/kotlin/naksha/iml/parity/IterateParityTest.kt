package naksha.iml.parity

import naksha.iml.ImlConfig
import naksha.iml.ImlStorage
import naksha.model.Naksha
import naksha.model.request.OrderBy
import naksha.model.request.ReadHandle
import naksha.model.request.SuccessResponse
import naksha.model.request.ops.Gt
import naksha.model.request.query.DoubleOp
import naksha.model.request.query.PQuery
import naksha.model.request.query.Property
import naksha.model.request.query.SortOrder
import naksha.iml.parity.Parity.diff
import naksha.iml.parity.Parity.hubIterateAll
import naksha.iml.parity.Parity.observe
import naksha.iml.parity.Parity.outcome
import naksha.iml.parity.Parity.record
import naksha.iml.parity.Parity.request
import naksha.iml.parity.Parity.row
import naksha.iml.parity.Parity.timed
import naksha.iml.parity.Parity.toMaps
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

/**
 * Phase 2 parity, matrix item "/iterate with handle over a layer larger than 100 000 features, and paging with limit".
 * Also tests hypotheses H4 (property search capped by hardCap) and H5 (in-memory paging caps).
 * Uses `ev-big` (120 000 points, versionsToKeep=1, scripts/seed-p2.py) and `ev-hist`.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class IterateParityTest {
  private lateinit var config: ImlConfig
  private lateinit var storage: ImlStorage
  private val big = 120_000

  @BeforeAll
  fun setUp() {
    Parity.assumeEnabled()
    val (c, s) = Parity.open("iml-eval-p2-iterate")
    config = c
    storage = s
  }

  @AfterAll
  fun tearDown() {
    if (::config.isInitialized) Naksha.removeStorage(config)
  }

  @Test
  fun `iterate 120k - hub handle vs lib single read`() {
    val (hubResult, hubMs) = timed { hubIterateAll("ev-big", 10_000) }
    val (hub, pages) = hubResult
    // limit=100000 is the documented maximum, but the page exceeds the hub's 10 MiB response cap (MAX_HTTP_RESPONSE_SIZE) → 513.
    val (hubMaxResponse, hubMaxMs) = timed { Parity.hub("/spaces/ev-big/iterate", listOf("limit" to "100000")) }
    val hubMax = "$hubMaxResponse" to 1
    val hubOver = Parity.hub("/spaces/ev-big/iterate", listOf("limit" to "120000"))

    var selectMs = 0L
    var loadMs = 0L
    val lib = storage.newReadSession().use { s ->
      val (resp, sel) = timed { s.executeRead(request("ev-big")) as SuccessResponse }
      selectMs = sel
      val (features, load) = timed { resp.features.mapNotNull { it } }
      loadMs = load
      toMaps(features)
    }
    val d = record(diff("iterate-120k-single-read", hub, lib, v1Layer = true), mapOf(
      "hubPages(limit=10000)" to pages, "hubMs" to hubMs, "hub(limit=100000)" to hubMax.first, "hubMaxMs" to hubMaxMs,
      "hubLimit120000" to "$hubOver", "libSelectMs" to selectMs, "libMaterializeMs" to loadMs,
      "libFeaturesPerSecond" to (big * 1000L / maxOf(1, selectMs + loadMs)), "hubFeaturesPerSecond" to (big * 1000L / maxOf(1, hubMs))
    ))
    assertEquals(big, hub.size)
    assertEquals(big, lib.size)
    assertTrue(d.sameKeys)
    row("/iterate >100k", "GET /iterate?limit=10000&handle=… ($pages pages, $hubMs ms)", "one ReadFeatures without limit (default hardCap 16 777 216): select $selectMs ms + materialize $loadMs ms",
      d.result, "hub limit>100000 → ${hubOver.status}; lib has no per-request cap below hardCap")
  }

  @Test
  fun `iterate 120k - lib continuation handles`() {
    val observations = LinkedHashMap<String, Any?>()
    for (pageSize in listOf(10_000, 1_000)) {
      var pagesRead = 0
      var featuresRead = 0
      val result = outcome({ _: Unit -> "pages=$pagesRead features=$featuresRead" }) {
        storage.newReadSession().use { s ->
          var resp = s.executeRead(request("ev-big") { limit = pageSize; returnHandle = true }) as SuccessResponse
          while (true) {
            pagesRead++
            featuresRead += resp.features.size
            val handle = resp.getRaw("handle") as? String ?: break
            resp = s.executeRead(ReadHandle(handle).apply { returnHandle = true }) as SuccessResponse
          }
        }
      }
      observations["limit=$pageSize"] = "$result (pagesRead=$pagesRead featuresRead=$featuresRead)"
    }
    // Where is the limit? Each size in a fresh storage (handles are storage-wide), so earlier snapshots don't count.
    fun probe(n: Int, s: ImlStorage) = outcome({ r: SuccessResponse -> "ok page=${r.features.size} handle=${r.getRaw("handle") != null}" }) {
      s.newReadSession().use { session ->
        session.executeRead(request("ev-big") {
          queryMembers = naksha.model.request.ops.Lt("fn", n.toLong() + 1)
          limit = 100
          returnHandle = true
        }) as SuccessResponse
      }
    }
    val probes = LinkedHashMap<Int, String>()
    for (n in listOf(5_000, 8_000, 9_000, 9_200, 9_400, 9_500, 10_000, 100_001)) {
      val (c, s) = Parity.open("iml-eval-p2-handle-$n")
      try { probes[n] = probe(n, s) } finally { Naksha.removeStorage(c) }
    }
    observations["freshStorageProbes(limit=100, returnHandle)"] = probes
    // Shared budget: two open snapshots of 5 000 candidates each in one storage, then a third.
    val (c, s) = Parity.open("iml-eval-p2-handle-shared")
    try {
      observations["sharedBudget"] = listOf(probe(5_000, s), probe(5_000, s), probe(5_000, s), probe(5_000, s))
    } finally { Naksha.removeStorage(c) }
    observe("iterate-120k-lib-handles", observations)
    row("/iterate handle", "GET /iterate?limit=10000&handle=… (stateless, encrypted i-keyset)", "returnHandle=true + ReadHandle (in-memory snapshot)",
      if (observations.values.any { it.toString().startsWith("NakshaException") || it.toString().contains("Exception") }) "error" else "same", "${observations["limit=10000"]}; probes=$probes")
  }

  @Test
  fun `iterate 120k - keyset paging over fn (workaround)`() {
    val (hubResult, hubMs) = timed { hubIterateAll("ev-big", 10_000) }
    val hubOrder = hubResult.first.map { it["id"] as String }
    val libOrder = ArrayList<String>()
    val lib = ArrayList<Map<*, *>>()
    var pages = 0
    val (_, libMs) = timed {
      storage.newReadSession().use { s ->
        var last = 0L
        while (true) {
          val resp = s.executeRead(request("ev-big") {
            queryMembers = Gt("fn", last)
            orderBy = OrderBy("fn", SortOrder.ASCENDING)
            limit = 10_000
          }) as SuccessResponse
          val tuples = resp.featureTupleList
          if (tuples.isEmpty()) break
          pages++
          val features = toMaps(resp.features.mapNotNull { it })
          lib += features
          libOrder += features.map { it["id"] as String }
          last = tuples.last()!!.tupleNumber.featureNumber
          if (tuples.size < 10_000) break
        }
      }
    }
    val d = record(diff("iterate-120k-keyset", hubResult.first, lib, v1Layer = true), mapOf("hubMs" to hubMs, "libMs" to libMs, "libPages" to pages, "sameOrder" to (hubOrder == libOrder)))
    assertEquals(hubOrder, libOrder)
    row("/iterate (workaround)", "GET /iterate?limit=10000&handle=… ($hubMs ms)", "keyset: Gt(fn, last) + OrderBy(fn) + limit=10000 ($pages pages, $libMs ms)",
      "${d.result}; same order", "stateless like IML; fn = i, the same key IML's handle encodes")
  }

  @Test
  fun `paging with limit on a small layer`() {
    val hubResult = hubIterateAll("ev-hist", 5)
    val hubOrder = hubResult.first.map { it["id"] as String }
    val libOrder = ArrayList<String>()
    val lib = ArrayList<Map<*, *>>()
    storage.newReadSession().use { s ->
      var resp = s.executeRead(request("ev-hist") { limit = 5; returnHandle = true }) as SuccessResponse
      while (true) {
        val page = toMaps(resp.features.mapNotNull { it })
        lib += page
        libOrder += page.map { it["id"] as String }
        val handle = resp.getRaw("handle") as? String ?: break
        resp = s.executeRead(ReadHandle(handle).apply { returnHandle = true }) as SuccessResponse
      }
    }
    val d = record(diff("iterate-small-limit5", hubResult.first, lib), mapOf("hubPages" to hubResult.second, "hubOrder" to hubOrder, "libOrder" to libOrder))
    assertTrue(d.sameKeys)
    row("/iterate limit", "GET /spaces/ev-hist/iterate?limit=5&handle=… (${hubResult.second} pages)", "limit=5 + returnHandle + ReadHandle",
      if (hubOrder == libOrder) "${d.result}; same order" else "${d.result}; order differs", "hub order=${hubOrder.take(6)}… lib order=${libOrder.take(6)}…")
    // Reusing a handle: hub handles are stateless and can be replayed; lib-iml handles are single-use.
    val hubFirst = Parity.hubOk("/spaces/ev-hist/iterate", listOf("limit" to "5"))
    val token = hubFirst.field("nextPageToken") as String
    val replay1 = Parity.hubOk("/spaces/ev-hist/iterate", listOf("limit" to "5", "handle" to token)).features.map { it["id"] }
    val replay2 = Parity.hubOk("/spaces/ev-hist/iterate", listOf("limit" to "5", "handle" to token)).features.map { it["id"] }
    val libReuse = storage.newReadSession().use { s ->
      val first = s.executeRead(request("ev-hist") { limit = 5; returnHandle = true }) as SuccessResponse
      val h = first.getRaw("handle") as String
      s.executeRead(ReadHandle(h)).length
      outcome({ r: naksha.model.request.Response -> "ok ${r.length}" }) { s.executeRead(ReadHandle(h)) }
    }
    observe("iterate-handle-reuse", mapOf("hubReplaySame" to (replay1 == replay2), "libSecondUse" to libReuse))
    row("/iterate handle reuse", "replaying the same handle → same page (${replay1 == replay2})", "second ReadHandle with the same token", "diff", libReuse)
  }

  @Test
  fun `H4 - property search with a lower hardCap is truncated silently`() {
    val (c, s) = Parity.open("iml-eval-p2-hardcap") { hardCap = 10_000 }
    try {
      val prop = outcome({ l: List<Map<*, *>> -> "${l.size} features ${Parity.ids(l)}" }) {
        Parity.libRead(s, "ev-big") { query.properties = PQuery(Property("properties", "n"), DoubleOp.EQ, 119_999) }
      }
      val early = outcome({ l: List<Map<*, *>> -> "${l.size} features ${Parity.ids(l)}" }) {
        Parity.libRead(s, "ev-big") { query.properties = PQuery(Property("properties", "n"), DoubleOp.EQ, 5) }
      }
      val plain = outcome({ r: SuccessResponse -> "${r.featureTupleList.size} tuples, handle=${r.getRaw("handle")}, raw keys=${r.keys}" }) {
        s.newReadSession().use { session -> session.executeRead(request("ev-big")) as SuccessResponse }
      }
      val overLimit = outcome({ r: SuccessResponse -> "${r.featureTupleList.size} tuples" }) {
        s.newReadSession().use { session -> session.executeRead(request("ev-big") { limit = 50_000 }) as SuccessResponse }
      }
      observe("h4-hardcap-truncation", mapOf("hardCap" to 10_000, "property n=119999" to prop, "property n=5" to early, "noLimit" to plain, "limit=50000" to overLimit))
      row("H4 hardCap", "GET /search?p.n=119999 → hub 400 (needs an index) / small layers: SQL, exact", "hardCap=10000 + PropertyFilter", if (prop.startsWith("ok: 0")) "diff (silently empty)" else "same", "n=119999: $prop; n=5: $early; no limit: $plain; limit=50000: $overLimit")
    } finally {
      Naksha.removeStorage(c)
    }
  }
}
