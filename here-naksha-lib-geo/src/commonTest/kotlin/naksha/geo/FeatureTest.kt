package naksha.geo

import naksha.base.Platform.PlatformCompanion.fromJSON
import kotlin.test.*

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
              20.6899332,
               6.6805668
            ],
            [
              20.5619886,
               4.7968097
            ],
            [
              20.6899332,
               6.6805668
            ]
          ]
        ]
      }
    }
"""

        val feature = assertNotNull(fromJSON(polygonJson, GeoFeature.TYPE))
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

        val polygon = assertIs<SpPolygon>(feature.geometry)
        val polygonCoords = polygon.coordinates
        assertEquals(1, polygon.coordinates.size)
        val polyCoords = assertNotNull(polygonCoords[0])
        assertEquals(3, polyCoords.size)
        val p = assertNotNull(polyCoords[0])
        assertEquals(20.6899332, p.longitude)
        assertEquals(6.6805668, p.latitude)
    }
}