package naksha.iml.parity

import naksha.geo.SpBoundingBox
import naksha.geo.SpGeometry
import naksha.geo.SpPoint
import naksha.iml.ImlConfig
import naksha.iml.ImlStorage
import naksha.base.Platform
import naksha.model.Naksha
import naksha.model.objects.StandardMembers
import naksha.model.objects.XyzMembers
import naksha.model.request.SuccessResponse
import naksha.model.request.ops.Equals
import naksha.model.request.ops.Intersects
import naksha.model.request.ops.IsAnyOf
import naksha.model.request.ops.SpBuffer
import naksha.model.request.query.DoubleOp
import naksha.model.request.query.PQuery
import naksha.model.request.query.Property
import naksha.model.request.query.SpIntersects
import naksha.iml.parity.Parity.diff
import naksha.iml.parity.Parity.hubOk
import naksha.iml.parity.Parity.ids
import naksha.iml.parity.Parity.libRead
import naksha.iml.parity.Parity.observe
import naksha.iml.parity.Parity.outcome
import naksha.iml.parity.Parity.record
import naksha.iml.parity.Parity.row
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.exp

/**
 * Phase 2 parity, matrix items "get by id", "/bbox", "/tile", "/spatial" and the count part of "/statistics".
 * Assertions pin what was observed (characterization); every scenario writes its raw diff via [Parity.record].
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FeatureReadParityTest {
  private lateinit var config: ImlConfig
  private lateinit var storage: ImlStorage

  @BeforeAll
  fun setUp() {
    Parity.assumeEnabled()
    val (c, s) = Parity.open("iml-eval-p2-read")
    config = c
    storage = s
  }

  @AfterAll
  fun tearDown() {
    if (::config.isInitialized) Naksha.removeStorage(config)
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Get by id

  @Test
  fun `by id - single and several ids`() {
    for (space in listOf("ev-hist", "ev-v1")) {
      val hubOne = hubOk("/spaces/$space/features/pt-2d").json as Map<*, *>
      val libOne = libRead(storage, space) { queryMembers = Equals(StandardMembers.Id, "pt-2d") }
      val one = record(diff("byid-one-$space", listOf(hubOne), libOne, v1Layer = space == "ev-v1"))
      assertTrue(one.sameKeys)

      val wanted = listOf("pt-2d", "ln-3d", "pg-hole", "12345", "nope")
      val hubMany = hubOk("/spaces/$space/features", wanted.map { "id" to it }).features
      val libMany = libRead(storage, space) { queryMembers = IsAnyOf(StandardMembers.Id, *wanted.toTypedArray()) }
      val many = record(diff("byid-many-$space", hubMany, libMany, v1Layer = space == "ev-v1"))
      assertTrue(many.sameKeys)
      assertEquals(listOf("12345", "ln-3d", "pg-hole", "pt-2d"), many.hubKeys)
      row("by id", "GET /spaces/$space/features/{id}", "ReadFeatures + Equals(Id)", one.result, one.summary())
      row("by id", "GET /spaces/$space/features?id=a&id=b…", "ReadFeatures + IsAnyOf(Id, …)", many.result, many.summary())
    }
    // Legacy featureIds path (deprecated, still supported by lib-iml).
    val legacy = libRead(storage, "ev-hist") { featureIds.addAll(listOf("pt-2d", "ln-3d")) }
    assertEquals(listOf("ln-3d", "pt-2d"), ids(legacy))
  }

  @Test
  fun `by id - unknown id`() {
    val hubSingle = Parity.hub("/spaces/ev-hist/features/nope")
    val hubMany = Parity.hub("/spaces/ev-hist/features", listOf("id" to "nope"))
    val lib = outcome({ l: List<Map<*, *>> -> "${l.size} features" }) { libRead(storage, "ev-hist") { queryMembers = Equals(StandardMembers.Id, "nope") } }
    observe("byid-unknown", mapOf("hubSingle" to "$hubSingle", "hubMany" to "$hubMany", "lib" to lib))
    assertEquals(404, hubSingle.status)
    assertEquals("ok: 0 features", lib)
    row("by id", "GET /features/{unknown} → 404; ?id=unknown → 200 []", "Equals(Id) → empty SuccessResponse", "same (lib has no 404; caller checks empty)", lib)
  }

  // ---------------------------------------------------------------------------------------------------------------
  // BBox

  private fun bbox(space: String, w: Double, s: Double, e: Double, n: Double, extra: List<Pair<String, String>> = emptyList()) =
    hubOk("/spaces/$space/bbox", listOf("west" to "$w", "south" to "$s", "east" to "$e", "north" to "$n", "limit" to "100000") + extra).features

  private fun libBbox(space: String, w: Double, s: Double, e: Double, n: Double) =
    libRead(storage, space) { queryMembers = Intersects(XyzMembers.XyzGeometry, SpBoundingBox(w, s, e, n).toPolygon()) }

  @Test
  fun `bbox - plain, lines and polygons`() {
    val cases = listOf(
      "bbox-frankfurt" to doubleArrayOf(8.0, 49.9, 8.7, 50.3),
      "bbox-europe" to doubleArrayOf(0.0, 40.0, 20.0, 60.0),
      "bbox-world" to doubleArrayOf(-180.0, -90.0, 180.0, 90.0),
      "bbox-hole-inside" to doubleArrayOf(10.12, 52.12, 10.18, 52.18) // inside the polygon hole of pg-hole
    )
    for ((name, b) in cases) {
      val hub = bbox("ev-hist", b[0], b[1], b[2], b[3])
      val lib = libBbox("ev-hist", b[0], b[1], b[2], b[3])
      val d = record(diff(name, hub, lib))
      assertTrue(d.sameKeys, d.summary())
      row("/bbox", "GET /bbox?west=${b[0]}&south=${b[1]}&east=${b[2]}&north=${b[3]}", "Intersects(geo, SpBoundingBox.toPolygon())", d.result, d.summary())
    }
  }

  @Test
  fun `bbox - big layer counts`() {
    val hub = bbox("ev-big", -10.0, 0.0, 10.0, 10.0)
    val lib = libBbox("ev-big", -10.0, 0.0, 10.0, 10.0)
    val d = record(diff("bbox-big", hub, lib, v1Layer = true))
    assertTrue(d.sameKeys, d.summary())
    row("/bbox", "GET /spaces/ev-big/bbox (${hub.size} features)", "Intersects(geo, polygon)", d.result, d.summary())
  }

  @Test
  fun `bbox - south greater than north and out of range`() {
    val hubSwapped = bbox("ev-hist", 8.0, 50.3, 8.7, 49.9)
    val libSwapped = outcome({ l: List<Map<*, *>> -> ids(l).toString() }) { libBbox("ev-hist", 8.0, 50.3, 8.7, 49.9) }
    val hubOut = Parity.hub("/spaces/ev-hist/bbox", listOf("west" to "-200", "south" to "0", "east" to "8", "north" to "10"))
    val libOut = outcome({ l: List<Map<*, *>> -> ids(l).toString() }) { libBbox("ev-hist", -200.0, 0.0, 8.0, 10.0) }
    observe("bbox-invalid", mapOf("hubSouthGtNorth" to ids(hubSwapped).toString(), "libSouthGtNorth" to libSwapped, "hubLonOutOfRange" to "$hubOut", "libLonOutOfRange" to libOut))
    row("/bbox", "south>north (hub accepts, normalizes)", "polygon from SpBoundingBox", if ("ok: ${ids(hubSwapped)}" == libSwapped) "same" else "diff", "hub=${ids(hubSwapped)} lib=$libSwapped")
    row("/bbox", "west=-200 → hub 400", "polygon with lon -200", if (libOut.startsWith("ok")) "diff (no validation)" else "same (rejected)", "hub=$hubOut lib=$libOut")
  }

  @Test
  fun `bbox - antimeridian`() {
    // The seed has am-east (179.5, 10) and am-west (-179.5, 10).
    val hubAm = bbox("ev-hist", 179.0, 9.0, -179.0, 11.0)
    val libAm = outcome({ l: List<Map<*, *>> -> ids(l).toString() }) { libBbox("ev-hist", 179.0, 9.0, -179.0, 11.0) }
    // What a caller has to do in lib-iml to get the two features: split the box at ±180 and OR the parts.
    val libSplit = libRead(storage, "ev-hist") {
      queryMembers = naksha.model.request.ops.Or(
        Intersects(XyzMembers.XyzGeometry, SpBoundingBox(179.0, 9.0, 180.0, 11.0).toPolygon()),
        Intersects(XyzMembers.XyzGeometry, SpBoundingBox(-180.0, 9.0, -179.0, 11.0).toPolygon())
      )
    }
    val hubWide = bbox("ev-hist", 170.0, -90.0, -170.0, 90.0)
    val libWide = outcome({ l: List<Map<*, *>> -> ids(l).toString() }) { libBbox("ev-hist", 170.0, -90.0, -170.0, 90.0) }
    observe("bbox-antimeridian", mapOf(
      "hub(west=179,east=-179)" to ids(hubAm), "lib(west=179,east=-179)" to libAm, "libSplitAt180" to ids(libSplit),
      "hub(west=170,east=-170,lat±90)" to ids(hubWide), "lib(west=170,east=-170,lat±90)" to libWide
    ))
    assertEquals(listOf("am-east", "am-west"), ids(libSplit))
    row("/bbox", "antimeridian west=179&east=-179 → hub ${ids(hubAm)}", "SpBoundingBox(179,…,-179,…).toPolygon()", if ("ok: ${ids(hubAm)}" == libAm) "same (both don't wrap)" else "diff", "lib=$libAm; split at ±180 + Or → ${ids(libSplit)}")
    row("/bbox", "west=170&east=-170 (lat ±90) → hub n=${hubWide.size}", "same box as polygon", if ("ok: ${ids(hubWide)}" == libWide) "same (both read it as -170..170)" else "diff", "lib=$libWide")
  }

  @Test
  fun `bbox - clip and force2D`() {
    val hubClip = bbox("ev-hist", 8.05, 49.9, 8.15, 50.3, listOf("clip" to "true"))
    val hubNoClip = bbox("ev-hist", 8.05, 49.9, 8.15, 50.3)
    val lib = libBbox("ev-hist", 8.05, 49.9, 8.15, 50.3)
    val clipDiff = record(diff("bbox-clip", hubClip, lib))
    val noClipDiff = record(diff("bbox-noclip", hubNoClip, lib))
    val hub2d = bbox("ev-hist", 8.0, 49.9, 8.7, 50.3, listOf("force2D" to "true"))
    val lib3d = libBbox("ev-hist", 8.0, 49.9, 8.7, 50.3)
    val f2d = record(diff("bbox-force2d", hub2d, lib3d))
    row("/bbox clip=true", "GET /bbox?…&clip=true (geometry cut to the box)", "none (no clip option; client must clip, e.g. JTS)", "unsupported", "ids equal=${clipDiff.sameKeys}; clipped vs unclipped geometry: ${clipDiff.categories}; without clip: ${noClipDiff.result}")
    row("/bbox force2D=true", "GET /bbox?…&force2D=true", "none (no option; client drops Z)", "unsupported", "geometry diffs vs hub 2D: ${f2d.categories}")
  }

  @Test
  fun `bbox - with property filter`() {
    val hub = hubOk("/spaces/ev-hist/bbox", raw = "west=0&south=0&east=20&north=60&p.rank=gt=5").features
    // queryMembers cannot be combined with legacy property queries, so the bbox goes through the legacy spatial query.
    val lib = libRead(storage, "ev-hist") {
      query.spatial = SpIntersects(SpBoundingBox(0.0, 0.0, 20.0, 60.0).toPolygon())
      query.properties = PQuery(Property("properties", "rank"), DoubleOp.GT, 5)
    }
    val d = record(diff("bbox-property", hub, lib))
    val mixed = outcome({ l: List<Map<*, *>> -> "${l.size}" }) {
      libRead(storage, "ev-hist") {
        queryMembers = Intersects(XyzMembers.XyzGeometry, SpBoundingBox(0.0, 0.0, 20.0, 60.0).toPolygon())
        query.properties = PQuery(Property("properties", "rank"), DoubleOp.GT, 5)
      }
    }
    observe("bbox-property-mixed", mapOf("modernIntersects+legacyProperties" to mixed))
    assertTrue(d.sameKeys, d.summary())
    row("/bbox + p.*", "GET /bbox?…&p.rank=gt=5", "legacy query.spatial SpIntersects + query.properties (client-side PropertyFilter)", d.result, "${d.summary()}; modern Intersects + legacy property → $mixed")
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Tile

  /** Web-Mercator tile bounds (west, south, east, north) of a quadkey. */
  private fun quadkeyBounds(quadkey: String): DoubleArray {
    var x = 0
    var y = 0
    for (c in quadkey) {
      val d = c - '0'
      x = x * 2 + (d and 1)
      y = y * 2 + (d shr 1)
    }
    val z = quadkey.length
    val n = 1 shl z
    fun lon(px: Int) = px.toDouble() / n * 360.0 - 180.0
    fun lat(py: Int) = Math.toDegrees(atan(kotlin.math.sinh(PI * (1 - 2.0 * py / n))))
    return doubleArrayOf(lon(x), lat(y + 1), lon(x + 1), lat(y))
  }

  @Test
  fun `tile - unsupported, emulated via bbox of the tile`() {
    for (qk in listOf("120203", "1202", "12020")) {
      val hub = hubOk("/spaces/ev-hist/tile/quadkey/$qk").features
      val b = quadkeyBounds(qk)
      val lib = libBbox("ev-hist", b[0], b[1], b[2], b[3])
      val d = record(diff("tile-quadkey-$qk", hub, lib), mapOf("bounds" to b.toList()))
      row("/tile", "GET /tile/quadkey/$qk (n=${hub.size})", "no tile API; client converts the quadkey to a bbox + Intersects", if (d.sameKeys) "unsupported natively; emulation ${d.result}" else "unsupported; emulation diff", d.summary())
    }
    val web = hubOk("/spaces/ev-hist/tile/web/6_33_21").features
    row("/tile", "GET /tile/web/6_33_21 (n=${web.size}), /tile/here/…, .mvt, margin, clustering", "none", "unsupported", "MVT/clustering/viz have no lib-iml counterpart")
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Spatial

  @Test
  fun `spatial - lat lon radius and POST geometry`() {
    val hubRadius = hubOk("/spaces/ev-hist/spatial", listOf("lat" to "50.1", "lon" to "8.5", "radius" to "20000")).features
    val libRadius = libRead(storage, "ev-hist") { queryMembers = Intersects(XyzMembers.XyzGeometry, SpPoint(8.5, 50.1), SpBuffer(20000.0, true)) }
    val r = record(diff("spatial-radius", hubRadius, libRadius))
    assertTrue(r.sameKeys, r.summary())
    row("/spatial", "GET /spatial?lat=50.1&lon=8.5&radius=20000", "Intersects(geo, SpPoint, SpBuffer(20000, geography=true))", r.result, r.summary())

    // Radius at the 20 km boundary: pt-3d (8.6, 50.2) is ~13.3 km away, ln-3d ~ 7 km; a 7.5 km radius splits them.
    for (radius in listOf(7000, 7500, 13000, 14000)) {
      val h = hubOk("/spaces/ev-hist/spatial", listOf("lat" to "50.1", "lon" to "8.5", "radius" to "$radius")).features
      val l = libRead(storage, "ev-hist") { queryMembers = Intersects(XyzMembers.XyzGeometry, SpPoint(8.5, 50.1), SpBuffer(radius.toDouble(), true)) }
      val d = record(diff("spatial-radius-$radius", h, l))
      row("/spatial", "GET /spatial?lat=50.1&lon=8.5&radius=$radius", "Intersects + SpBuffer(geography)", d.result, d.summary())
    }

    val hubPoint = hubOk("/spaces/ev-hist/spatial", listOf("lat" to "50.15", "lon" to "8.55")).features
    val libPoint = libRead(storage, "ev-hist") { queryMembers = Intersects(XyzMembers.XyzGeometry, SpPoint(8.55, 50.15)) }
    val p = record(diff("spatial-point", hubPoint, libPoint))
    row("/spatial", "GET /spatial?lat=50.15&lon=8.55 (no radius)", "Intersects(geo, SpPoint)", p.result, p.summary())

    val polygon = """{"type":"Polygon","coordinates":[[[8.0,49.9],[8.7,49.9],[8.7,50.3],[8.0,50.3],[8.0,49.9]]]}"""
    val hubPost = hubOk("/spaces/ev-hist/spatial", method = "POST", body = polygon).features
    val libPost = libRead(storage, "ev-hist") { queryMembers = Intersects(XyzMembers.XyzGeometry, geometry(polygon)) }
    val pp = record(diff("spatial-post-polygon", hubPost, libPost))
    assertTrue(pp.sameKeys, pp.summary())
    row("/spatial POST", "POST /spatial (Polygon body)", "Intersects(geo, geometry)", pp.result, pp.summary())

    val line = """{"type":"LineString","coordinates":[[9.0,51.0],[9.1,51.1]]}"""
    val hubLine = hubOk("/spaces/ev-hist/spatial", listOf("radius" to "5000"), method = "POST", body = line).features
    val libLine = libRead(storage, "ev-hist") { queryMembers = Intersects(XyzMembers.XyzGeometry, geometry(line), SpBuffer(5000.0, true)) }
    val pl = record(diff("spatial-post-line-radius", hubLine, libLine))
    row("/spatial POST", "POST /spatial?radius=5000 (LineString body)", "Intersects(geo, line, SpBuffer(5000))", pl.result, pl.summary())

    val hubRef = Parity.hub("/spaces/ev-hist/spatial", listOf("refSpaceId" to "ev-hist", "refFeatureId" to "pg-hole"))
    row("/spatial refSpaceId/refFeatureId, h3Index", "GET /spatial?refSpaceId=…&refFeatureId=… → $hubRef", "none (caller loads the feature, then Intersects)", "unsupported", "h3Index: no counterpart")
  }

  private fun geometry(json: String): SpGeometry = (Platform.fromJSON(json) as naksha.base.PlatformMap).proxy(SpGeometry::class)

  // ---------------------------------------------------------------------------------------------------------------
  // Statistics / counts

  @Test
  fun `statistics - count`() {
    for (space in listOf("ev-v1", "ev-hist", "ev-ext", "ev-big", "ev-p2")) {
      val stats = hubOk("/spaces/$space/statistics").json as Map<*, *>
      val count = (stats["count"] as Map<*, *>)["value"] as Number
      val (libCount, ms) = Parity.timed {
        storage.newReadSession().use { s -> (s.executeRead(Parity.request(space)) as SuccessResponse).featureTupleList.size }
      }
      val estimated = storage.newReadSession().use { s ->
        s.getCollectionById(checkNotNull(s.getCatalogById("public")), space)?.estimatedFeatureCount
      }
      observe("statistics-$space", mapOf("hubCount" to count, "hubEstimated" to (stats["count"] as Map<*, *>)["estimated"], "libSelectionCount" to libCount, "libSelectionMs" to ms, "libEstimatedFeatureCount" to estimated))
      row("/statistics", "GET /spaces/$space/statistics count=$count", "no statistics API; count = size of a full selection ($ms ms); estimatedFeatureCount=$estimated",
        if (count.toLong() == libCount.toLong()) "count same (via full scan)" else "diff", "bbox/properties/geometryTypes/min-maxVersion: unsupported")
    }
  }
}
