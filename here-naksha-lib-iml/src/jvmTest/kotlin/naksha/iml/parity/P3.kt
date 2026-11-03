package naksha.iml.parity

import naksha.base.Platform
import java.io.File
import java.lang.management.ManagementFactory
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.sql.Connection
import java.sql.Driver
import java.sql.DriverManager
import java.sql.DriverPropertyInfo
import java.time.Duration
import java.util.Properties
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.logging.Logger

/**
 * Phase 3 (edge cases) helpers on top of [Parity]: Hub space/feature management with JSON bodies, direct local JDBC checks,
 * a connection-counting JDBC driver, a heap sampler and the edge-case table.
 *
 * Output: `EDGE|item|case|expected (IML)|observed|severity` lines on stdout, and rows appended to `<IML_PARITY_OUT>/edge-cases.tsv`.
 * Raw observations go through [Parity.observe] / [Parity.record] to `<IML_PARITY_OUT>/<scenario>.json`.
 * Everything is local only: [Parity.assumeEnabled] validates the hub and DB URLs.
 */
object P3 {
  private val outDir: File? = System.getenv("IML_PARITY_OUT")?.takeIf { it.isNotBlank() }?.let { File(it) }
  private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()

  // ---------------------------------------------------------------------------------------------------------------
  // Edge-case table

  /** One row of the report table: case | expected (IML) | observed | severity. */
  fun case(item: String, case: String, expected: String, observed: String, severity: String) {
    val line = listOf(item, case, expected, observed, severity).joinToString("\t") { it.replace("\t", " ").replace("\n", " ") }
    println("EDGE|$line")
    val dir = outDir ?: return
    dir.mkdirs()
    File(dir, "edge-cases.tsv").appendText(line + "\n")
  }

  fun log(test: String, message: String) = println("P3|$test|$message")

  // ---------------------------------------------------------------------------------------------------------------
  // Hub REST with JSON bodies

