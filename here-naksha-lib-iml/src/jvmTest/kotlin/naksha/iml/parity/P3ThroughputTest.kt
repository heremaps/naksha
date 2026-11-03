package naksha.iml.parity

import naksha.geo.SpBoundingBox
import naksha.iml.ImlConfig
import naksha.iml.ImlStorage
import naksha.model.Naksha
import naksha.model.objects.XyzMembers
import naksha.model.request.SuccessResponse
import naksha.model.request.ops.Intersects
import naksha.iml.parity.P3.case
import naksha.iml.parity.Parity.observe
import naksha.iml.parity.Parity.request
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Phase 3, item "throughput: parallel readers on one storage (are reads serialized?) and on several storages" (hypothesis H6).
 * Workload: 64 distinct 10°×5° bbox reads on `ev-1m` (about 870 points each), fully materialized; caches cleared before each run.
 * Compared with the Hub's `/bbox` at the same parallelism. Gated by `IML_P3_LARGE=true` (needs `ev-1m`).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@org.junit.jupiter.api.Timeout(value = 30, unit = java.util.concurrent.TimeUnit.MINUTES)
class P3ThroughputTest {
  private val space = "ev-1m"
  private val configs = ArrayList<ImlConfig>()
  private val boxes = (0 until 64).map { k -> val w = -170.0 + (k % 16) * 20.0; val s = -70.0 + (k / 16) * 30.0; listOf(w, s, w + 10.0, s + 5.0) }

  @BeforeAll
  fun setUp() {
    Parity.assumeEnabled()
    assumeTrue(System.getenv("IML_P3_LARGE") == "true", "Set IML_P3_LARGE=true")
  }

  @AfterAll
  fun tearDown() {
    for (c in configs) runCatching { Naksha.removeStorage(c) }
  }

  private fun storage(id: String): ImlStorage = Parity.open(id).let { configs += it.first; it.second }

  // Worker threads have no NakshaContext appId, so options are passed explicitly.
  private fun libRead(s: ImlStorage, box: List<Double>): Int = s.newReadSession(naksha.model.SessionOptions(appId = "iml-eval-parity")).use { session ->
    (session.executeRead(request(space) { queryMembers = Intersects(XyzMembers.XyzGeometry, SpBoundingBox(box[0], box[1], box[2], box[3]).toPolygon()) }) as SuccessResponse)
      .features.count { it != null }
  }

  private fun hubRead(box: List<Double>): Int =
    Parity.hubOk("/spaces/$space/bbox", listOf("west" to "${box[0]}", "south" to "${box[1]}", "east" to "${box[2]}", "north" to "${box[3]}", "limit" to "100000")).features.size

  /** Runs all boxes with `threads` workers; `pick(worker)` returns the read function for that worker. */
  private fun run(threads: Int, pick: (Int) -> (List<Double>) -> Int): Map<String, Any?> {
    val queue = ConcurrentLinkedQueue(boxes)
    val features = AtomicLong()
    val pool = Executors.newFixedThreadPool(threads)
    val latencies = ConcurrentLinkedQueue<Long>()
    val start = System.nanoTime()
    repeat(threads) { w ->
      val read = pick(w)
      pool.submit {
        naksha.model.NakshaContext.currentContext().withAppId("iml-eval-parity")
        while (true) {
          val box = queue.poll() ?: break
          val t0 = System.nanoTime()
          features.addAndGet(read(box).toLong())
          latencies.add((System.nanoTime() - t0) / 1_000_000)
        }
      }
    }
    pool.shutdown()
    check(pool.awaitTermination(30, TimeUnit.MINUTES))
    val ms = (System.nanoTime() - start) / 1_000_000
    val sorted = latencies.sorted()
    return linkedMapOf("threads" to threads, "wallMs" to ms, "features" to features.get(), "featuresPerSecond" to features.get() * 1000 / maxOf(1, ms),
      "latencyMedianMs" to sorted[sorted.size / 2], "latencyMaxMs" to sorted.last())
  }

