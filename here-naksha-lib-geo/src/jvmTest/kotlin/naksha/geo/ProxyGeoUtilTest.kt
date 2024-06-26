package naksha.geo

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ProxyGeoUtilTest {

    @Test
    fun testEnvelope(){
        // given
        val x1 = 1.0
        val y1 = 1.1
        val x2 = 2.0
        val y2 = 2.1

        // when
        val polygon = ProxyGeoUtil.createBBoxEnvelope(x1, y1, x2, y2)

        // then
        val coords =polygon.coordinates!![0]!!

        assertEquals(1.0, coords[0]!!.getLongitude())
        assertEquals(1.1, coords[0]!!.getLatitude())

        assertEquals(2.0, coords[1]!!.getLongitude())
        assertEquals(1.1, coords[1]!!.getLatitude())

        assertEquals(2.0, coords[2]!!.getLongitude())
        assertEquals(2.1, coords[2]!!.getLatitude())

        assertEquals(1.0, coords[3]!!.getLongitude())
        assertEquals(2.1, coords[3]!!.getLatitude())

        assertEquals(1.0, coords[4]!!.getLongitude())
        assertEquals(1.1, coords[4]!!.getLatitude())
    }
}