  fun hub(method: String, path: String, body: String? = null, ctype: String = "application/json", query: List<Pair<String, String>> = emptyList(), timeoutSeconds: Long = 300): Parity.HubResponse {
    val qs = query.joinToString("&") { "${URLEncoder.encode(it.first, Charsets.UTF_8)}=${URLEncoder.encode(it.second, Charsets.UTF_8).replace("+", "%20")}" }
    val uri = URI(Parity.hubUrl!!.trimEnd('/') + path + if (qs.isEmpty()) "" else "?$qs")
    val builder = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(timeoutSeconds)).header("Accept", "application/json, application/geo+json")
    if (body != null) builder.header("Content-Type", ctype).method(method, HttpRequest.BodyPublishers.ofString(body))
    else builder.method(method, HttpRequest.BodyPublishers.noBody())
    val start = System.nanoTime()
    val response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString())
    return Parity.HubResponse(response.statusCode(), response.body(), (System.nanoTime() - start) / 1_000_000)
  }

  fun pathId(id: String): String = URLEncoder.encode(id, Charsets.UTF_8).replace("+", "%20")

  /** Deletes the space if it exists and creates it again. */
  fun recreateSpace(id: String, versionsToKeep: Int, extra: Map<String, Any?> = emptyMap()) {
    hub("DELETE", "/spaces/$id")
    val body = Platform.toJSON(linkedMapOf<String, Any?>("id" to id, "title" to "eval phase 3 $id", "versionsToKeep" to versionsToKeep) + extra)
    val r = hub("POST", "/spaces", body)
    check(r.status == 200) { "create space $id -> ${r.status}: ${r.body.take(300)}" }
  }

  fun deleteSpace(id: String): Parity.HubResponse = hub("DELETE", "/spaces/$id")

  fun point(id: String, lon: Double, lat: Double, props: Map<String, Any?>): Map<String, Any?> =
    linkedMapOf("type" to "Feature", "id" to id, "geometry" to mapOf("type" to "Point", "coordinates" to listOf(lon, lat)), "properties" to props)

  fun putFeatures(space: String, features: List<Map<String, Any?>>, query: List<Pair<String, String>> = emptyList()): Parity.HubResponse {
    val r = hub("PUT", "/spaces/$space/features", Platform.toJSON(mapOf("type" to "FeatureCollection", "features" to features)), "application/geo+json", query)
    check(r.status == 200) { "PUT $space features -> ${r.status}: ${r.body.take(300)}" }
    return r
  }

  fun deleteFeatures(space: String, ids: List<String>): Parity.HubResponse {
    val r = hub("DELETE", "/spaces/$space/features", query = ids.map { "id" to it })
    check(r.status in 200..299) { "DELETE $space features -> ${r.status}: ${r.body.take(300)}" }
    return r
  }

  /** Points `prefix0000…` on a small grid, so every test space has deterministic content. */
  fun grid(prefix: String, count: Int, lon0: Double, lat0: Double, props: (Int) -> Map<String, Any?> = { mapOf("n" to it, "name" to "$prefix $it") }) =
    (0 until count).map { point("$prefix%05d".format(it), lon0 + (it % 100) * 0.001, lat0 + (it / 100) * 0.001, props(it)) }

  // ---------------------------------------------------------------------------------------------------------------
  // Direct local JDBC (checks only, never through lib-iml)

  fun <T> db(block: (Connection) -> T): T = DriverManager.getConnection(Parity.dbUrl!!).use(block)

  fun scalar(sql: String, vararg params: Any?): Any? = db { c ->
    c.prepareStatement(sql).use { st ->
      params.forEachIndexed { i, p -> st.setObject(i + 1, p) }
      st.executeQuery().use { if (it.next()) it.getObject(1) else null }
    }
  }

  fun execute(sql: String, vararg params: Any?): Int = db { c ->
    c.prepareStatement(sql).use { st ->
      params.forEachIndexed { i, p -> st.setObject(i + 1, p) }
      st.executeUpdate()
    }
  }

  fun table(space: String): String? = scalar("SELECT h_id FROM xyz_config.space_meta WHERE id = ?", space) as String?

  // ---------------------------------------------------------------------------------------------------------------
  // Connection counting: replaces the registered PostgreSQL driver with a delegating one for the duration of a block.

  class CountingDriver(private val delegate: Driver) : Driver by delegate {
    val connects = AtomicInteger()
    val connectNanos = AtomicLong()
    override fun connect(url: String?, info: Properties?): Connection? {
      if (!delegate.acceptsURL(url)) return null
      val start = System.nanoTime()
      try {
        return delegate.connect(url, info)
      } finally {
        connects.incrementAndGet()
        connectNanos.addAndGet(System.nanoTime() - start)
      }
    }
    override fun getPropertyInfo(url: String?, info: Properties?): Array<DriverPropertyInfo> = delegate.getPropertyInfo(url, info)
    override fun getParentLogger(): Logger = delegate.parentLogger
  }

  fun <T> countingConnections(block: (CountingDriver) -> T): T {
    val pg = DriverManager.getDriver(Parity.dbUrl!!)
    val counting = CountingDriver(pg)
    DriverManager.deregisterDriver(pg)
    DriverManager.registerDriver(counting)
    try {
      return block(counting)
    } finally {
      DriverManager.deregisterDriver(counting)
      DriverManager.registerDriver(pg)
    }
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Heap sampling

  class HeapSampler(private val periodMs: Long = 50) : AutoCloseable {
    private val bean = ManagementFactory.getMemoryMXBean()
    @Volatile private var running = true
    @Volatile var maxUsedMiB = 0L
      private set
    val baselineMiB: Long
    private val thread: Thread

    init {
      System.gc()
      baselineMiB = bean.heapMemoryUsage.used / (1024 * 1024)
      thread = Thread {
        while (running) {
          val used = bean.heapMemoryUsage.used / (1024 * 1024)
          if (used > maxUsedMiB) maxUsedMiB = used
          Thread.sleep(periodMs)
        }
      }.apply { isDaemon = true; start() }
    }

    override fun close() {
      running = false
      thread.join(1000)
    }

    fun summary() = "heap baseline=${baselineMiB} MiB peak=${maxUsedMiB} MiB (max ${Runtime.getRuntime().maxMemory() / (1024 * 1024)} MiB)"
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Misc

  /** Runs `docker <args>` for the eval stack's own containers (compose project imleval, started in Phase 1) and returns exit code + output. */
  fun docker(vararg args: String): Pair<Int, String> {
    require(args.any { it.startsWith("imleval-") }) { "only the eval stack's containers" }
    val p = ProcessBuilder(listOf("docker") + args).redirectErrorStream(true).start()
    val out = p.inputStream.bufferedReader().readText()
    return p.waitFor() to out.trim()
  }

  fun waitFor(timeoutMs: Long, what: String, probe: () -> Boolean): Long {
    val start = System.currentTimeMillis()
    while (System.currentTimeMillis() - start < timeoutMs) {
      if (runCatching(probe).getOrDefault(false)) return System.currentTimeMillis() - start
      Thread.sleep(250)
    }
    error("timeout waiting for $what")
  }

  fun hubUp(): Boolean = runCatching { hub("GET", "/", timeoutSeconds = 5).status == 200 }.getOrDefault(false)

  fun dbUp(): Boolean = runCatching { scalar("SELECT 1") == 1 }.getOrDefault(false)

  fun describe(e: Throwable): String {
    val code = (e as? naksha.base.NakshaException)?.error?.code
    val sqlState = generateSequence(e) { it.cause }.filterIsInstance<java.sql.SQLException>().firstOrNull()?.sqlState
    return "${e::class.simpleName}${if (code != null) "($code)" else ""}${if (sqlState != null) "[sqlstate $sqlState]" else ""}: ${e.message?.lineSequence()?.firstOrNull()?.take(200)}"
  }
}
