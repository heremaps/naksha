import naksha.base.JvmMap
import naksha.base.Platform
import naksha.geo.PolygonProxy
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals


class PolygonProxyTest {


    @Test
    fun testPolygon() {
        // given
        val polygon = """
      {
        "type": "Polygon",
        "coordinates": [
          [
            [
              20.6899332428942,
              6.680566836459747
            ],
            [
              20.5619886821394,
              4.796809744839422
            ],
            [
              22.56000370152873,
              4.42289603090056
            ],
            [
              22.07061483015346,
              6.681892425334823
            ],
            [
              20.6899332428942,
              6.680566836459747
            ]
          ]
        ]
      }
        """.trimIndent()

        // when
        val parsedJson = Platform.fromJSON(polygon)
        val polygonProxy = (parsedJson as JvmMap).proxy(PolygonProxy::class)

        // then
        assertEquals("Polygon", polygonProxy.type)
        val coordinates = polygonProxy.coordinates
        assertEquals(1, coordinates?.size)
        val lineString = coordinates?.get(0)
        assertEquals(5, lineString?.size)
        val lastPosition = lineString?.get(4)
        assertEquals(2, lastPosition?.size)
        assertEquals(20.6899332428942, lastPosition?.getLongitude())
        assertEquals(6.680566836459747, lastPosition?.getLatitude())
    }
}