package naksha.geo

import naksha.base.Platform.PlatformCompanion.fromJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull


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
      }"""

        // when
        val polygon = assertNotNull(fromJson(polygonJson, SpPolygon.TYPE))

        // then
        assertEquals(SpPolygon.TYPE.jsonType, polygon.type)
        assertEquals(1, polygon.coordinates.size)
        val polygonCoords = polygon.coordinates
        val linearRingCoord = assertNotNull(polygonCoords[0])
        assertEquals(3, linearRingCoord.size)
        val p = assertNotNull(linearRingCoord[0])
        assertEquals(20.6899332428942, p.longitude)
    }

}