package naksha.base

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class PlatformSwapTest {
    @Test
    fun swapsKnownBytePatterns() {
        assertEquals(0x0807060504030201L, Platform.swapLong(0x0102030405060708L))
        assertEquals(0x04030201, Platform.swapInt(0x01020304))
        assertEquals(0x0201.toShort(), Platform.swapShort(0x0102.toShort()))
        assertEquals(128L, Platform.swapLong(Long.MIN_VALUE))
        assertEquals(128, Platform.swapInt(Int.MIN_VALUE))
        assertEquals(128.toShort(), Platform.swapShort(Short.MIN_VALUE))
        assertEquals(-1L, Platform.swapLong(-1L))
        assertEquals(-1, Platform.swapInt(-1))
        assertEquals((-1).toShort(), Platform.swapShort((-1).toShort()))
    }

    @Test
    fun swapsMatchAnIndependentReferenceAndAreReversible() {
        val random = Random(33)
        repeat(10000) {
            val value = random.nextLong()
            var expected = 0L
            for (byte in 0..7) expected = (expected shl 8) or ((value ushr (8 * byte)) and 255L)
            assertEquals(expected, Platform.swapLong(value))
            assertEquals(value, Platform.swapLong(Platform.swapLong(value)))
            val int = value.toInt()
            assertEquals((expected ushr 32).toInt(), Platform.swapInt(int))
            assertEquals(int, Platform.swapInt(Platform.swapInt(int)))
            val short = value.toShort()
            assertEquals((expected ushr 48).toShort(), Platform.swapShort(short))
            assertEquals(short, Platform.swapShort(Platform.swapShort(short)))
        }
    }

    @Test
    fun togglesOnlySignBitsForAllIntegerWidths() {
        assertEquals(Long.MIN_VALUE, PlatformUtil.swapSign(0L))
        assertEquals(Int.MIN_VALUE, PlatformUtil.swapSign(0))
        assertEquals(Short.MIN_VALUE, PlatformUtil.swapSign(0.toShort()))
        assertEquals(Byte.MIN_VALUE, PlatformUtil.swapSign(0.toByte()))
        assertEquals(Long.MAX_VALUE, PlatformUtil.swapSign(-1L))
        assertEquals(Int.MAX_VALUE, PlatformUtil.swapSign(-1))
        for (value in Short.MIN_VALUE..Short.MAX_VALUE) {
            val short = value.toShort()
            assertEquals(short, PlatformUtil.swapSign(PlatformUtil.swapSign(short)))
            assertEquals(value and 0x7fff, PlatformUtil.swapSign(short).toInt() and 0x7fff)
        }
        for (value in Byte.MIN_VALUE..Byte.MAX_VALUE) {
            val byte = value.toByte()
            assertEquals(byte, PlatformUtil.swapSign(PlatformUtil.swapSign(byte)))
            assertEquals(value and 0x7f, PlatformUtil.swapSign(byte).toInt() and 0x7f)
        }
    }
}
