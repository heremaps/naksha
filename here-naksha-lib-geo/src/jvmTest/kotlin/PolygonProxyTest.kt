import naksha.base.JvmMap
import naksha.base.Platform
import naksha.geo.GeometryProxy
import naksha.geo.PolygonCoordsProxy
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
        val geometryProxy = (parsedJson as JvmMap).proxy(GeometryProxy::class)

        // then
        assertEquals("Polygon", geometryProxy.type)
        assertEquals(1, geometryProxy.coordinates?.size)
        val polygonCoords = geometryProxy.coordinates!!.proxy(PolygonCoordsProxy::class)
        assertEquals(5, polygonCoords[0]?.size)
        assertEquals(20.6899332428942, polygonCoords[0]?.get(0)?.getLongitude())
    }
}