package naksha.iml.parity

import naksha.base.Platform
import naksha.base.Version
import naksha.iml.ImlConfig
import naksha.iml.ImlStorage
import naksha.iml.ImlTestFixture
import naksha.model.Naksha
import naksha.model.NakshaContext
import naksha.model.objects.NakshaFeature
import naksha.model.request.ReadFeatures
import naksha.model.request.SuccessResponse
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.io.File
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Phase 2 parity harness: query the local XYZ Hub REST API and lib-iml on the same database, normalize both results and diff them.
 *
 * Gated by `IML_PARITY_HUB_URL` and `NAKSHA_TEST_IML_DB_URL` (both loopback only, `NAKSHA_TEST_IML_REMOTE_DB_URL` must be unset).
 * Expects the seeds `iml-naksha-eval/scripts/seed-all.sh` and `scripts/seed-p2.py`.
 * If `IML_PARITY_OUT` names a directory, raw diffs are written there as `<scenario>.json` and matrix rows are appended to `matrix-rows.tsv`.
 *
 * Normalization (compared fields, as in the evaluation plan): `id`, geometry (1e-9 tolerance), user `properties` (without `@ns:com:here:xyz`),
 * and the namespace fields `createdAt`, `updatedAt`, `version`, `author`, `tags`, plus a derived `deleted` flag.
 * lib-iml versions are packed `(v << 2) | action` (finding F3), so they are unpacked with `ushr 2` before comparing.
 * Fields lib-iml adds (`uuid`, `action`, `nextVersion`) are not compared.
 */
object Parity {
  val hubUrl: String? = System.getenv("IML_PARITY_HUB_URL")
  val dbUrl: String? = System.getenv("NAKSHA_TEST_IML_DB_URL")
  private val outDir: File? = System.getenv("IML_PARITY_OUT")?.takeIf { it.isNotBlank() }?.let { File(it) }
  private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
  private val XYZ = "@ns:com:here:xyz"

