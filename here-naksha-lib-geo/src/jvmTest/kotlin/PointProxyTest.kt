import naksha.geo.PointProxy
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class PointProxyTest {

    @Test
    fun testPoint() {
        // given
        val point = PointProxy(1.0, 2.0, 3.0)

        // expect
        assertEquals(1.0, point.coordinates?.getLongitude())
        assertEquals(2.0, point.coordinates?.getLatitude())
        assertEquals(3.0, point.coordinates?.getAltitude())
    }

}