package naksha.base

import naksha.base.PlatformUtil.PlatformUtilCompanion.round_double
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class PlatformUtilTest {
    @Test
    fun without_round_double() {
        val a = 0.1234567
        val b = -100.0
        val c = +100.0
        val r1 = a + (b + c)
        val r2 = (a + b) + c
        assertNotEquals(0.0, r2 - r1 )
    }

    @Test
    fun with_round_double() {
        val a = 0.1234567
        val b = -100.0
        val c = +100.0
        val r1 = round_double( a + round_double(b + c) )
        val r2 = round_double( round_double(a + b) + c )
        assertEquals(0.0, r2 - r1 )
    }
}