  fun assumeEnabled() {
    assumeTrue(!hubUrl.isNullOrBlank() && !dbUrl.isNullOrBlank(), "Set IML_PARITY_HUB_URL and NAKSHA_TEST_IML_DB_URL")
    require(URI(hubUrl!!).host in setOf("localhost", "127.0.0.1")) { "IML_PARITY_HUB_URL must be local" }
    ImlTestFixture.validateLocalUrl(dbUrl!!)
    require(System.getenv("NAKSHA_TEST_IML_REMOTE_DB_URL").isNullOrBlank()) { "Remote DB URL must not be set" }
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Storage

  fun open(id: String, configure: ImlConfig.() -> Unit = {}): Pair<ImlConfig, ImlStorage> {
    NakshaContext.currentContext().withAppId("iml-eval-parity")
    val config = ImlConfig(id, dbUrl!!).apply(configure)
    return config to (Naksha.useStorage(config) as ImlStorage)
  }

  fun request(space: String, configure: ReadFeatures.() -> Unit = {}) = ReadFeatures().apply {
    catalogId = "public"
    collectionId = space
    configure()
  }

  /** Executes one read and returns the materialized features as plain JSON maps (as a client would serialize them). */
  fun libRead(storage: ImlStorage, space: String, configure: ReadFeatures.() -> Unit = {}): List<Map<*, *>> =
    storage.newReadSession().use { session ->
      toMaps((session.executeRead(request(space, configure)) as SuccessResponse).features.mapNotNull { it })
    }

  fun toMaps(features: List<NakshaFeature>): List<Map<*, *>> = features.map { Platform.fromJSON(Platform.toJSON(it)) as Map<*, *> }

  // ---------------------------------------------------------------------------------------------------------------
  // Hub REST

  class HubResponse(val status: Int, val body: String, val millis: Long) {
    // Platform.fromJSON drops integers beyond Long (BigIntegerNode); lib-iml widens them with ImlTuple.jsonNumbers, so the hub side must too.
    val json: Any? by lazy { runCatching { Platform.fromJSON(naksha.iml.ImlTuple.jsonNumbers(body)) }.getOrNull() }
    val features: List<Map<*, *>> get() = ((json as? Map<*, *>)?.get("features") as List<*>? ?: emptyList<Any>()).map { it as Map<*, *> }
    val error: String? get() = (json as? Map<*, *>)?.let { m -> m["error"]?.let { "$it: ${m["errorMessage"]}" } }
    fun field(name: String): Any? = (json as? Map<*, *>)?.get(name)
    override fun toString() = "$status ${error ?: "n=${features.size}"}"
  }

  /** `query` pairs are URL-encoded; `raw` is appended verbatim (for parameters the hub must see unencoded, e.g. `p.rank!=5`). */
  fun hub(path: String, query: List<Pair<String, String>> = emptyList(), raw: String? = null, method: String = "GET", body: String? = null): HubResponse {
    val encoded = query.joinToString("&") { "${URLEncoder.encode(it.first, Charsets.UTF_8)}=${URLEncoder.encode(it.second, Charsets.UTF_8).replace("+", "%20")}" }
    val qs = listOfNotNull(encoded.takeIf { it.isNotEmpty() }, raw).joinToString("&")
    val uri = URI(hubUrl!!.trimEnd('/') + path + if (qs.isEmpty()) "" else "?$qs")
    val builder = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(120)).header("Accept", "application/json")
    if (body != null) builder.header("Content-Type", "application/geo+json").method(method, HttpRequest.BodyPublishers.ofString(body))
    else builder.method(method, HttpRequest.BodyPublishers.noBody())
    val start = System.nanoTime()
    val response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString())
    return HubResponse(response.statusCode(), response.body(), (System.nanoTime() - start) / 1_000_000)
  }

  fun hubOk(path: String, query: List<Pair<String, String>> = emptyList(), raw: String? = null, method: String = "GET", body: String? = null): HubResponse {
    val r = hub(path, query, raw, method, body)
    check(r.status == 200) { "hub $method $path ?${query} $raw -> ${r.status}: ${r.body.take(300)}" }
    return r
  }

  /** Follows `nextPageToken` until the hub stops returning one. */
  fun hubIterateAll(space: String, limit: Int, extra: List<Pair<String, String>> = emptyList()): Pair<List<Map<*, *>>, Int> {
    val all = ArrayList<Map<*, *>>()
    var token: String? = null
    var pages = 0
    do {
      val q = ArrayList(extra).apply { add("limit" to "$limit"); if (token != null) add("handle" to token!!) }
      val r = hubOk("/spaces/$space/iterate", q)
      all += r.features
      pages++
      token = r.field("nextPageToken") as String?
    } while (token != null && pages < 10_000)
    return all to pages
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Normalization and diff

  data class Norm(val key: String, val id: String, val geometry: Any?, val properties: Map<*, *>, val xyz: Map<String, Any?>)

  private fun xyzOf(f: Map<*, *>): Map<*, *> = ((f["properties"] as Map<*, *>?)?.get(XYZ) as Map<*, *>?) ?: emptyMap<Any, Any>()

  fun hubVersion(f: Map<*, *>): Long? = (xyzOf(f)["version"] as Number?)?.toLong()

  /** Unpacks lib-iml's `(v << 2) | action`; HEAD and missing stay null. */
  fun libVersion(f: Map<*, *>): Long? {
    val v = (xyzOf(f)["version"] as Number?)?.toLong() ?: return null
    return if (v == Version.HEAD.number) null else v ushr 2
  }

  fun normalizeHub(f: Map<*, *>, keyed: Boolean = false): Norm = normalize(f, hubVersion(f), xyzOf(f)["deleted"] == true, keyed)

  /** lib-iml marks deletions with action DELETE; hub hide rows (H/J) keep the hub's `deleted: true` from jsondata but get action VERSION. */
  fun normalizeLib(f: Map<*, *>, keyed: Boolean = false): Norm =
    normalize(f, libVersion(f), xyzOf(f)["action"] == "DELETE" || xyzOf(f)["deleted"] == true, keyed)

  private fun normalize(f: Map<*, *>, version: Long?, deleted: Boolean, keyed: Boolean): Norm {
    val id = f["id"] as String
    val props = LinkedHashMap((f["properties"] as Map<*, *>?) ?: emptyMap<Any, Any>())
    props.remove(XYZ)
    val ns = xyzOf(f)
    val xyz = linkedMapOf<String, Any?>(
      "createdAt" to ns["createdAt"], "updatedAt" to ns["updatedAt"], "version" to version, "author" to ns["author"],
      "tags" to (ns["tags"] as List<*>?)?.map { it.toString() }?.sorted(), "deleted" to deleted
    )
    return Norm(if (keyed) "$id@$version" else id, id, f["geometry"], props, xyz)
  }

  data class FieldDiff(val key: String, val path: String, val hub: String, val lib: String, val category: String)

  class Diff(val scenario: String, val hubKeys: List<String>, val libKeys: List<String>, val onlyHub: List<String>, val onlyLib: List<String>, val fields: List<FieldDiff>) {
    val categories: Map<String, Int> get() = fields.groupingBy { it.category }.eachCount()
    val sameKeys get() = onlyHub.isEmpty() && onlyLib.isEmpty()
    /** `same`, `same except <known categories>`, or `diff`. Known: F1 geometry, F5 author on v1 layers. */
    val result: String
      get() {
        if (!sameKeys) return "diff"
        val unknown = categories.keys - KNOWN
        return when {
          fields.isEmpty() -> "same"
          unknown.isEmpty() -> "same except known " + categories.keys.sorted().joinToString("+")
          else -> "diff"
        }
      }
    fun summary() = "result=$result hub=${hubKeys.size} lib=${libKeys.size} onlyHub=${onlyHub.take(10)} onlyLib=${onlyLib.take(10)} fieldDiffs=$categories"
  }

  val KNOWN = setOf("geometry-F1", "xyz.author-F5")

  /**
   * Order-insensitive diff by id (or by id@version when `keyed`), comparing the normalized fields.
   * `v1Layer` marks a `versionsToKeep=1` layer, where an author difference (hub null, lib from the column) is the known finding F5.
   */
  fun diff(scenario: String, hub: List<Map<*, *>>, lib: List<Map<*, *>>, keyed: Boolean = false, v1Layer: Boolean = false, compareGeometry: Boolean = true): Diff {
    val h = hub.map { normalizeHub(it, keyed) }.associateBy { it.key }
    val l = lib.map { normalizeLib(it, keyed) }.associateBy { it.key }
    val fields = ArrayList<FieldDiff>()
    for (key in h.keys intersect l.keys) {
      val a = h.getValue(key)
      val b = l.getValue(key)
      if (compareGeometry) walk(key, "geometry", a.geometry, b.geometry, fields)
      walk(key, "properties", a.properties, b.properties, fields)
      for (k in a.xyz.keys) {
        val av = a.xyz[k]
        val bv = b.xyz[k]
        if (!same(av, bv)) {
          val cat = if (k == "author" && v1Layer && av == null) "xyz.author-F5" else "xyz.$k"
          fields += FieldDiff(key, "xyz.$k", show(av), show(bv), cat)
        }
      }
    }
    return Diff(scenario, h.keys.sorted(), l.keys.sorted(), (h.keys - l.keys).sorted(), (l.keys - h.keys).sorted(), fields)
  }

  private fun same(a: Any?, b: Any?): Boolean = when {
    a is Number && b is Number -> Math.abs(a.toDouble() - b.toDouble()) <= 1e-9
    else -> a == b
  }

  private fun integral(x: Any?) = x is Number && x.toDouble() == Math.rint(x.toDouble())

  private fun walk(key: String, path: String, a: Any?, b: Any?, out: MutableList<FieldDiff>) {
    val geometry = path.startsWith("geometry")
    when {
      a is Map<*, *> && b is Map<*, *> -> for (k in (a.keys + b.keys)) {
        // A key present on one side only, with a null value on the other: lib-iml drops explicit JSON nulls.
        if (a.containsKey(k) != b.containsKey(k) && (a[k] == null && b[k] == null)) {
          out += FieldDiff(key, "$path.$k", if (a.containsKey(k)) "null" else "<absent>", if (b.containsKey(k)) "null" else "<absent>", "properties-null-dropped")
        } else walk(key, "$path.$k", a[k], b[k], out)
      }
      a is List<*> && b is List<*> && a.size == b.size -> a.indices.forEach { walk(key, "$path[$it]", a[it], b[it], out) }
      a is List<*> && b is List<*> && geometry -> {
        // A shorter lib coordinate list where the hub has a whole-number Z: lib-geo dropped it (finding F1).
        val f1 = b.size < a.size && a.drop(b.size).all { integral(it) }
        out += FieldDiff(key, path, show(a), show(b), if (f1) "geometry-F1" else "geometry")
        if (f1) b.indices.forEach { walk(key, "$path[$it]", a[it], b[it], out) }
      }
      a is Number && b is Number -> if (Math.abs(a.toDouble() - b.toDouble()) > 1e-9) {
        val f1 = geometry && integral(a) && b.toDouble() == 0.0
        out += FieldDiff(key, path, show(a), show(b), if (f1) "geometry-F1" else if (geometry) "geometry" else "properties")
      }
      a != b -> out += FieldDiff(key, path, show(a), show(b), if (geometry) "geometry" else "properties")
    }
  }

  private fun show(x: Any?): String = Platform.toJSON(x).take(160)

  // ---------------------------------------------------------------------------------------------------------------
  // Output

  /** Writes the raw diff (and optional extra observations) to `<IML_PARITY_OUT>/<scenario>.json` and prints a summary line. */
  fun record(diff: Diff, extra: Map<String, Any?> = emptyMap()): Diff {
    report(diff.scenario, diff.summary() + if (extra.isEmpty()) "" else " extra=${show(extra)}")
    val dir = outDir ?: return diff
    dir.mkdirs()
    val json = linkedMapOf(
      "scenario" to diff.scenario, "result" to diff.result, "hubCount" to diff.hubKeys.size, "libCount" to diff.libKeys.size,
      "onlyHub" to diff.onlyHub, "onlyLib" to diff.onlyLib, "categories" to diff.categories,
      "fieldDiffs" to diff.fields.take(500).map { linkedMapOf("key" to it.key, "path" to it.path, "hub" to it.hub, "lib" to it.lib, "category" to it.category) },
      "extra" to extra
    )
    File(dir, "${diff.scenario}.json").writeText(Platform.toJSON(json) + "\n")
    return diff
  }

  /** Records free-form observations of a scenario without a feature diff (e.g. errors, unsupported features). */
  fun observe(scenario: String, values: Map<String, Any?>) {
    report(scenario, show(values).let { Platform.toJSON(values).take(1500) })
    val dir = outDir ?: return
    dir.mkdirs()
    File(dir, "$scenario.json").writeText(Platform.toJSON(linkedMapOf("scenario" to scenario) + values) + "\n")
  }

  /** One matrix row: IML endpoint/params | lib-iml equivalent | result | note. Criticality is judged in the report. */
  fun row(item: String, iml: String, lib: String, result: String, note: String = "") {
    val line = listOf(item, iml, lib, result, note).joinToString("\t") { it.replace("\t", " ").replace("\n", " ") }
    println("MATRIX|$line")
    val dir = outDir ?: return
    dir.mkdirs()
    File(dir, "matrix-rows.tsv").appendText(line + "\n")
  }

  fun report(test: String, message: String) = println("PARITY|$test|$message")

  fun <T> timed(block: () -> T): Pair<T, Long> {
    val start = System.nanoTime()
    val value = block()
    return value to (System.nanoTime() - start) / 1_000_000
  }

  /** Runs a lib-iml call and returns either "ok: <summary>" or the exception class, Naksha error code and message. */
  fun <T> outcome(summary: (T) -> String = { it.toString() }, block: () -> T): String = try {
    "ok: " + summary(block())
  } catch (e: Throwable) {
    val code = (e as? naksha.base.NakshaException)?.error?.code
    "${e::class.simpleName}${if (code != null) "($code)" else ""}: ${e.message?.lineSequence()?.firstOrNull()?.take(200)}"
  }

  fun ids(features: List<Map<*, *>>): List<String> = features.map { it["id"] as String }.sorted()
}
