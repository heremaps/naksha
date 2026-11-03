package naksha.iml.parity

import naksha.base.Action
import naksha.base.Platform
import naksha.base.Version
import naksha.geo.SpBoundingBox
import naksha.iml.ImlConfig
import naksha.iml.ImlReadSession
import naksha.iml.ImlStorage
import naksha.iml.ImlTestFixture
import naksha.model.Naksha
import naksha.model.NakshaContext
import naksha.model.objects.NakshaFeature
import naksha.model.objects.StandardMembers
import naksha.model.objects.XyzMembers
import naksha.model.request.ReadCollections
import naksha.model.request.ReadFeatures
import naksha.model.request.SuccessResponse
import naksha.model.request.ops.Equals
import naksha.model.request.ops.Intersects
import naksha.model.request.ops.TagListContains
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.sql.DriverManager
import java.util.UUID

/**
 * Phase 1 of the lib-iml evaluation: open spaces created by a real local XYZ Hub with lib-iml (read-only) and
 * compare what lib-iml returns with what the Hub REST API returns for the same space.
 *
 * Gated by `IML_PARITY_HUB_URL` (e.g. `http://localhost:8080/hub`) and `NAKSHA_TEST_IML_DB_URL` (the Hub's database).
 * Both must be loopback URLs. Expects the data of `iml-naksha-eval/scripts/seed-all.sh`.
 *
 * The tests characterize behaviour: an assertion states what lib-iml does today, and each test prints a
 * `PARITY|<test>|...` line with the observation so the evaluation report can quote it.
 * Nothing in the Hub's spaces is modified. Only the NEW_LAYOUT test creates (and drops) its own scratch schema.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HubReadbackTest {
  private val hubUrl: String? = System.getenv("IML_PARITY_HUB_URL")
  private val dbUrl: String? = System.getenv("NAKSHA_TEST_IML_DB_URL")
  private val http = HttpClient.newHttpClient()
  private lateinit var config: ImlConfig
  private lateinit var storage: ImlStorage
  private var initMillis = 0L

  @BeforeAll
  fun setUp() {
    assumeTrue(!hubUrl.isNullOrBlank() && !dbUrl.isNullOrBlank(), "Set IML_PARITY_HUB_URL and NAKSHA_TEST_IML_DB_URL")
    val hub = URI(hubUrl!!)
    require(hub.host in setOf("localhost", "127.0.0.1")) { "IML_PARITY_HUB_URL must be local" }
    ImlTestFixture.validateLocalUrl(dbUrl!!)
    require(System.getenv("NAKSHA_TEST_IML_REMOTE_DB_URL").isNullOrBlank()) { "Remote DB URL must not be set" }
    NakshaContext.currentContext().withAppId("iml-eval-readback")
    config = ImlConfig("iml-eval-hub", dbUrl!!) // read-only defaults: schema=public, metadataSchema=xyz_config
    val start = System.nanoTime()
    storage = Naksha.useStorage(config) as ImlStorage
    initMillis = (System.nanoTime() - start) / 1_000_000
    report("setUp", "storage init (discovery of space_meta) took ${initMillis} ms")
  }

  @AfterAll
  fun tearDown() {
    if (::config.isInitialized) Naksha.removeStorage(config)
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Discovery

  @Test
  fun `discovery lists only spaces that have a space_meta row`() {
    val hubSpaces = hubSpaceIds()
    storage.newReadSession().use { session ->
      val collections = (session.executeRead(ReadCollections().apply { mapId = "public" }) as SuccessResponse)
        .features.mapNotNull { it?.id }.toSet()
      report("discovery", "hub spaces=$hubSpaces lib-iml collections(public)=$collections")
      assertTrue(collections.containsAll(listOf("ev-v1", "ev-hist", "ev-ext")))
      // NEW_LAYOUT spaces never get a space_meta row (xyz-hub ModifySpace.buildQuery only upserts it for OLD_LAYOUT).
      assertFalse("ev-nl" in collections, "ev-nl (NEW_LAYOUT) is not discoverable")
      // Branches live in DynamoDB (BranchConfigClient) plus a physical table; neither is visible to lib-iml.
      assertTrue(collections.none { it.contains("b1") || it.contains("_0_") }, "no branch collection")
      assertNull(session.getCollectionById(checkNotNull(session.getCatalogById("public")), "ev-nl"))
    }
  }

  // ---------------------------------------------------------------------------------------------------------------
  // HEAD reads of standalone spaces vs Hub /iterate

  @Test
  fun `HEAD of ev-v1 matches hub ids and properties`() = compareHead("ev-v1")

  @Test
  fun `HEAD of ev-hist matches hub ids and properties`() = compareHead("ev-hist")

  private fun compareHead(space: String) {
    val hub = hubFeatures("/spaces/$space/iterate", mapOf("limit" to "1000"))
    val (lib, millis) = timed { readAll(space) }
    val hubIds = hub.keys
    val libIds = lib.keys
    report("head-$space", "hub=${hubIds.size} lib=${libIds.size} libReadMs=$millis onlyHub=${hubIds - libIds} onlyLib=${libIds - hubIds}")
    assertEquals(hubIds, libIds)
    val diffs = ArrayList<String>()
    for (id in hubIds) {
      val h = hub.getValue(id)
      val l = lib.getValue(id)
      diffs += diffFeature(id, h, l)
    }
    for (d in diffs) report("head-$space-diff", d)
    // User properties must survive; only @ns:com:here:xyz bookkeeping may differ.
    assertTrue(diffs.none { it.contains("properties.") && !it.contains("@ns:com:here:xyz") }, diffs.joinToString("\n"))
  }

  // ---------------------------------------------------------------------------------------------------------------
  // IDs, geometry, size

  @Test
  fun `special ids are found by exact id`() {
    val ids = listOf("12345", "00123", "straße-東京-🚀-ünï", "q'uote\"%_\\id", "long-" + "x".repeat(295))
    storage.newReadSession().use { session ->
      for (id in ids) {
        val found = (session.executeRead(request("ev-hist") { queryMembers = Equals(StandardMembers.Id, id) }) as SuccessResponse)
          .features.mapNotNull { it?.id }
        val hub = hubFeatures("/spaces/ev-hist/features", mapOf("id" to id)).keys
        report("ids", "id(len=${id.length})=${id.take(24)} lib=${found.map { it.take(24) }} hub=${hub.map { it.take(24) }}")
        assertEquals(listOf(id), found)
        assertEquals(setOf(id), hub)
      }
      // A numeric-looking ID must not match its numeric twin.
      val twin = (session.executeRead(request("ev-hist") { queryMembers = Equals(StandardMembers.Id, "123") }) as SuccessResponse).features
      assertEquals(0, twin.size)
    }
  }

  @Test
  fun `geometry edge cases and 3D coordinates`() {
    val lib = readAll("ev-hist")
    val hub = hubFeatures("/spaces/ev-hist/iterate", mapOf("limit" to "1000"))
    for (id in listOf("geo-null", "geo-absent", "geo-empty", "pt-2d", "pt-3d", "ln-3d", "pg-hole", "am-east")) {
      report("geometry", "$id lib=${geometryJson(lib[id])} hub=${geometryJson(hub[id])}")
    }
    assertNull(lib.getValue("geo-null")["geometry"])
    assertNull(lib.getValue("geo-absent")["geometry"])
    val z = coordinates(lib.getValue("pt-3d"))
    assertEquals(123.5, (z[2] as Number).toDouble(), 1e-9)
  }

  /** Asserts correct behaviour (only with IML_PARITY_STRICT=true); FAILS until finding F1 (integer coordinates in nested geometries) is fixed. */
  @Test
  fun `expected - nested geometries keep integer coordinates and Z (FAILS until fixed)`() {
    assumeTrue(System.getenv("IML_PARITY_STRICT") == "true", "Set IML_PARITY_STRICT=true to run tests that assert the fixed behaviour")
    val lib = readAll("ev-hist")
    val hub = hubFeatures("/spaces/ev-hist/iterate", mapOf("limit" to "1000"))
    val broken = listOf("ln-3d", "ln-2d", "pg-hole", "pg-3d", "mpg", "mln").filter { id ->
      diffFeature(id, mapOf("g" to hub.getValue(id)["geometry"]), mapOf("g" to lib.getValue(id)["geometry"])).isNotEmpty()
    }
    report("geometry-int", "features with coordinate differences: $broken")
    assertEquals(emptyList<String>(), broken)
  }

  @Test
  fun `large feature over 2 MB materializes completely`() {
    val (lib, millis) = timed { readById("ev-hist", "big") }
    val blob = (lib.getValue("big")["properties"] as Map<*, *>)["blob"] as String
    report("large", "blob length=${blob.length} readMs=$millis")
    assertEquals(2_500_000, blob.length)
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Bounding box incl. antimeridian, tags

  @Test
  fun `bbox and tag reads agree with hub`() {
    val bbox = SpBoundingBox(8.0, 49.9, 8.7, 50.3) // west, south, east, north
    storage.newReadSession().use { session ->
      val lib = (session.executeRead(request("ev-hist") { queryMembers = Intersects(XyzMembers.XyzGeometry, bbox.toPolygon()) }) as SuccessResponse)
        .features.mapNotNull { it?.id }.toSet()
      val hub = hubFeatures("/spaces/ev-hist/bbox", mapOf("west" to "8.0", "south" to "49.9", "east" to "8.7", "north" to "50.3")).keys
      report("bbox", "lib=$lib hub=$hub")
      assertEquals(hub, lib)
      val libTag = (session.executeRead(request("ev-hist") { queryMembers = TagListContains(XyzMembers.XyzTags, "red") }) as SuccessResponse)
        .features.mapNotNull { it?.id }.toSet()
      val hubTag = hubFeatures("/spaces/ev-hist/search", mapOf("tags" to "red")).keys
      report("tags", "lib=$libTag hub=$hubTag")
      assertEquals(hubTag, libTag)
    }
    // Antimeridian: Hub accepts west > east; lib-iml needs the caller to build a polygon. Recorded, compared in Phase 2.
    val hubAm = hubFeatures("/spaces/ev-hist/bbox", mapOf("west" to "179", "south" to "9", "east" to "-179", "north" to "11")).keys
    report("bbox-antimeridian", "hub(west=179,east=-179)=$hubAm")
  }

  // ---------------------------------------------------------------------------------------------------------------
  // History

  @Test
  fun `historical snapshot at version 4 matches hub versionRef 4`() {
    storage.newReadSession().use { session ->
      val lib = (session.executeRead(request("ev-hist") {
        queryMembers = Equals(StandardMembers.Id, "pt-2d")
        queryHistory = true
        version = Version.manual(4, Action.VERSION).number
      }) as SuccessResponse).features.mapNotNull { it }
      val hub = hubFeatures("/spaces/ev-hist/features", mapOf("id" to "pt-2d", "versionRef" to "4"))
      val libName = lib.singleOrNull()?.properties?.getRaw("name")
      val hubName = (hub["pt-2d"]?.get("properties") as Map<*, *>?)?.get("name")
      report("history", "lib name=$libName hub name=$hubName")
      assertEquals("point 2d v2", libName)
      assertEquals(hubName, libName)
    }
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Composite and branch (the phase goal)

  @Test
  fun `composite ev-ext is read as its delta table only`() {
    val lib = readAll("ev-ext", deleted = true)
    val hubComposite = hubFeatures("/spaces/ev-ext/iterate", mapOf("limit" to "1000")).keys
    val hubExtension = hubFeatures("/spaces/ev-ext/iterate", mapOf("limit" to "1000", "context" to "extension")).keys
    report("composite", "lib=${lib.keys} hub(default)=${hubComposite.size} ids hub(extension)=$hubExtension")
    report("composite", "hub default has mpg=${"mpg" in hubComposite}; lib has mpg=${"mpg" in lib} action=${xyzOf(lib["mpg"])?.get("action")}")
    // lib-iml sees only the delta table: no base features, and the hide row of mpg (operation H) comes back as a live feature.
    assertFalse("pt-3d" in lib.keys)
    assertTrue("mpg" in lib.keys && "mpg" !in hubComposite)
    assertTrue(lib.keys.size < hubComposite.size)
  }

  @Test
  fun `branch b1 is not readable, ev-hist always returns main`() {
    val main = readAll("ev-hist")
    val hubMain = hubFeatures("/spaces/ev-hist/iterate", mapOf("limit" to "1000"))
    val hubBranch = hubFeatures("/spaces/ev-hist/iterate", mapOf("limit" to "1000", "versionRef" to "b1"))
    val onlyBranch = hubBranch.keys - main.keys
    val onlyMain = main.keys - hubBranch.keys
    val changed = (hubBranch.keys intersect main.keys).filter {
      (hubBranch.getValue(it)["properties"] as Map<*, *>)["name"] != (main.getValue(it)["properties"] as Map<*, *>)["name"]
    }
    report("branch", "hub(b1)=${hubBranch.size} lib(ev-hist)=${main.size} onlyInBranch=$onlyBranch onlyInLib=$onlyMain differentName=$changed")
    assertEquals(hubMain.keys, main.keys)
    assertTrue("b-only" in onlyBranch && "main-only" in onlyMain && "mln" in onlyMain)
    assertTrue("ln-2d" in changed && "pt-3d" in changed)
    // There is no request field to select a branch: ReadFeatures has catalogId/collectionId/version only.
  }

  // ---------------------------------------------------------------------------------------------------------------
  // NEW_LAYOUT (jsondata TEXT), registered through a scratch metadata schema

  @Test
  fun `NEW_LAYOUT table read through a scratch space_meta`() {
    val nlTable = (hubJson("/spaces/ev-nl", emptyMap()) as Map<*, *>).let { ((it["storage"] as Map<*, *>)["params"] as Map<*, *>)["tableName"] as String }
    val scratch = "iml_eval_meta_" + UUID.randomUUID().toString().replace("-", "").take(12)
    DriverManager.getConnection(dbUrl).use { c ->
      c.createStatement().use {
        it.execute("CREATE SCHEMA \"$scratch\"")
        it.execute("CREATE TABLE \"$scratch\".space_meta (id text NOT NULL, schem text NOT NULL, h_id text, meta jsonb, PRIMARY KEY(id, schem))")
      }
      c.prepareStatement("INSERT INTO \"$scratch\".space_meta VALUES ('ev-nl', 'public', ?, '{}')").use { it.setString(1, nlTable); it.executeUpdate() }
    }
    val nlConfig = ImlConfig("iml-eval-nl", dbUrl!!).apply { metadataSchema = scratch }
    try {
      val nl = Naksha.useStorage(nlConfig) as ImlStorage
      nl.newReadSession().use { session ->
        val head = (session.executeRead(ReadFeatures().apply { catalogId = "public"; collectionId = "ev-nl" }) as SuccessResponse)
        val ids = head.features.mapNotNull { it?.id }.toSet()
        val hub = hubFeatures("/spaces/ev-nl/iterate", mapOf("limit" to "1000")).keys
        report("new-layout", "HEAD lib=${ids.size} hub=${hub.size} equal=${ids == hub}")
        assertEquals(hub, ids)
        val tag = runCatching {
          (session.executeRead(ReadFeatures().apply {
            catalogId = "public"; collectionId = "ev-nl"; queryMembers = TagListContains(XyzMembers.XyzTags, "red")
          }) as SuccessResponse).features.size
        }
        report("new-layout", "tag query: ${tag.fold({ "ok size=$it" }, { "FAILED ${it::class.simpleName}: ${it.message?.lineSequence()?.firstOrNull()}" })}")
        assertTrue(tag.isFailure, "JSONB operators on jsondata TEXT fail")
      }
    } finally {
      Naksha.removeStorage(nlConfig)
      DriverManager.getConnection(dbUrl).use { c -> c.createStatement().use { it.execute("DROP SCHEMA \"$scratch\" CASCADE") } }
    }
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Helpers

  private fun request(space: String, configure: ReadFeatures.() -> Unit = {}) = ReadFeatures().apply {
    catalogId = "public"
    collectionId = space
    configure()
  }

  private fun readAll(space: String, deleted: Boolean = false): Map<String, Map<*, *>> = storage.newReadSession().use { session ->
    toMaps((session.executeRead(request(space) { queryDeleted = deleted }) as SuccessResponse).features.mapNotNull { it })
  }

  private fun readById(space: String, id: String): Map<String, Map<*, *>> = storage.newReadSession().use { session: ImlReadSession ->
    toMaps((session.executeRead(request(space) { queryMembers = Equals(StandardMembers.Id, id) }) as SuccessResponse).features.mapNotNull { it })
  }

  private fun toMaps(features: List<NakshaFeature>): Map<String, Map<*, *>> =
    features.associate { checkNotNull(it.id) to (Platform.fromJSON(Platform.toJSON(it)) as Map<*, *>) }

  private fun hubSpaceIds(): Set<String> = (hubJson("/spaces", emptyMap()) as List<*>).map { (it as Map<*, *>)["id"] as String }.toSet()

  private fun hubFeatures(path: String, query: Map<String, String>): Map<String, Map<*, *>> {
    val fc = hubJson(path, query) as Map<*, *>
    return (fc["features"] as List<*>? ?: emptyList<Any>()).associate { f -> ((f as Map<*, *>)["id"] as String) to f }
  }

  private fun hubJson(path: String, query: Map<String, String>): Any? {
    val qs = query.entries.joinToString("&") { "${it.key}=${URLEncoder.encode(it.value, Charsets.UTF_8)}" }
    val uri = URI(hubUrl!!.trimEnd('/') + path + if (qs.isEmpty()) "" else "?$qs")
    val response = http.send(HttpRequest.newBuilder(uri).header("Accept", "application/json").GET().build(), HttpResponse.BodyHandlers.ofString())
    check(response.statusCode() == 200) { "GET $uri -> ${response.statusCode()}: ${response.body().take(300)}" }
    return Platform.fromJSON(response.body())
  }

  private fun xyzOf(feature: Map<*, *>?): Map<*, *>? = (feature?.get("properties") as Map<*, *>?)?.get("@ns:com:here:xyz") as Map<*, *>?

  private fun geometryJson(feature: Map<*, *>?): String = if (feature == null) "<missing>" else Platform.toJSON(feature["geometry"]).take(120)

  private fun coordinates(feature: Map<*, *>): List<*> = (feature["geometry"] as Map<*, *>)["coordinates"] as List<*>

  /** Returns one line per differing leaf, with dotted paths; geometry coordinates compared with 1e-9 tolerance. */
  private fun diffFeature(id: String, hub: Map<*, *>, lib: Map<*, *>): List<String> {
    val out = ArrayList<String>()
    fun walk(path: String, a: Any?, b: Any?) {
      when {
        a is Map<*, *> && b is Map<*, *> -> for (k in (a.keys + b.keys)) walk(if (path.isEmpty()) "$k" else "$path.$k", a[k], b[k])
        a is List<*> && b is List<*> && a.size == b.size -> a.indices.forEach { walk("$path[$it]", a[it], b[it]) }
        a is Number && b is Number -> if (Math.abs(a.toDouble() - b.toDouble()) > 1e-9) out += "$id $path hub=$a lib=$b"
        a != b -> out += "$id $path hub=${Platform.toJSON(a).take(80)} lib=${Platform.toJSON(b).take(80)}"
      }
    }
    walk("", hub, lib)
    return out
  }

  private fun <T> timed(block: () -> T): Pair<T, Long> {
    val start = System.nanoTime()
    val value = block()
    return value to (System.nanoTime() - start) / 1_000_000
  }

  private fun report(test: String, message: String) = println("PARITY|$test|$message")
}
