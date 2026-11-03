package naksha.iml.parity

import naksha.base.Platform
import naksha.base.PlatformMap
import naksha.base.proxy
import naksha.geo.SpBoundingBox
import naksha.geo.SpGeometry
import naksha.iml.ImlConfig
import naksha.iml.ImlStorage
import naksha.model.Naksha
import naksha.model.objects.StandardMembers
import naksha.model.objects.XyzMembers
import naksha.model.request.SuccessResponse
import naksha.model.request.ops.Equals
import naksha.model.request.ops.Intersects
import naksha.iml.parity.P3.case
import naksha.iml.parity.P3.describe
import naksha.iml.parity.Parity.diff
import naksha.iml.parity.Parity.observe
import naksha.iml.parity.Parity.record
import naksha.iml.parity.Parity.request
import naksha.iml.parity.Parity.timed
import naksha.iml.parity.Parity.toMaps
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

/**
 * Phase 3, item "large geometries, invalid geometries, GeometryCollection, 3D vs 2D".
 * Uses `ev-p3-geo` from `scripts/seed-p3.py`. The Hub rejects GeometryCollection on write (seed log), so GeometryCollection rows
 * (`g-gc-*`) are inserted directly into the layer table here, the way another writer of the same DB could (version from the layer's
 * `_version_seq`). They are removed again in @AfterAll.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@org.junit.jupiter.api.Timeout(value = 10, unit = java.util.concurrent.TimeUnit.MINUTES)
class P3GeometryTest {
  private lateinit var config: ImlConfig
  private lateinit var storage: ImlStorage
  private lateinit var table: String
  private val space = "ev-p3-geo"
  private val gcIds = listOf("g-gc-mixed-db", "g-gc-nested-db", "g-gc-empty-db")

  @BeforeAll
  fun setUp() {
    Parity.assumeEnabled()
    table = P3.table(space) ?: error("seed ev-p3-geo first (scripts/seed-p3.py)")
    val gcs = mapOf(
      "g-gc-mixed-db" to """{"type":"GeometryCollection","geometries":[{"type":"Point","coordinates":[48.1,0.1,3.5]},{"type":"LineString","coordinates":[[48.2,0.2],[48.3,0.3]]},{"type":"Polygon","coordinates":[[[48.4,0.4],[48.6,0.4],[48.6,0.6],[48.4,0.4]]]}]}""",
      "g-gc-nested-db" to """{"type":"GeometryCollection","geometries":[{"type":"GeometryCollection","geometries":[{"type":"Point","coordinates":[48.7,0.7]}]},{"type":"Point","coordinates":[48.8,0.8]}]}""",
      "g-gc-empty-db" to """{"type":"GeometryCollection","geometries":[]}"""
    )
    P3.execute("DELETE FROM public.\"$table\" WHERE id = ANY(string_to_array(?, ','))", gcIds.joinToString(","))
    for ((id, gj) in gcs) {
      P3.execute(
        "INSERT INTO public.\"$table\" (id, version, next_version, operation, author, jsondata, geo) " +
          "SELECT ?, nextval('public.\"${table}_version_seq\"'), 9223372036854775807, 'I', 'ANONYMOUS', " +
          "jsonb_build_object('id', ?::text, 'type', 'Feature', 'properties', jsonb_build_object('name', ?::text, '@ns:com:here:xyz', jsonb_build_object('author', 'ANONYMOUS', 'createdAt', 1, 'updatedAt', 1))), " +
          "ST_Force3D(ST_SetSRID(ST_GeomFromGeoJSON(?), 4326))", id, id, id, gj)
    }
    val (c, s) = Parity.open("iml-eval-p3-geo")
    config = c
    storage = s
  }

  @AfterAll
  fun tearDown() {
    if (::table.isInitialized) runCatching { P3.execute("DELETE FROM public.\"$table\" WHERE id = ANY(string_to_array(?, ','))", gcIds.joinToString(",")) }
    if (::config.isInitialized) Naksha.removeStorage(config)
  }

  private fun geometry(json: String): SpGeometry = (Platform.fromJSON(json) as PlatformMap).proxy(SpGeometry::class)

  private fun libById(id: String): List<Map<*, *>> = Parity.libRead(storage, space) { queryMembers = Equals(StandardMembers.Id, id) }

  private fun hubById(id: String) = Parity.hub("/spaces/$space/features", listOf("id" to id))

  @Test
  fun `per-feature geometry parity`() {
    val ids = (P3.db { c -> c.createStatement().use { st -> st.executeQuery("SELECT id FROM public.\"$table\" WHERE next_version = 9223372036854775807 ORDER BY id").use { r -> generateSequence { if (r.next()) r.getString(1) else null }.toList() } } })
    val perFeature = LinkedHashMap<String, Any?>()
    for (id in ids) {
      Naksha.cache.clear(storage)
      val (hub, hubMs) = timed { hubById(id) }
      val (lib, libMs) = timed { runCatching { libById(id) } }
      val libList = lib.getOrNull()
      if (libList == null) {
        perFeature[id] = mapOf("hub" to hub.toString(), "lib" to describe(lib.exceptionOrNull()!!))
        continue
      }
      val d = diff("geo-$id", hub.features, libList)
      val libGeom = libList.firstOrNull()?.get("geometry") as Map<*, *>?
      val hubGeom = hub.features.firstOrNull()?.get("geometry") as Map<*, *>?
      fun firstCoord(g: Map<*, *>?): Any? {
        var c: Any? = g?.get("coordinates") ?: (g?.get("geometries") as List<*>?)?.firstOrNull()?.let { (it as Map<*, *>)["coordinates"] }
        while (c is List<*> && c.firstOrNull() is List<*>) c = c.first()
        return c
      }
      perFeature[id] = linkedMapOf(
        "result" to d.result, "categories" to d.categories, "hubStatus" to hub.status, "hubMs" to hubMs, "libMs" to libMs,
        "hubType" to hubGeom?.get("type"), "libType" to libGeom?.get("type"),
        "hubFirstPosition" to firstCoord(hubGeom), "libFirstPosition" to firstCoord(libGeom),
        "firstFieldDiffs" to d.fields.take(3).map { "${it.path}: hub ${it.hub} lib ${it.lib} (${it.category})" }
      )
      record(d, mapOf("hubMs" to hubMs, "libMs" to libMs))
    }
    observe("geo-per-feature", perFeature)
    fun r(id: String) = (perFeature[id] as Map<*, *>?)?.let { "${it["result"]} ${it["categories"]}; hub ${it["hubFirstPosition"]} lib ${it["libFirstPosition"]}" } ?: "n/a"
    fun t(id: String) = (perFeature[id] as Map<*, *>?)?.let { "hub ${it["hubMs"]} ms, lib ${it["libMs"]} ms" } ?: "n/a"
    case("5 geometry", "100 000-vertex polygon and 500 000-vertex line (by id)",
      "Hub: returned as stored",
      "polygon: ${r("g-big-poly-100k")} (${t("g-big-poly-100k")}); line: ${r("g-huge-line-500k")} (${t("g-huge-line-500k")})",
      "High (F1) wherever a coordinate is whole-number; otherwise exact. Size itself is no problem")
    case("5 geometry", "2D input (stored as GeometryZ with z=0): g-line-2d, g-point-2d",
      "Hub: returns what ST_AsGeoJSON gives for the stored Z",
      "line: ${r("g-line-2d")}; point: ${r("g-point-2d")}",
      "High (F1): z=0 is dropped for lines/polygons but kept for points, so 2D vs 3D output depends on the geometry type")
    case("5 geometry", "3D with fractional / negative Z, 3D polygon, mixed 2D/3D positions",
      "Hub: Z preserved",
      "line: ${r("g-line-3d-frac")}; polygon: ${r("g-poly-3d")}; mixed: ${r("g-mixed-dim-line")}",
      "see result (fractional Z survives; whole-number Z is F1)")
    case("5 geometry", "whole-number coordinates, MultiPolygon with hole, antimeridian line",
      "Hub: exact",
      "whole: ${r("g-line-whole")}; multipolygon: ${r("g-mpoly-hole")}; antimeridian: ${r("g-antimeridian-line")}",
      "High (F1)")
    case("5 geometry", "invalid but stored geometries (bow-tie, spike; Hub accepts them on write)",
      "Hub: returns them unchanged",
      "bowtie: ${r("g-bowtie")}; spike: ${r("g-spike")}",
      "see result")
    case("5 geometry", "GeometryCollection rows written directly into the table (Hub rejects GeometryCollection on write: 400 'GeometryCollection is not supported')",
      "Hub read of such rows: ${gcIds.joinToString { id -> "$id ${(perFeature[id] as Map<*, *>?)?.get("hubStatus")}" }}",
      "mixed: ${r("g-gc-mixed-db")}; nested: ${r("g-gc-nested-db")}; empty: ${r("g-gc-empty-db")}",
      "see result")
  }

  @Test
  fun `spatial queries over invalid and special geometries`() {
    val o = LinkedHashMap<String, Any?>()
    fun libBox(w: Double, s: Double, e: Double, n: Double) = try {
      "ok " + Parity.ids(Parity.libRead(storage, space) { queryMembers = Intersects(XyzMembers.XyzGeometry, SpBoundingBox(w, s, e, n).toPolygon()) })
    } catch (ex: Throwable) { describe(ex) }
    fun hubBox(w: Double, s: Double, e: Double, n: Double) = Parity.hub("/spaces/$space/bbox", listOf("west" to "$w", "south" to "$s", "east" to "$e", "north" to "$n")).let {
      if (it.status == 200) "ok " + Parity.ids(it.features) else it.toString()
    }
    for ((name, box) in mapOf(
      "bowtie+spike" to listOf(49.5, -0.5, 55.5, 2.5), "bowtie centre" to listOf(50.45, 0.45, 50.55, 0.55),
      "GeometryCollection" to listOf(48.0, 0.0, 49.0, 1.0), "antimeridian east part" to listOf(179.0, 4.0, 180.0, 6.0),
      "big polygon interior" to listOf(39.9, -0.1, 40.1, 0.1)
    )) {
      o[name] = mapOf("hub" to hubBox(box[0], box[1], box[2], box[3]), "lib" to libBox(box[0], box[1], box[2], box[3]))
    }
    val bowtie = """{"type":"Polygon","coordinates":[[[50.0,0.0],[51.0,1.0],[51.0,0.0],[50.0,1.0],[50.0,0.0]]]}"""
    o["query with invalid (bow-tie) polygon"] = mapOf(
      "hub" to Parity.hub("/spaces/$space/spatial", method = "POST", body = bowtie).let { if (it.status == 200) "ok " + Parity.ids(it.features) else it.toString() },
      "lib" to try { "ok " + Parity.ids(Parity.libRead(storage, space) { queryMembers = Intersects(XyzMembers.XyzGeometry, geometry(bowtie)) }) } catch (e: Throwable) { describe(e) }
    )
    val gc = """{"type":"GeometryCollection","geometries":[{"type":"Point","coordinates":[48.1,0.1]}]}"""
    o["query with a GeometryCollection"] = mapOf(
      "hub" to Parity.hub("/spaces/$space/spatial", method = "POST", body = gc).let { if (it.status == 200) "ok " + Parity.ids(it.features) else it.toString() },
      "lib" to try { "ok " + Parity.ids(Parity.libRead(storage, space) { queryMembers = Intersects(XyzMembers.XyzGeometry, geometry(gc)) }) } catch (e: Throwable) { describe(e) }
    )
    observe("geo-spatial-queries", o)
    for ((k, v) in o) {
      val m = v as Map<*, *>
      val same = m["hub"] == m["lib"]
      case("5 geometry", "spatial query: $k", "Hub: ${m["hub"]}", "lib: ${m["lib"]}", if (same) "same" else "diff (see report)")
    }
  }

  @Test
  fun `materialization cost of large geometries`() {
    val o = LinkedHashMap<String, Any?>()
    for (id in listOf("g-big-poly-100k", "g-huge-line-500k")) {
      Naksha.cache.clear(storage)
      val (tuple, ms) = timed {
        storage.newReadSession().use { s ->
          val r = s.executeRead(request(space) { queryMembers = Equals(StandardMembers.Id, id) }) as SuccessResponse
          r.featureTupleList.loadAll()
          r.featureTupleList.first()!!.tuple!!
        }
      }
      val (_, decodeMs) = timed { tuple.decodeFeature(null) }
      val hub = timed { hubById(id) }
      o[id] = mapOf("libLoadMs" to ms, "tupleBytes" to tuple.featureBytes.size, "decodeMs" to decodeMs, "hubMs" to hub.second, "hubBodyBytes" to hub.first.body.length,
        "dbGeoBytes" to P3.scalar("SELECT pg_column_size(geo) FROM public.\"$table\" WHERE id = ? AND next_version = 9223372036854775807", id))
    }
    observe("geo-large-cost", o)
    case("5 geometry", "materializing the 100k polygon and the 500k line",
      "Hub: GeoJSON streamed from the DB",
      o.entries.joinToString("; ") { "${it.key} ${it.value}" },
      "Low: works; cost is linear (JSON parse + JBON/TWKB encode, then decode)")
  }
}
