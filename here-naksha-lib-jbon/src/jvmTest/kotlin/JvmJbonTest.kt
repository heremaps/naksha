import com.here.naksha.lib.jbon.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

class JvmJbonTest {
    @Test
    fun basicTest() {
        val view = JvmPlatform.dataViewOf(ByteArray(256))
        assertNotNull(view)
        view.setInt32(0, 12345678)
        assertEquals(12345678, view.getInt32(0))
        assertNotEquals(12345678, view.getInt32(0, true))
    }

    @Test
    fun pureTest() {
        val expected: Byte = -1
        val intValue: Int = 255
        assertEquals(expected, (intValue and 0xff).toByte())
    }

    @Test
    fun testNull() {
        val view = JvmPlatform.dataViewOf(ByteArray(256))
        val builder = JbonBuilder(view)
        builder.writeNull()
        assertEquals(TYPE_NULL, view.getInt8(0).toInt())
        assertEquals(1, builder.clear())
    }

    @Test
    fun testUndefined() {
        val view = JvmPlatform.dataViewOf(ByteArray(256))
        val builder = JbonBuilder(view)
        builder.writeUndefined()
        assertEquals(TYPE_UNDEFINED, view.getInt8(0).toInt())
        assertEquals(1, builder.clear())
    }

    @Test
    fun testBoolean() {
        val view = JvmPlatform.dataViewOf(ByteArray(256))
        val builder = JbonBuilder(view)
        builder.writeBool(true)
        assertEquals(TYPE_BOOL_TRUE, view.getInt8(0).toInt())
        assertEquals(1, builder.clear())
        builder.writeBool(false)
        assertEquals(TYPE_BOOL_FALSE, view.getInt8(0).toInt())
        assertEquals(1, builder.clear())
    }

    @Test
    fun testIntEncoding() {
        val view = JvmPlatform.dataViewOf(ByteArray(256))
        val builder = JbonBuilder(view)
        // the values -8 to 7 should be encoded in one byte
        builder.writeInt(-8);
        assertEquals(1, builder.clear())
        builder.writeInt(7);
        assertEquals(1, builder.clear())
        // the values below -8 and above 7 should be encoded in two byte
        builder.writeInt(-9);
        assertEquals(-9, view.getInt8(1))
        assertEquals(2, builder.clear())
        builder.writeInt(8);
        assertEquals(8, view.getInt8(1))
        assertEquals(2, builder.clear())
        // a value less than -128 must be stored in three byte
        builder.writeInt(-129)
        assertEquals(-129, view.getInt16(1))
        assertEquals(3, builder.clear())
        // a value bigger than 127 must be stored in three byte
        builder.writeInt(128)
        assertEquals(128, view.getInt16(1))
        assertEquals(3, builder.clear())
        // a value less than -32768 must be stored in five byte
        builder.writeInt(-32769)
        assertEquals(-32769, view.getInt32(1))
        assertEquals(5, builder.clear())
        // a value bigger than 32767 must be stored in three byte
        builder.writeInt(32768)
        assertEquals(32768, view.getInt32(1))
        assertEquals(5, builder.clear())
    }

    @Test
    fun testFloat32Encoding() {
        val view = JvmPlatform.dataViewOf(ByteArray(256))
        val builder = JbonBuilder(view)
        // values -8 to 7 should be encoded in one byte
        for (i in 0..15) {
            val value = TINY_FLOATS[i]
            builder.writeFloat(value)
            assertEquals(TYPE_FLOAT4 xor i, view.getInt8(0).toInt() and 0xff)
            assertEquals(1, builder.clear())
        }
        // all other values are encoded in 5 byte
        builder.writeFloat(1.25f)
        assertEquals(TYPE_FLOAT32, view.getInt8(0).toInt() and 0xff)
        assertEquals(1.25f, view.getFloat32(1))
        assertEquals(5, builder.clear())
    }

    @Test
    fun testFloat64Encoding() {
        val view = JvmPlatform.dataViewOf(ByteArray(256))
        val builder = JbonBuilder(view)
        // values -8 to 7 should be encoded in one byte
        for (i in 0..15) {
            val value = TINY_DOUBLES[i]
            builder.writeDouble(value)
            assertEquals(TYPE_FLOAT4 xor i, view.getInt8(0).toInt() and 0xff)
            assertEquals(1, builder.clear())
        }
        // all other values are encoded in 5 byte
        builder.writeDouble(1.25)
        assertEquals(TYPE_FLOAT64, view.getInt8(0).toInt() and 0xff)
        assertEquals(1.25, view.getFloat64(1))
        assertEquals(9, builder.clear())
    }

    @Test
    fun testSize32Encoding() {
        val view = JvmPlatform.dataViewOf(ByteArray(256))
        val builder = JbonBuilder(view)
        // a value between 0 and (including) 10 must result in one byte
        builder.writeSize32(0);
        assertEquals(TYPE_SIZE32, view.getInt8(0).toInt() and 0xff)
        assertEquals(1, builder.clear())
        builder.writeSize32(11);
        assertEquals(11, (view.getInt8(0).toInt() and 0xff) - TYPE_SIZE32)
        assertEquals(1, builder.clear())
        // a value between 12 and 267 must be encoded in two byte
        builder.writeSize32(12);
        assertEquals(TYPE_SIZE32 xor 12, view.getInt8(0).toInt() and 0xff)
        assertEquals(0, view.getInt8(1))
        assertEquals(2, builder.clear())
        builder.writeSize32(267);
        assertEquals(TYPE_SIZE32 xor 12, view.getInt8(0).toInt() and 0xff)
        assertEquals((267 - 12).toByte(), view.getInt8(1))
        assertEquals(2, builder.clear())
        // a value between 268 and 65535 must be encoded in three byte
        builder.writeSize32(268);
        assertEquals(TYPE_SIZE32 xor 13, view.getInt8(0).toInt() and 0xff)
        assertEquals(268, view.getInt16(1))
        assertEquals(3, builder.clear())
        builder.writeSize32(65535);
        assertEquals(TYPE_SIZE32 xor 13, view.getInt8(0).toInt() and 0xff)
        assertEquals(65535.toShort(), view.getInt16(1))
        assertEquals(3, builder.clear())
        // a value above 65535 must be encoded in five byte
        builder.writeSize32(65536);
        assertEquals(TYPE_SIZE32 xor 15, view.getInt8(0).toInt() and 0xff)
        assertEquals(65536, view.getInt32(1))
        assertEquals(5, builder.clear())
        builder.writeSize32(Integer.MAX_VALUE);
        assertEquals(TYPE_SIZE32 xor 15, view.getInt8(0).toInt() and 0xff)
        assertEquals(Integer.MAX_VALUE, view.getInt32(1))
        assertEquals(5, builder.clear())
    }
}