package naksha.geo.naksha.geo

import naksha.geo.SpPoint
import kotlin.test.Test
import kotlin.test.assertEquals

class SpGeometryTest {

    @Test
    fun shouldConvertPoint(){
        // Given:
        val point = SpPoint()
        point.latitude = 1.0
        point.longitude = 2.0
        point.z = 3.0

        // When:
        val asPoint = point.asPoint()

        // Then
        assertEquals(point.latitude, asPoint.latitude)
        assertEquals(point.longitude, asPoint.longitude)
        assertEquals(point.z, asPoint.z)

        // And
        val coords = asPoint.getCoordinates()
        assertEquals(asPoint.latitude, coords.getLatitude())
        assertEquals(asPoint.longitude, coords.getLongitude())
        assertEquals(asPoint.z, coords.getZ())
    }
}