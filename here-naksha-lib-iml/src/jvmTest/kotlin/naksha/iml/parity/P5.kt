package naksha.iml.parity

import naksha.base.NakshaException
import naksha.iml.ImlConfig
import naksha.iml.ImlStorage
import naksha.model.Naksha
import naksha.model.request.ReadFeatures
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException

/**
 * Phase 5 (code review) helpers on top of [Parity] and [P3].
 *
 * Scratch fixtures: a data schema and a metadata schema owned by one test class (`imlp5<tag><rnd>`), holding plain copies
 * (`CREATE TABLE … AS SELECT`) of Hub layer tables and a `space_meta` with only the scratch rows. Locking, dropping or writing
 * a scratch table never touches a Hub-owned object, and the Hub can't see the scratch rows.
 *
 * Output: `REVIEW|finding|case|expected|observed|verdict` lines on stdout, and rows appended to `<IML_PARITY_OUT>/p5-cases.tsv`.
 */
object P5 {
  private val outDir: File? = System.getenv("IML_PARITY_OUT")?.takeIf { it.isNotBlank() }?.let { File(it) }
  val strict: Boolean = System.getenv("IML_PARITY_STRICT") == "true"

  fun rnd(): String = (1..5).map { "abcdefghijklmnopqrstuvwxyz0123456789".random() }.joinToString("")

  /** One row of the review evidence table. */
  fun row(finding: String, case: String, expected: String, observed: String, verdict: String) {
    val line = listOf(finding, case, expected, observed, verdict).joinToString("\t") { it.replace("\t", " ").replace("\n", " ") }
    println("REVIEW|$line")
    val dir = outDir ?: return
    dir.mkdirs()
    File(dir, "p5-cases.tsv").appendText(line + "\n")
  }

  /** A scratch data schema plus metadata schema; [close] drops both. */
  class Scratch(tag: String) : AutoCloseable {
    private val suffix = rnd()
    val data = "imlp5${tag}d$suffix"
    val meta = "imlp5${tag}m$suffix"
    private val configs = ArrayList<ImlConfig>()

    init {
      P3.execute("CREATE SCHEMA \"$data\"")
      P3.execute("CREATE SCHEMA \"$meta\"")
      P3.execute("CREATE TABLE \"$meta\".space_meta (LIKE xyz_config.space_meta INCLUDING ALL)")
    }

    fun sqlTable(table: String) = "\"$data\".\"$table\""

    /** Copies the rows of a Hub layer's table into a plain scratch table and registers it like the Hub would (no `nakshaCollection`). */
    fun addLayer(id: String, table: String, fromHubSpace: String?, metaJson: String = "{}"): String {
      if (fromHubSpace != null) {
        val hubTable = checkNotNull(P3.table(fromHubSpace)) { "no space_meta row for $fromHubSpace" }
        val exists = P3.scalar("SELECT to_regclass(?)", "${sqlTable(table)}") != null
        if (!exists) P3.execute("CREATE TABLE ${sqlTable(table)} AS SELECT * FROM public.\"$hubTable\"")
      }
      P3.execute("INSERT INTO \"$meta\".space_meta (id, schem, h_id, meta) VALUES (?, ?, ?, ?::jsonb)", id, data, table, metaJson)
      return table
    }

    fun open(storageId: String, configure: ImlConfig.() -> Unit = {}): Pair<ImlConfig, ImlStorage> {
      val (config, storage) = Parity.open(storageId) {
        schema = data
        metadataSchema = meta
        configure()
      }
      configs.add(config)
      return config to storage
    }

    fun request(layer: String, configure: ReadFeatures.() -> Unit = {}) = ReadFeatures().apply {
      catalogId = data
      collectionId = layer
      configure()
    }

    override fun close() {
      for (c in configs) runCatching { Naksha.removeStorage(c) }
      runCatching { P3.execute("DROP SCHEMA IF EXISTS \"$data\" CASCADE") }
      runCatching { P3.execute("DROP SCHEMA IF EXISTS \"$meta\" CASCADE") }
    }
  }

  /** Opens a direct connection, takes `ACCESS EXCLUSIVE` on [sqlTable] and keeps the transaction open until the connection is closed. */
  fun lock(sqlTable: String): Connection {
    val c = DriverManager.getConnection(Parity.dbUrl!!)
    c.autoCommit = false
    c.createStatement().use { it.execute("LOCK TABLE $sqlTable IN ACCESS EXCLUSIVE MODE") }
    return c
  }

  fun release(lock: Connection) {
    runCatching { lock.rollback() }
    runCatching { lock.close() }
  }

