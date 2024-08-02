package naksha.geo

import naksha.base.Platform
import naksha.base.PlatformMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class FeatureTest {
    @Test
    fun testPolygonFeature() {
        // given
        val polygonJson = """
    {
      "type": "Feature",
      "id": "Example",
      "bbox": [100.0, 0.0, -100.0, 105.0, 1.0, 0.0],
      "geometry": {
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
    }
"""

        val parsedJson = Platform.fromJSON(polygonJson)
        assertIs<PlatformMap>(parsedJson)
        val feature = Platform.proxy(parsedJson, SpFeature::class)
        assertEquals("Feature", feature.type)
        assertEquals("Example", feature.id)
        val bbox = feature.bbox
        assertNotNull(bbox)
        assertEquals(6, bbox.size)
        assertEquals(100.0, bbox[0])
        assertEquals(0.0, bbox[1])
        assertEquals(-100.0, bbox[2])
        assertEquals(105.0, bbox[3])
        assertEquals(1.0, bbox[4])
        assertEquals(0.0, bbox[5])

        val geometry = feature.geometry
        assertEquals(SpType.Polygon.toString(), geometry.type)
        val polygon = geometry.asPolygon()
        assertEquals(1, polygon.getCoordinates().size)
        val polygonCoords = polygon.getCoordinates()
        assertEquals(3, polygonCoords[0]?.size)
        assertEquals(20.6899332428942, polygonCoords[0]?.get(0)?.getLongitude())
    }
}