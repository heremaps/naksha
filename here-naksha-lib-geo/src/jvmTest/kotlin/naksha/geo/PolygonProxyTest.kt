package naksha.geo

import naksha.base.JvmMap
import naksha.base.Platform
import naksha.geo.cords.PolygonCoordsProxy
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals


class PolygonProxyTest {


    @Test
    fun testPolygon() {
        // given
        val polygonJson = """
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
              20.6899332428942,
              6.680566836459747
            ]
          ]
        ]
      }
        """.trimIndent()

        // when
        val parsedJson = Platform.fromJSON(polygonJson)
        val geometryProxy = (parsedJson as JvmMap).proxy(PolygonProxy::class)

        // then
        assertEquals("Polygon", geometryProxy.type)
        val polygon = geometryProxy.proxy(PolygonProxy::class)
        assertEquals(1, polygon.coordinates?.size)
        val polygonCoords = polygon.coordinates!!.proxy(PolygonCoordsProxy::class)
        assertEquals(3, polygonCoords[0]?.size)
        assertEquals(20.6899332428942, polygonCoords[0]?.get(0)?.getLongitude())
    }

}