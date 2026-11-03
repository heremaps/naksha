package naksha.iml.parity

import naksha.base.TupleNumber
import naksha.iml.ImlConfig
import naksha.iml.ImlHandles
import naksha.iml.ImlStorage
import naksha.iml.ImlTuple
import naksha.model.Naksha
import naksha.model.SessionOptions
import naksha.model.objects.NakshaFeature
import naksha.model.request.FeatureTuple
import naksha.model.request.ReadHandle
import naksha.model.request.ResultFilter
import naksha.model.request.SuccessResponse
import naksha.model.request.ops.Equals
import naksha.iml.parity.P3.case
import naksha.iml.parity.P3.describe
import naksha.iml.parity.Parity.observe
import naksha.iml.parity.Parity.request
import naksha.iml.parity.Parity.timed
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

/**
 * Phase 3, item "limits: hardCap, the handle TTL (5 min), and the handle caps (100k tuples, 32 MiB). Is the result truncated silently, or does it fail?"
 * Real storages on `ev-big` (120 000), `ev-hist` and `ev-p3-geo`; the TTL and the tuple cap are tested on `ImlHandles` directly with a fake clock
 * (internal class, same module), because the storage wires the production defaults (`ImlStorage.handles = ImlHandles()`).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@org.junit.jupiter.api.Timeout(value = 10, unit = java.util.concurrent.TimeUnit.MINUTES)
class P3LimitsTest {
  private val configs = ArrayList<ImlConfig>()

  @BeforeAll
  fun setUp() = Parity.assumeEnabled()

  @AfterAll
  fun tearDown() {
    for (c in configs) runCatching { Naksha.removeStorage(c) }
  }

  private fun open(id: String, configure: ImlConfig.() -> Unit = {}): ImlStorage = Parity.open(id, configure).let { configs += it.first; it.second }

  private fun resultFilter(block: (FeatureTuple) -> FeatureTuple?) = object : ResultFilter {
    override fun filter(featureTuple: FeatureTuple): FeatureTuple? = block(featureTuple)
  }

  @Test
  fun `hardCap - plain reads, limits and result filters`() {
    val s = open("iml-eval-p3-hardcap") { hardCap = 1_000 }
    val o = LinkedHashMap<String, Any?>()
    s.newReadSession().use { session ->
      val plain = session.executeRead(request("ev-big")) as SuccessResponse
      o["noLimit"] = "${plain.featureTupleList.size} tuples; response keys=${plain.keys.toList()}"
      o["limit=5000"] = "${(session.executeRead(request("ev-big") { limit = 5_000 }) as SuccessResponse).featureTupleList.size} tuples"
      // A selective result filter: only n >= 119000 qualifies, which is beyond the first 1 000 candidates.
      val filtered = session.executeRead(request("ev-big") {
        limit = 10
        resultFilters.add(this@P3LimitsTest.resultFilter { ft -> ft.takeIf { ((ft.feature?.properties?.get("n") as Number?)?.toInt() ?: -1) >= 119_000 } })
      }) as SuccessResponse
      o["resultFilter n>=119000 limit=10"] = "${filtered.features.size} features, keys=${filtered.keys.toList()}"
      o["hub /iterate?limit=1000"] = Parity.hubOk("/spaces/ev-big/iterate", listOf("limit" to "1000")).let { "${it.features.size} features, nextPageToken=${it.field("nextPageToken") != null}" }
      o["hub /iterate?limit=100001"] = Parity.hub("/spaces/ev-big/iterate", listOf("limit" to "100001")).toString()
    }
    observe("limits-hardcap", o)
    case("4 limits", "hardCap=1000: read without limit, limit=5000, and a selective resultFilter on ev-big (120 000)",
      "Hub: limit > 100 000 → ${o["hub /iterate?limit=100001"]}; a full page always carries nextPageToken (${o["hub /iterate?limit=1000"]})",
      "no limit: ${o["noLimit"]}; limit=5000: ${o["limit=5000"]}; filter: ${o["resultFilter n>=119000 limit=10"]}",
      "High: truncation is silent (no handle, no 'incomplete' flag, no error); a filter that matches only beyond hardCap returns 0 features")
  }

  @Test
  fun `JDBC connections per read - plain, filtered and paged`() {
    val s = open("iml-eval-p3-conns")
    val o = LinkedHashMap<String, Any?>()
    P3.countingConnections { driver ->
      fun count(name: String, build: naksha.model.request.ReadFeatures.() -> Unit) {
        Naksha.cache.clear(s)
        val before = driver.connects.get()
        val (outcome, ms) = timed {
          try { s.newReadSession().use { session -> "ok ${(session.executeRead(request("ev-big", build)) as SuccessResponse).features.count { it != null }}" } } catch (e: Throwable) { describe(e) }
        }
        o[name] = "$outcome, ${driver.connects.get() - before} connections, $ms ms"
      }
      count("plain full read (120 000)") {}
      count("property filter n=119999 (120 000 candidates)") { query.properties = naksha.model.request.query.PQuery(naksha.model.request.query.Property("properties", "n"), naksha.model.request.query.DoubleOp.EQ, 119_999) }
      count("returnHandle limit=100 on 9 000 candidates") { queryMembers = naksha.model.request.ops.Lt("fn", 9_001L); limit = 100; returnHandle = true }
    }
    observe("limits-connections-per-read", o)
    case("4 limits", "JDBC connections per read on ev-big (counting driver)",
      "Hub: pooled connections, none opened per request",
      o.entries.joinToString("; ") { "${it.key}: ${it.value}" },
      "Medium: filtered and paged reads open one connection per 128-candidate batch (ImlReadSession.resultResponse → loadExact → withConnection)")
  }

  @Test
  fun `handle TTL - fake clock on ImlHandles`() {
    var now = 1_000_000L
    val handles = ImlHandles(clock = { now })
    val options = SessionOptions(appId = "iml-eval-parity")
    val tuples = (1..3L).map { ImlTuple.encode(NakshaFeature("t$it"), TupleNumber(1, 1, 1, it, 4)) }
    val o = LinkedHashMap<String, Any?>()
    val first = handles.create(tuples, 1, options)
    val h1 = first.getRaw("handle") as String
    now += 299_999
    val second = handles.read(ReadHandle(h1).apply { returnHandle = true }, options, 1_000_000)
    o["page 2 at 299.999 s"] = "ok ${second.length} feature(s), new handle=${second.getRaw("handle") != null}"
    val h2 = second.getRaw("handle") as String
    now += 2
    o["page 3 at 300.001 s (continuation 2 ms after page 2)"] = try { "ok ${handles.read(ReadHandle(h2), options, 1_000_000).length}" } catch (e: Throwable) { describe(e) }
    o["retainedAfterExpiry"] = handles.retainedSnapshots
    observe("limits-handle-ttl", o)
    case("4 limits", "handle TTL: continue at 299.999 s, then 2 ms later (ImlHandles with a fake clock)",
      "Hub: the iterate handle is stateless (encrypted i keyset) and never expires",
      "page 2: ${o["page 2 at 299.999 s"]}; page 3: ${o["page 3 at 300.001 s (continuation 2 ms after page 2)"]}",
      "High for full reads: the 5 min TTL counts from the first page and is not extended, so a slow consumer of a large layer fails mid-way")
    assertTrue((o["page 3 at 300.001 s (continuation 2 ms after page 2)"] as String).contains("expired"))
  }

  @Test
  fun `handle caps - which limit binds first`() {
    val o = LinkedHashMap<String, Any?>()
    val options = SessionOptions(appId = "iml-eval-parity")
    // Minimal tuples: the per-tuple charge is 4 * body bytes + 512 (ImlHandles.addBytes).
    val tiny = ImlTuple.encode(NakshaFeature("x"), TupleNumber(1, 1, 1, 1, 4))
    o["tinyTupleBodyBytes"] = tiny.featureBytes.size
    val perTuple = tiny.featureBytes.size * 4L + 512
    o["chargePerTinyTuple"] = perTuple
    o["maxTuplesPerSnapshot(16 MiB)"] = (16L * 1024 * 1024) / perTuple
    o["maxTuplesStorage(32 MiB)"] = (32L * 1024 * 1024) / perTuple
    val handles = ImlHandles()
    var retainedTuples = 0
    var snapshots = 0
    var failure = "none"
    val size = ((16L * 1024 * 1024) / perTuple).toInt()
    val batch = List(size) { tiny }
    while (snapshots < 10) {
      try {
        handles.create(batch, 1, options)
        snapshots++
        retainedTuples += size
      } catch (e: Throwable) {
        failure = describe(e); break
      }
    }
    o["snapshotsOfMaxSize"] = snapshots
    o["retainedTuplesAtFailure"] = retainedTuples
    o["failure"] = failure
    // 128-snapshot cap with tiny snapshots.
    val small = ImlHandles()
    var count = 0
    var smallFailure = "none"
    while (count < 200) {
      try { small.create(listOf(tiny, tiny), 1, options); count++ } catch (e: Throwable) { smallFailure = describe(e); break }
    }
    o["smallSnapshotsUntilFailure"] = "$count then $smallFailure"
    observe("limits-handle-caps", o)
    case("4 limits", "handle caps: 100 000 tuples vs 32 MiB vs 16 MiB per snapshot vs 128 snapshots",
      "Hub: no server-side state, so no caps; limit max 100 000 and a 10 MiB response cap, both explicit errors (400/513)",
      "each tuple costs ≥ $perTuple accounted bytes, so a snapshot holds ≤ ${o["maxTuplesPerSnapshot(16 MiB)"]} and the storage ≤ ${o["maxTuplesStorage(32 MiB)"]} tuples; " +
        "$snapshots full snapshots fit ($retainedTuples tuples), then $failure; small snapshots: ${o["smallSnapshotsUntilFailure"]}",
      "Medium: the 100 000-tuple cap can never bind (the byte cap is hit first at ≤ ${o["maxTuplesStorage(32 MiB)"]}); caps fail explicitly, not silently")
  }

  @Test
  fun `handle caps on a real storage - shared budget and silent drop`() {
    val o = LinkedHashMap<String, Any?>()
    val s = open("iml-eval-p3-handles")
    // 128 open snapshots on a small layer exhaust the storage for every app.
    val tokens = ArrayList<String>()
    var exhausted = "none"
    s.newReadSession().use { session ->
      while (tokens.size < 200) {
        try {
          val r = session.executeRead(request("ev-p2") { limit = 1; returnHandle = true }) as SuccessResponse
          tokens += r.getRaw("handle") as String
        } catch (e: Throwable) { exhausted = describe(e); break }
      }
    }
    o["openHandlesUntilFailure"] = "${tokens.size} then $exhausted"
    // ev-hist holds one 2.5 MB feature (Phase 1 seed, TOAST test): how many concurrent handles fit there?
    val s2 = open("iml-eval-p3-handles-hist")
    var histHandles = 0
    var histFailure = "none"
    s2.newReadSession().use { session ->
      while (histHandles < 200) {
        try { session.executeRead(request("ev-hist") { limit = 1; returnHandle = true }); histHandles++ } catch (e: Throwable) { histFailure = describe(e); break }
      }
    }
    o["ev-hist (one 2.5 MB feature) handlesUntilFailure"] = "$histHandles then $histFailure"
    o["otherAppSameStorage"] = try {
      s.newReadSession(SessionOptions(appId = "another-app")).use { session -> "ok ${(session.executeRead(request("ev-p2") { limit = 1; returnHandle = true }) as SuccessResponse).length}" }
    } catch (e: Throwable) { describe(e) }
    o["otherAppWithoutHandle"] = try {
      s.newReadSession(SessionOptions(appId = "another-app")).use { session -> "ok ${(session.executeRead(request("ev-p2") { limit = 1 }) as SuccessResponse).length}" }
    } catch (e: Throwable) { describe(e) }
    // Continuation without returnHandle drops the rest without any marker.
    s.newReadSession().use { session ->
      val r = session.executeRead(ReadHandle(tokens.first())) as SuccessResponse
      o["continuationWithoutReturnHandle"] = "${r.length} feature(s), handle=${r.getRaw("handle")}, keys=${r.keys.toList()}"
    }
    // A single large feature (500 000-vertex line) in the candidate set.
    o["layerWithOneHugeFeature(limit=1)"] = try {
      s.newReadSession().use { session -> "ok ${(session.executeRead(request("ev-p3-geo") { limit = 1; returnHandle = true }) as SuccessResponse).length}" }
    } catch (e: Throwable) { describe(e) }
    o["hubIterateSameLayer(limit=1)"] = Parity.hub("/spaces/ev-p3-geo/iterate", listOf("limit" to "1")).let { "${it.status} handle=${it.field("nextPageToken") != null}" }
    // Cost of reaching the 16 MiB builder cap: everything is materialized before the error.
    Naksha.cache.clear(s)
    val (outcome, ms) = timed { try { s.newReadSession().use { session -> "ok ${(session.executeRead(request("ev-big") { limit = 10; returnHandle = true }) as SuccessResponse).length}" } } catch (e: Throwable) { describe(e) } }
    o["ev-big limit=10 returnHandle"] = "$outcome after $ms ms"
    observe("limits-handle-storage", o)
    case("4 limits", "open handles from one app on one storage (ev-p2, 9 small features), then another app",
      "Hub: unlimited stateless handles",
      "${o["openHandlesUntilFailure"]}; other app with handle: ${o["otherAppSameStorage"]}; other app without handle: ${o["otherAppWithoutHandle"]}; on ev-hist (one 2.5 MB feature): ${o["ev-hist (one 2.5 MB feature) handlesUntilFailure"]}",
      "High (multi-tenant): any client can block paging for all clients of the storage for 5 min")
    case("4 limits", "ReadHandle without returnHandle=true",
      "Hub: every full page returns the next handle",
      "${o["continuationWithoutReturnHandle"]}",
      "Medium: the rest of the snapshot is dropped silently (documented, but an easy trap)")
    case("4 limits", "returnHandle on ev-p3-geo with one 500 000-vertex line, limit=1",
      "Hub: ${o["hubIterateSameLayer(limit=1)"]}",
      "${o["layerWithOneHugeFeature(limit=1)"]}; on ev-big (limit=10): ${o["ev-big limit=10 returnHandle"]}",
      "OK for one 16 MB line (TWKB keeps it under the 16 MiB builder cap); the 120 000 small points fail after full materialization of ~9 300 candidates")
  }
}
