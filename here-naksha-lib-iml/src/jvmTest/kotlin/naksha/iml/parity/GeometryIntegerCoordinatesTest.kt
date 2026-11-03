package naksha.iml.parity

import naksha.base.Platform
import naksha.base.PlatformMap
import naksha.base.proxy
import naksha.geo.LineStringCoord
import naksha.geo.PointCoord
import naksha.geo.SpGeometry
import naksha.iml.ImlTuple
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test

/**
 * Database-free reproduction of the coordinate loss seen in HubReadbackTest (evaluation finding F1).
 *
 * lib-iml loads geometry as `ST_AsGeoJSON(geo, 15, 0)` text, which prints whole numbers without a decimal point
 * (`[8,50,1]`). `ImlTuple.read` parses it with `Platform.fromJSON(jsonNumbers(...))` (ImlTuple.kt:62), and
 * `jsonNumbers` deliberately keeps representable integers as integers. `SpGeometry.getCoordinates()` converts
 * integers to doubles only in the top-level coordinates list (lib-geo SpGeometry.kt:76-84), so a Point is fine,
 * but in nested lists the `ListProxy<Double>` getters return null for integer elements: `getLongitude()` /
 * `getLatitude()` fall back to 0.0 and `getZ()` returns null.
 *
 * The `characterize` tests assert today's behaviour and pass. The `expected` test asserts correct behaviour, runs
 * only with `IML_PARITY_STRICT=true`, and FAILS until the bug is fixed.
 */
class GeometryIntegerCoordinatesTest {
  private fun parse(json: String): SpGeometry =
    (Platform.fromJSON(ImlTuple.jsonNumbers(json)) as PlatformMap).proxy(SpGeometry::class)

  @Test
  fun `characterize - top-level Point integers are converted`() {
    val point = parse("""{"type":"Point","coordinates":[8,50,1]}""").getCoordinates() as PointCoord
    assertEquals(8.0, point.getLongitude())
    assertEquals(50.0, point.getLatitude())
    assertEquals(1.0, point.getZ())
  }

  @Test
  fun `characterize - nested LineString integers become 0 and Z is lost`() {
    val line = parse("""{"type":"LineString","coordinates":[[8,50,1],[8.5,50.5,2.5]]}""").getCoordinates() as LineStringCoord
    val first = checkNotNull(line[0])
    println("PARITY|geometry-int|first=${first.getLongitude()},${first.getLatitude()},${first.getZ()} second=${line[1]?.getLongitude()},${line[1]?.getLatitude()},${line[1]?.getZ()}")
    assertEquals(0.0, first.getLongitude())
    assertEquals(0.0, first.getLatitude())
    assertEquals(null, first.getZ())
    assertEquals(2.5, line[1]?.getZ())
  }

  @Test
  fun `expected - nested integer coordinates survive (FAILS until fixed)`() {
    assumeTrue(System.getenv("IML_PARITY_STRICT") == "true", "Set IML_PARITY_STRICT=true to run tests that assert the fixed behaviour")
    val line = parse("""{"type":"LineString","coordinates":[[8,50,1],[8.5,50.5,2.5]]}""").getCoordinates() as LineStringCoord
    val first = checkNotNull(line[0])
    assertEquals(listOf(8.0, 50.0, 1.0), listOf(first.getLongitude(), first.getLatitude(), first.getZ()))
  }
}