  /** Backends waiting on a heavyweight lock whose query reads [tableName]: (pid, application_name). */
  fun lockWaiters(tableName: String): List<Pair<Int, String>> = P3.db { c ->
    c.prepareStatement("SELECT pid, application_name FROM pg_stat_activity WHERE wait_event_type = 'Lock' AND query LIKE ? AND pid <> pg_backend_pid()").use { st ->
      st.setString(1, "%$tableName%")
      st.executeQuery().use { rs -> generateSequence { if (rs.next()) rs.getInt(1) to rs.getString(2) else null }.toList() }
    }
  }

  fun terminate(pid: Int): Boolean = P3.scalar("SELECT pg_terminate_backend(?)", pid) == true

  fun waitFor(timeoutMs: Long, probe: () -> Boolean): Long {
    val start = System.currentTimeMillis()
    while (System.currentTimeMillis() - start < timeoutMs) {
      if (runCatching(probe).getOrDefault(false)) return System.currentTimeMillis() - start
      Thread.sleep(50)
    }
    return -1
  }

  /** A thread that records its outcome, elapsed time and whether it is still running at a given moment. */
  class Probe(val name: String, private val block: () -> String) {
    @Volatile var outcome: String = "running"
    @Volatile var elapsedMs: Long = -1
    @Volatile var startedAt: Long = 0
    val thread = Thread({
      naksha.model.NakshaContext.currentContext().withAppId("iml-eval-parity")
      startedAt = System.currentTimeMillis()
      outcome = try { block() } catch (e: Throwable) { P3.describe(e) }
      elapsedMs = System.currentTimeMillis() - startedAt
    }, "p5-$name").apply { isDaemon = true }
    fun start(): Probe { thread.start(); return this }
    fun join(ms: Long = 120_000): Probe { thread.join(ms); return this }
    val running: Boolean get() = thread.isAlive
    fun summary(): Map<String, Any?> = linkedMapOf("outcome" to outcome, "elapsedMs" to elapsedMs)
  }

  /** The exception, its causes and suppressed exceptions, each with SQLState and Naksha error code. */
  fun chain(e: Throwable): List<String> {
    val out = ArrayList<String>()
    val seen = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Throwable, Boolean>())
    fun visit(t: Throwable, role: String) {
      if (!seen.add(t) || out.size > 20) return
      val state = (t as? SQLException)?.sqlState
      val code = (t as? NakshaException)?.error?.code
      out += "$role ${t::class.simpleName}${if (code != null) "($code)" else ""}${if (state != null) "[$state]" else ""}: ${t.message?.lineSequence()?.firstOrNull()?.take(120)}"
      t.cause?.let { visit(it, "$role.cause") }
      t.suppressed.forEachIndexed { i, s -> visit(s, "$role.suppressed[$i]") }
    }
    visit(e, "thrown")
    return out
  }

  fun sqlStates(e: Throwable): Set<String> {
    val out = HashSet<String>()
    val seen = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Throwable, Boolean>())
    fun visit(t: Throwable) {
      if (!seen.add(t)) return
      (t as? SQLException)?.sqlState?.let { out += it }
      t.cause?.let(::visit)
      t.suppressed.forEach(::visit)
    }
    visit(e)
    return out
  }

  /** Compares two GeoJSON coordinate trees: same nesting and list sizes, numbers within [tolerance]. Returns the first difference or null. */
  fun coordinateDiff(a: Any?, b: Any?, tolerance: Double, path: String = "coordinates"): String? = when {
    a is List<*> && b is List<*> -> {
      if (a.size != b.size) "$path: size ${a.size} vs ${b.size} (${Parity_show(a)} vs ${Parity_show(b)})"
      else a.indices.asSequence().mapNotNull { coordinateDiff(a[it], b[it], tolerance, "$path[$it]") }.firstOrNull()
    }
    a is Number && b is Number -> if (Math.abs(a.toDouble() - b.toDouble()) <= tolerance) null else "$path: ${a} vs ${b}"
    a is Map<*, *> && b is Map<*, *> -> {
      if (a["type"] != b["type"]) "$path.type: ${a["type"]} vs ${b["type"]}"
      else if (a["type"] == "GeometryCollection") coordinateDiff(a["geometries"], b["geometries"], tolerance, "$path.geometries")
      else coordinateDiff(a["coordinates"], b["coordinates"], tolerance, "$path")
    }
    a == null && b == null -> null
    else -> "$path: ${Parity_show(a)} vs ${Parity_show(b)}"
  }

  private fun Parity_show(x: Any?): String = naksha.base.Platform.toJSON(x).take(80)
}
