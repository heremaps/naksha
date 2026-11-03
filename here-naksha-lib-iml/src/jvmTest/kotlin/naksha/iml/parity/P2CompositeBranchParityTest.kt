package naksha.iml.parity

import naksha.base.Action
import naksha.base.Version
import naksha.iml.ImlConfig
import naksha.iml.ImlStorage
import naksha.model.Naksha
import naksha.iml.parity.Parity.diff
import naksha.iml.parity.Parity.hubOk
import naksha.iml.parity.Parity.ids
import naksha.iml.parity.Parity.libRead
import naksha.iml.parity.Parity.observe
import naksha.iml.parity.Parity.outcome
import naksha.iml.parity.Parity.record
import naksha.iml.parity.Parity.row
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.sql.DriverManager
import java.util.UUID

/**
 * Phase 2 parity, matrix items "reading a composite (extends) layer" and "reading a branch" (the phase goal).
 *
 * Besides the plain comparison, two proofs of concept show how much is missing: both compose the hub result on the client from
 * lib-iml primitives (snapshot reads of the base plus a read of the delta/branch table registered in a scratch `space_meta`).
 * The scratch schema is created and dropped by the test; the hub's own tables are only read.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class P2CompositeBranchParityTest {
  private lateinit var config: ImlConfig
  private lateinit var storage: ImlStorage
  private val xyz = "@ns:com:here:xyz"

  @BeforeAll
  fun setUp() {
    Parity.assumeEnabled()
    val (c, s) = Parity.open("iml-eval-p2-composite")
    config = c
    storage = s
  }

  @AfterAll
  fun tearDown() {
    if (::config.isInitialized) Naksha.removeStorage(config)
  }

  private fun v(n: Long) = Version.manual(n, Action.VERSION).number
  private fun ns(f: Map<*, *>) = (f["properties"] as Map<*, *>)[xyz] as Map<*, *>
  private fun hubIter(space: String, vararg q: Pair<String, String>) = hubOk("/spaces/$space/iterate", listOf("limit" to "1000") + q).features

  // ---------------------------------------------------------------------------------------------------------------
  // Composite

  @Test
  fun `composite - contexts`() {
    val lib = libRead(storage, "ev-ext")
    val default = record(diff("composite-default", hubIter("ev-ext"), lib))
    val extension = record(diff("composite-extension", hubIter("ev-ext", "context" to "extension"), lib))
    val superCtx = record(diff("composite-super", hubIter("ev-ext", "context" to "super"), libRead(storage, "ev-hist")))
    val mpg = lib.firstOrNull { it["id"] == "mpg" }?.let { ns(it) }
    observe("composite-hide-row", mapOf("libMpgAction" to mpg?.get("action"), "libMpgDeleted" to mpg?.get("deleted"), "libMpgVersion" to mpg?.get("version")))
    row("composite", "GET /spaces/ev-ext/iterate (context=default: base + delta, hides applied)", "ReadFeatures on ev-ext", default.result, default.summary())
    row("composite", "context=extension (delta only, hide rows flagged deleted=true)", "ReadFeatures on ev-ext", extension.result, extension.summary() + " lib mpg action=${mpg?.get("action")}")
    row("composite", "context=super (base only)", "ReadFeatures on the base layer ev-hist (caller must know it)", superCtx.result, superCtx.summary())
    val ctxParam = outcome({ l: List<Map<*, *>> -> "${l.size}" }) { libRead(storage, "ev-ext") { setRaw("context", "default") } }
    row("composite", "?context=…", "no request field for a context (unknown raw field set → $ctxParam)", "unsupported", "")
  }

  @Test
  fun `composite - client-side composition proof of concept`() {
    val base = libRead(storage, "ev-hist").associateBy { it["id"] as String }
    val delta = libRead(storage, "ev-ext") { queryDeleted = true }
    val composed = LinkedHashMap(base)
    for (f in delta) {
      // Hide rows (H/J) come back with action VERSION (unknown operation); deletions in the extension with DELETE.
      if (ns(f)["action"] == "VERSION" || ns(f)["action"] == "DELETE") composed.remove(f["id"]) else composed[f["id"] as String] = f
    }
    val d = record(diff("composite-poc", hubIter("ev-ext"), composed.values.toList()))
    row("composite (PoC)", "GET /spaces/ev-ext/iterate (default)", "client: base ev-hist HEAD + ev-ext delta, VERSION/DELETE rows remove", d.result, d.summary())
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Branch

  @Test
  fun `branch - hub refs vs lib`() {
    val branches = hubOk("/spaces/ev-hist/branches").json as List<*>
    val out = LinkedHashMap<String, String>()
    for (ref in listOf("b1", "b1:HEAD", "b1:10", "b1:12", "b1:13")) {
      val hub = hubIter("ev-hist", "versionRef" to ref)
      val lib = libRead(storage, "ev-hist")
      val d = record(diff("branch-$ref".replace(":", "_"), hub, lib))
      out[ref] = d.summary()
      row("branch", "GET /spaces/ev-hist/iterate?versionRef=$ref (n=${hub.size})", "none: ReadFeatures has no branch selector; reading ev-hist returns main", "unsupported (silently main)", d.summary())
    }
    val byId = hubOk("/spaces/ev-hist/features", listOf("id" to "ln-2d", "id" to "b-only", "id" to "mln", "versionRef" to "b1")).features
    row("branch", "GET /features?id=ln-2d&id=b-only&id=mln&versionRef=b1 → ${ids(byId)}", "none", "unsupported", "")
    val stats = hubOk("/spaces/ev-hist/statistics", listOf("versionRef" to "b1")).json as Map<*, *>
    row("branch", "GET /statistics?versionRef=b1 → count ${naksha.base.Platform.toJSON(stats["count"])}", "none", "unsupported", "")
    val discovery = storage.newReadSession().use { s -> s.getCollectionById(checkNotNull(s.getCatalogById("public")), "ev-hist:b1") }
    observe("branch-refs", out + mapOf("hubBranches" to naksha.base.Platform.toJSON(branches), "libCollection(ev-hist:b1)" to discovery?.id))
  }

  @Test
  fun `branch - client-side composition proof of concept`() {
    val branch = (hubOk("/spaces/ev-hist/branches").json as List<*>).map { it as Map<*, *> }.single { it["id"] == "b1" }
    val baseVersion = (branch["baseRef"] as String).substringAfter(":").toLong()
    val dbUrl = Parity.dbUrl!!
    val (root, branchTable) = DriverManager.getConnection(dbUrl).use { c ->
      val root = c.prepareStatement("SELECT h_id FROM xyz_config.space_meta WHERE id = 'ev-hist'").use { st -> st.executeQuery().use { it.next(); it.getString(1) } }
      // BranchManager.branchTableName: <root>_<baseNodeId>_<baseVersion>_<nodeId>; b1 is node 1 on main (node 0).
      val table = c.prepareStatement("SELECT relname FROM pg_class WHERE relkind = 'p' AND relname = ?").use { st ->
        st.setString(1, "${root}_0_${baseVersion}_1"); st.executeQuery().use { if (it.next()) it.getString(1) else null }
      }
      root to checkNotNull(table) { "branch table ${root}_0_${baseVersion}_1 not found" }
    }
    val scratch = "iml_eval_br_" + UUID.randomUUID().toString().replace("-", "").take(10)
    DriverManager.getConnection(dbUrl).use { c ->
      c.createStatement().use {
        it.execute("CREATE SCHEMA \"$scratch\"")
        it.execute("CREATE TABLE \"$scratch\".space_meta (id text NOT NULL, schem text NOT NULL, h_id text, meta jsonb, PRIMARY KEY(id, schem))")
      }
      c.prepareStatement("INSERT INTO \"$scratch\".space_meta VALUES ('ev-hist', 'public', ?, '{}'), ('b1-delta', 'public', ?, '{}')").use {
        it.setString(1, root); it.setString(2, branchTable); it.executeUpdate()
      }
    }
    val (brConfig, br) = Parity.open("iml-eval-p2-branch") { metadataSchema = scratch }
    try {
      val results = LinkedHashMap<String, String>()
      val deltaHead = libRead(br, "b1-delta") { queryDeleted = true }
      results["deltaRows"] = deltaHead.map { "${it["id"]}:${ns(it)["action"]}:rel v${Parity.libVersion(it)}" }.toString()
      for ((ref, target) in listOf("b1:HEAD" to null, "b1:10" to 10L, "b1:12" to 12L, "b1:13" to 13L)) {
        val main = libRead(br, "ev-hist") { queryHistory = true; version = v(baseVersion) }.associateBy { it["id"] as String }
        val delta: List<Map<*, *>> = libRead(br, "b1-delta") {
          queryDeleted = true
          if (target != null) { queryHistory = true; version = v(target - baseVersion) }
        }
        val composed = LinkedHashMap(main)
        for (f in delta) {
          if (ns(f)["action"] == "DELETE") composed.remove(f["id"]) else composed[f["id"] as String] = rebase(f, baseVersion)
        }
        val hub = hubIter("ev-hist", "versionRef" to ref)
        val d = record(diff("branch-poc-${ref.replace(":", "_")}", hub, composed.values.toList()))
        results[ref] = d.summary()
        row("branch (PoC)", "GET /iterate?versionRef=$ref", "client: ev-hist snapshot at base $baseVersion + branch table $branchTable (scratch space_meta), versions + $baseVersion", d.result, d.summary())
      }
      // Without the rebase, branch versions are relative to the base (BranchManager: own sequence starting at the base version).
      val raw = libRead(br, "b1-delta").map { "${it["id"]}@${Parity.libVersion(it)}" }
      results["deltaVersionsWithoutRebase"] = raw.toString()
      results["hubBranchVersions"] = hubOk("/spaces/ev-hist/features", listOf("id" to "b-only", "id" to "ln-2d", "versionRef" to "b1")).features.map { "${it["id"]}@${Parity.hubVersion(it)}" }.toString()
      observe("branch-poc", results)
      assertTrue(results["b1:HEAD"]!!.contains("onlyHub=[] onlyLib=[]"), results.toString())
    } finally {
      Naksha.removeStorage(brConfig)
      DriverManager.getConnection(dbUrl).use { c -> c.createStatement().use { it.execute("DROP SCHEMA \"$scratch\" CASCADE") } }
    }
  }

  /** Shifts a branch-relative lib-iml version (packed) by the branch base version, so it compares with the hub's absolute version. */
  @Suppress("UNCHECKED_CAST")
  private fun rebase(f: Map<*, *>, base: Long): Map<*, *> {
    val copy = LinkedHashMap(f as Map<Any?, Any?>)
    val props = LinkedHashMap((f["properties"] as Map<Any?, Any?>))
    val n = LinkedHashMap(props[xyz] as Map<Any?, Any?>)
    val packed = (n["version"] as Number).toLong()
    n["version"] = ((packed ushr 2) + base shl 2) or (packed and 3)
    props[xyz] = n
    copy["properties"] = props
    return copy
  }
}
