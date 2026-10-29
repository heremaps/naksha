package naksha.psql

import naksha.base.Platform.PlatformCompanion.swapLong
import naksha.psql.PgUtil.PgUtilCompanion.fnToInt8
import naksha.psql.PgUtil.PgUtilCompanion.int8ToFn
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PgFeatureNumberTest {
    @Test
    fun knownBytesAndExtremesRoundTrip() {
        assertEquals(0x0807060504030201L, swapLong(0x0102030405060708L))
        assertEquals(Long.MIN_VALUE, fnToInt8(0))
        assertEquals(0L, fnToInt8(128))
        for (fn in listOf(Long.MIN_VALUE, Long.MAX_VALUE, -1L, 0L, 1L, 255L, 256L)) {
            assertEquals(fn, int8ToFn(fnToInt8(fn)))
            assertEquals(fn, fnToInt8(int8ToFn(fn)))
        }
        val random = Random(33)
        repeat(10000) {
            val fn = random.nextLong()
            assertEquals(fn, int8ToFn(fnToInt8(fn)))
        }
    }

    @Test
    fun physicalOrderFollowsAllRoutingBytesForPositiveAndNegativeFeatures() {
        for (upperBits in listOf(0L, Long.MIN_VALUE, -256L, 0x123456789abcde00L)) {
            var previous: Long? = null
            for (byte in 0..255) {
                val logical = upperBits or byte.toLong()
                val stored = fnToInt8(logical)
                assertEquals(byte, ((stored xor Long.MIN_VALUE) ushr 56).toInt())
                assertEquals(logical, int8ToFn(stored))
                previous?.let { assertTrue(it < stored) }
                previous = stored
            }
        }
    }

    @Test
    fun everyPartitionCountCoversEveryRoutingByteExactlyOnce() {
        for (count in 1..256) {
            for (byte in 0..255) {
                val matching = (0 until count).filter { slot ->
                    byte >= slot * 256 / count && byte < (slot + 1) * 256 / count
                }
                assertEquals(1, matching.size)
                val slot = matching.single()
                val lower = slot * 256 / count
                val upper = (slot + 1) * 256 / count
                for (tail in listOf(0L, 0x00ffffffffffffffL)) {
                    val stored = ((byte.toLong() shl 56) or tail) xor Long.MIN_VALUE
                    assertTrue(stored >= ((lower.toLong() shl 56) xor Long.MIN_VALUE))
                    if (upper < 256) assertTrue(stored < ((upper.toLong() shl 56) xor Long.MIN_VALUE))
                    assertEquals(stored, fnToInt8(int8ToFn(stored)))
                }
            }
        }
    }
}
