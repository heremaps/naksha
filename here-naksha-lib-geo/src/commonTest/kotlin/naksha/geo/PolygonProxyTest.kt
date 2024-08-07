package naksha.geo

import naksha.base.Platform
import naksha.base.PlatformObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs


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
        assertIs<PlatformObject>(parsedJson)
        val geometry = Platform.proxy(parsedJson, SpPolygon::class)

        // then
        assertEquals(SpType.Polygon.toString(), geometry.type)
        val polygon = geometry.asPolygon()
        assertEquals(1, polygon.getCoordinates().size)
        val polygonCoords = polygon.getCoordinates()
        assertEquals(3, polygonCoords[0]?.size)
        assertEquals(20.6899332428942, polygonCoords[0]?.get(0)?.getLongitude())
    }

}