package naksha.base

import kotlin.test.Test
import kotlin.test.assertEquals

class Int64Test {
    @Test
    fun conversionUsesLong() {
        assertEquals(Long.MAX_VALUE, Platform.toInt64(Long.MAX_VALUE))
        assertEquals(Long.MIN_VALUE, Platform.toInt64(Long.MIN_VALUE.toString()))
        assertEquals(4_294_967_295L, (-1).toUnsignedInt64())
    }

    @Test
    fun atomicUsesLong() {
        val atomic = AtomicInt64(Long.MAX_VALUE)
        assertEquals(Long.MAX_VALUE, atomic.getAndAdd(1))
        assertEquals(Long.MIN_VALUE, atomic.get())
        assertEquals(Long.MIN_VALUE + 1, atomic.addAndGet(1))
        assertEquals(true, atomic.compareAndSet(Long.MIN_VALUE + 1, 42))
        assertEquals(42L, atomic.get())
    }

    @Test
    fun rawBitsRoundTrip() {
        val bits = 0xfff8_0000_0000_0042uL.toLong()
        val value = Platform.toDoubleRawBits(bits)
        assertEquals(bits, Platform.toInt64RawBits(value))
        assertEquals(bits, value.toLongRawBits())
    }

    @Test
    fun binaryPreservesBitsAndByteOrder() {
        val value = 0x0123_4567_89ab_cdefL
        val binary = Binary(16)
        binary.setInt64(0, value)
        binary.setInt64(8, value, littleEndian = true)
        assertEquals(value, binary.getInt64(0))
        assertEquals(value, binary.getInt64(8, littleEndian = true))
        assertEquals(0x01, binary.byteArray[0].toInt())
        assertEquals(0xef, binary.byteArray[8].toInt() and 0xff)
    }
}