  /**
   * Why don't several storages scale? Runs the 8-storage workload and samples the worker threads (ThreadMXBean, every 100 ms):
   * thread state, the monitor/lock a thread waits for and its owner, and the top naksha.* / org.postgresql.* frame.
   */
  @Test
  fun `diagnose serialization across storages`() {
    val stores = (0 until 8).map { storage("iml-eval-p3-diag-$it") }
    val bean = java.lang.management.ManagementFactory.getThreadMXBean()
    val states = java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.atomic.AtomicInteger>()
    val frames = java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.atomic.AtomicInteger>()
    val locks = java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.atomic.AtomicInteger>()
    val workers = java.util.concurrent.ConcurrentHashMap.newKeySet<Long>()
    val sampling = java.util.concurrent.atomic.AtomicBoolean(true)
    val sampler = Thread {
      while (sampling.get()) {
        val ids = workers.toLongArray()
        if (ids.isNotEmpty()) for (info in bean.getThreadInfo(ids, true, true)) {
          if (info == null) continue
          states.computeIfAbsent(info.threadState.name) { java.util.concurrent.atomic.AtomicInteger() }.incrementAndGet()
          val top = info.stackTrace.firstOrNull { it.className.startsWith("naksha.") || it.className.startsWith("org.postgresql.") }
          val key = "${info.threadState} ${top?.className?.substringAfterLast('.')}.${top?.methodName}:${top?.lineNumber}"
          frames.computeIfAbsent(key) { java.util.concurrent.atomic.AtomicInteger() }.incrementAndGet()
          if (info.lockName != null) locks.computeIfAbsent("${info.lockName} owner=${info.lockOwnerName?.take(30)}".replace(Regex("@[0-9a-f]+"), "")) { java.util.concurrent.atomic.AtomicInteger() }.incrementAndGet()
        }
        Thread.sleep(100)
      }
    }.apply { isDaemon = true; start() }
    val gcBeans = java.lang.management.ManagementFactory.getGarbageCollectorMXBeans()
    val cpu = java.util.concurrent.ConcurrentHashMap<Long, Long>()
    fun measured(threads: Int): Map<String, Any?> {
      stores.forEach { Naksha.cache.clear(it) }
      cpu.clear()
      val gc0 = gcBeans.sumOf { it.collectionTime }
      val gcn0 = gcBeans.sumOf { it.collectionCount }
      val res = run(threads) { w -> { box ->
        val id = Thread.currentThread().id
        workers.add(id)
        val c0 = bean.getThreadCpuTime(id)
        val n = libRead(stores[w], box)
        cpu.merge(id, bean.getThreadCpuTime(id) - c0, Long::plus)
        n
      } }
      return res + mapOf("workerCpuMs" to cpu.values.sum() / 1_000_000, "gcMs" to gcBeans.sumOf { it.collectionTime } - gc0, "gcCount" to gcBeans.sumOf { it.collectionCount } - gcn0)
    }
    val x1 = measured(1)
    val r = measured(8)
    sampling.set(false)
    sampler.join(2_000)
    val top = { m: Map<String, java.util.concurrent.atomic.AtomicInteger> -> m.entries.sortedByDescending { it.value.get() }.take(8).associate { it.key to it.value.get() } }
    val o = linkedMapOf("x1 (1 storage)" to x1, "x8 (8 storages)" to r, "threadStates" to top(states), "topFrames" to top(frames), "waitedLocks" to top(locks),
      "dbActiveBackendsDuringRun(sample)" to "see frames", "cpus" to Runtime.getRuntime().availableProcessors())
    observe("throughput-diagnose", o)
    case("9 throughput", "where 8 threads on 8 storages wait (thread sampling every 100 ms)",
      "Hub: n/a",
      "states ${o["threadStates"]}; top frames ${o["topFrames"]}; locks ${o["waitedLocks"]}",
      "see §3.9")
  }

  @Test
  fun `parallel readers - one storage, several storages, Hub`() {
    val o = LinkedHashMap<String, Any?>()
    val shared = storage("iml-eval-p3-tp-shared")
    val perThread = (0 until 8).map { storage("iml-eval-p3-tp-$it") }
    // Warm-up: JIT, DB buffers.
    run(4) { { box -> libRead(shared, box) } }
    run(4) { { box -> hubRead(box) } }
    for (threads in listOf(1, 2, 4, 8)) {
      Naksha.cache.clear(shared)
      o["lib one storage x$threads"] = run(threads) { { box -> libRead(shared, box) } }
      perThread.forEach { Naksha.cache.clear(it) }
      o["lib $threads storages x$threads"] = run(threads) { w -> { box -> libRead(perThread[w], box) } }
      o["hub x$threads"] = run(threads) { { box -> hubRead(box) } }
    }
    // Connections opened per read.
    val connections = P3.countingConnections { driver ->
      Naksha.cache.clear(shared)
      val before = driver.connects.get()
      shared.newReadSession().use { s ->
        val afterSession = driver.connects.get()
        val r = s.executeRead(request(space) { queryMembers = Intersects(XyzMembers.XyzGeometry, SpBoundingBox(10.0, 10.0, 20.0, 15.0).toPolygon()) }) as SuccessResponse
        val afterSelect = driver.connects.get()
        r.features.size
        val afterLoad = driver.connects.get()
        mapOf("newReadSession" to afterSession - before, "selection" to afterSelect - afterSession, "materialization" to afterLoad - afterSelect,
          "avgConnectMs" to driver.connectNanos.get() / maxOf(1, driver.connects.get()) / 1_000_000.0)
      }
    }
    o["connectionsPerRead"] = connections
    observe("throughput-parallel", o)
    fun fps(k: String) = (o[k] as Map<*, *>)["featuresPerSecond"]
    fun wall(k: String) = (o[k] as Map<*, *>)["wallMs"]
    case("9 throughput", "64 bbox reads (~870 features each) with 1/2/4/8 threads on ONE storage",
      "Hub: x1 ${fps("hub x1")} f/s, x2 ${fps("hub x2")}, x4 ${fps("hub x4")}, x8 ${fps("hub x8")}",
      "lib: x1 ${fps("lib one storage x1")} f/s (${wall("lib one storage x1")} ms), x2 ${fps("lib one storage x2")}, x4 ${fps("lib one storage x4")}, x8 ${fps("lib one storage x8")} (${wall("lib one storage x8")} ms)",
      "High: H6 confirmed if flat; reads on one storage hold a single permit (ImlStorage.kt connectionPermit) for selection and for materialization")
    case("9 throughput", "the same with one storage per thread",
      "Hub: as above",
      "lib: x1 ${fps("lib 1 storages x1")} f/s, x2 ${fps("lib 2 storages x2")}, x4 ${fps("lib 4 storages x4")}, x8 ${fps("lib 8 storages x8")}",
      "workaround: N storages scale, but each has its own tuple cache, handles and discovery snapshot")
    case("9 throughput", "JDBC connections opened per read (counting driver)",
      "Hub: pooled connections (dbMaxPoolSize), none per request",
      "$connections",
      "Medium: 3 connects per session+read; locally ~ms, remotely a TLS handshake each (inferred)")
  }
}
