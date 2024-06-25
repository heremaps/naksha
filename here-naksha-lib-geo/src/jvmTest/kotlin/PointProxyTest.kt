import naksha.geo.PointCoordsProxy
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class PointProxyTest {

    @Test
    fun testPoint() {
        // given
        val point = PointCoordsProxy(1.0, 2.0, 3.0)

        // expect
        assertEquals(1.0, point.getLongitude())
        assertEquals(2.0, point.getLatitude())
        assertEquals(3.0, point.getAltitude())
    }

}