package naksha.geo

import kotlin.test.Test
import kotlin.test.assertEquals

class PointProxyTest {

    @Test
    fun testPoint() {
        // given
        val point = PointCoord(1.0, 2.0, 3.0)

        // expect
        assertEquals(1.0, point.longitude)
        assertEquals(2.0, point.latitude)
        assertEquals(3.0, point.z)
    }

}