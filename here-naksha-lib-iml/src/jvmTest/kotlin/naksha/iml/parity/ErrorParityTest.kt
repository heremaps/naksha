package naksha.iml.parity

import naksha.base.Action
import naksha.base.Platform
import naksha.base.PlatformMap
import naksha.base.Version
import naksha.base.proxy
import naksha.geo.SpGeometry
import naksha.geo.SpPoint
import naksha.iml.ImlConfig
import naksha.iml.ImlStorage
import naksha.model.Naksha
import naksha.model.SessionOptions
import naksha.model.objects.StandardMembers
import naksha.model.objects.XyzMembers
import naksha.model.request.ErrorResponse
import naksha.model.request.SuccessResponse
import naksha.model.request.ops.Equals
import naksha.model.request.ops.Intersects
import naksha.model.request.query.DoubleOp
import naksha.model.request.query.PQuery
import naksha.model.request.query.Property
import naksha.iml.parity.Parity.hub
import naksha.iml.parity.Parity.observe
import naksha.iml.parity.Parity.request
import naksha.iml.parity.Parity.row
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

/**
 * Phase 2 parity, matrix item "errors: unknown layer, unknown feature, bad params". Compares IML HTTP codes with lib-iml outcomes:
 * whether lib-iml throws or returns an `ErrorResponse`, and which `NakshaError` code it uses.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ErrorParityTest {
  private lateinit var config: ImlConfig
  private lateinit var storage: ImlStorage
  private val results = LinkedHashMap<String, Map<String, String>>()

  @BeforeAll
  fun setUp() {
    Parity.assumeEnabled()
    val (c, s) = Parity.open("iml-eval-p2-errors")
    config = c
    storage = s
  }

  @AfterAll
  fun tearDown() {
    observe("errors", results)
    if (::config.isInitialized) Naksha.removeStorage(config)
  }

  /** Executes a read and classifies it: thrown exception (class + code) or returned ErrorResponse / SuccessResponse. */
  private fun lib(space: String = "ev-hist", options: SessionOptions? = null, build: naksha.model.request.ReadFeatures.() -> Unit = {}): String = try {
    storage.newReadSession(options).use { s ->
      when (val r = s.executeRead(request(space, build))) {
        is ErrorResponse -> "returned ErrorResponse(${r.error.code}): ${r.error.msg}"
        is SuccessResponse -> "returned SuccessResponse(${r.featureTupleList.size})"
        else -> "returned ${r::class.simpleName}"
      }
    }
  } catch (e: Throwable) {
    val code = (e as? naksha.base.NakshaException)?.error?.code
    "throws ${e::class.simpleName}${if (code != null) "($code)" else ""}: ${e.message?.lineSequence()?.firstOrNull()?.take(160)}"
  }

  private fun case(name: String, hubCall: () -> Parity.HubResponse, libDesc: String, libCall: () -> String) {
    val h = hubCall()
    val l = libCall()
    results[name] = mapOf("hub" to "${h.status} ${h.error ?: "n=${h.features.size}"}", "lib" to l)
    val hubErr = h.status >= 400
    val libErr = l.startsWith("throws") || l.contains("ErrorResponse")
    val result = when {
      hubErr && libErr -> "both fail"
      !hubErr && !libErr -> "both succeed"
      hubErr -> "diff (hub ${h.status}, lib succeeds)"
      else -> "diff (hub ${h.status}, lib fails)"
    }
    row("errors", "$name → ${h.status} ${h.error?.take(90) ?: ""}", libDesc, result, l)
  }

  private fun geometry(json: String): SpGeometry = (Platform.fromJSON(json) as PlatformMap).proxy(SpGeometry::class)

  @Test
  fun `unknown layer, catalog and feature`() {
    case("unknown layer", { hub("/spaces/nope/features/pt-2d") }, "collectionId=nope") { lib("nope") }
    case("unknown layer iterate", { hub("/spaces/nope/iterate") }, "collectionId=nope") { lib("nope") }
    case("unknown catalog", { hub("/spaces/nope/iterate") }, "catalogId=nope") { lib { catalogId = "nope" } }
    case("physical table name as layer", { hub("/spaces/" + physical("ev-hist") + "/iterate") }, "collectionId=<physical table>") { lib(physical("ev-hist")) }
    case("unknown feature", { hub("/spaces/ev-hist/features/nope") }, "Equals(Id, nope)") { lib { queryMembers = Equals(StandardMembers.Id, "nope") } }
    case("unknown branch", { hub("/spaces/ev-hist/iterate", listOf("versionRef" to "nobranch")) }, "n/a (no branch selector)") { "n/a" }
    case("NEW_LAYOUT space", { hub("/spaces/ev-nl/iterate") }, "collectionId=ev-nl (no space_meta row)") { lib("ev-nl") }
  }

  private fun physical(space: String) = java.sql.DriverManager.getConnection(Parity.dbUrl!!).use { c ->
    c.prepareStatement("SELECT h_id FROM xyz_config.space_meta WHERE id = ?").use { st -> st.setString(1, space); st.executeQuery().use { it.next(); it.getString(1) } }
  }

  @Test
  fun `bad parameters`() {
    case("limit=-1", { hub("/spaces/ev-hist/iterate", listOf("limit" to "-1")) }, "limit=-1") { lib { limit = -1 } }
    case("limit=0", { hub("/spaces/ev-hist/iterate", listOf("limit" to "0")) }, "limit=0") { lib { limit = 0 } }
    case("limit=100001", { hub("/spaces/ev-hist/iterate", listOf("limit" to "100001")) }, "limit=100001") { lib { limit = 100_001 } }
    case("versionRef=xx..yy", { hub("/spaces/ev-hist/iterate", listOf("versionRef" to "xx..yy")) }, "version=4L (unpacked)") { lib { queryHistory = true; version = 4L } }
    case("versionRef=-5", { hub("/spaces/ev-hist/iterate", listOf("versionRef" to "-5")) }, "version=Version.manual(-5)") {
      try { lib { queryHistory = true; version = Version.manual(-5, Action.VERSION).number } } catch (e: Throwable) { "throws ${e::class.simpleName}: ${e.message}" }
    }
    case("versions without history", { hub("/spaces/ev-v1/changesets", listOf("startVersion" to "0", "endVersion" to "5")) }, "versions=5 without queryHistory") { lib { versions = 5 } }
    case("bbox west=-200", { hub("/spaces/ev-hist/bbox", listOf("west" to "-200", "south" to "0", "east" to "8", "north" to "10")) }, "Intersects polygon lon -200") {
      lib { queryMembers = Intersects(XyzMembers.XyzGeometry, naksha.geo.SpBoundingBox(-200.0, 0.0, 8.0, 10.0).toPolygon()) }
    }
    case("spatial lat=95", { hub("/spaces/ev-hist/spatial", listOf("lat" to "95", "lon" to "8.5")) }, "Intersects SpPoint(8.5, 95)") {
      lib { queryMembers = Intersects(XyzMembers.XyzGeometry, SpPoint(8.5, 95.0)) }
    }
    val unclosed = """{"type":"Polygon","coordinates":[[[8.0,49.9],[8.7,49.9],[8.7,50.3]]]}"""
    case("spatial POST unclosed polygon", { hub("/spaces/ev-hist/spatial", method = "POST", body = unclosed) }, "Intersects(unclosed polygon)") {
      lib { queryMembers = Intersects(XyzMembers.XyzGeometry, geometry(unclosed)) }
    }
    case("arbitrary property path in SQL", { hub("/spaces/ev-hist/search", raw = "p.name=line%203d") }, "queryMembers Equals(\"properties.name\", …)") {
      lib { queryMembers = Equals("properties.name", "line 3d") }
    }
    case("unknown operator", { hub("/spaces/ev-hist/search", raw = "p.rank=xx=5") }, "n/a (typed API)") { "n/a" }
    case("p.* with != (DoubleOp.NE)", { hub("/spaces/ev-hist/search", raw = "p.rank!=5") }, "PQuery(p.rank, DoubleOp.NE, 5)") {
      lib { query.properties = PQuery(Property("properties", "rank"), DoubleOp.NE, 5) }
    }
    case("partial fetch (selection=p.name)", { hub("/spaces/ev-hist/iterate", listOf("selection" to "p.name")) }, "fetchMode = 1 (partial)") {
      lib { fetchMode = 1 }
    }
  }

  @Test
  fun `timeouts and connection errors`() {
    case("statement timeout (hub 26 s connector budget → 504)", { hub("/spaces/ev-big/iterate", listOf("limit" to "1")) }, "SessionOptions.stmtTimeout = 1 ms, full read of ev-big") {
      lib("ev-big", SessionOptions(appId = "iml-eval-parity", stmtTimeout = 1))
    }
    // The streaming read above returns its first 1 000-row fetch quickly; a full sort must finish before the first row.
    case("statement timeout on a sorting query", { hub("/spaces/ev-big/iterate", listOf("limit" to "1")) }, "stmtTimeout = 1 ms + OrderBy(updated_at) on ev-big") {
      lib("ev-big", SessionOptions(appId = "iml-eval-parity", stmtTimeout = 1)) { orderBy = naksha.model.request.OrderBy("updated_at", naksha.model.request.query.SortOrder.DESCENDING) }
    }
    val bad = ImlConfig("iml-eval-p2-badport", Parity.dbUrl!!.replace("localhost:5432", "localhost:1"))
    val outcome = try { Naksha.useStorage(bad); "ok" } catch (e: Throwable) {
      val code = (e as? naksha.base.NakshaException)?.error?.code
      "throws ${e::class.simpleName}${if (code != null) "($code)" else ""}: ${e.message?.lineSequence()?.firstOrNull()?.take(160)}"
    } finally { runCatching { Naksha.removeStorage(bad) } }
    results["database unreachable"] = mapOf("hub" to "n/a (502/504 from the connector)", "lib" to outcome)
    row("errors", "database unreachable → hub 502/504", "Naksha.useStorage(port 1)", "lib fails at init", outcome)
  }
}
