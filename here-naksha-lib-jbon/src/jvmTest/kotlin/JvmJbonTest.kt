import com.here.naksha.lib.jbon.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
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
        val intValue = 255
        assertEquals(expected, (intValue and 0xff).toByte())
    }

    @Test
    fun testNull() {
        val view = JvmPlatform.dataViewOf(ByteArray(256))
        val builder = JbonBuilder(view)
        val reader = Jbon(view)
        builder.writeNull()
        assertEquals(TYPE_NULL, view.getInt8(0).toInt())
        assertEquals(TYPE_NULL, reader.type())
        assertTrue(reader.isNull())
        assertNull(reader.getBoolean())
        assertEquals(1, reader.size())
        assertEquals(1, builder.clear())
    }

    @Test
    fun testUndefined() {
        val view = JvmPlatform.dataViewOf(ByteArray(256))
        val builder = JbonBuilder(view)
        val reader = Jbon(view)
        builder.writeUndefined()
        assertEquals(TYPE_UNDEFINED, view.getInt8(0).toInt())
        assertEquals(TYPE_UNDEFINED, reader.type())
        assertTrue(reader.isUndefined())
        assertNull(reader.getBoolean())
        assertEquals(1, reader.size())
        assertEquals(1, builder.clear())
    }

    @Test
    fun testBoolean() {
        val view = JvmPlatform.dataViewOf(ByteArray(256))
        val builder = JbonBuilder(view)
        val reader = Jbon(view)
        builder.writeBool(true)
        assertEquals(TYPE_BOOL_TRUE, view.getInt8(0).toInt())
        assertEquals(TYPE_BOOL_TRUE, reader.type())
        assertTrue(reader.isBool())
        assertEquals(true, reader.getBoolean())
        assertEquals(1, reader.size())
        assertEquals(1, builder.clear())

        builder.writeBool(false)
        assertEquals(TYPE_BOOL_FALSE, view.getInt8(0).toInt())
        assertEquals(TYPE_BOOL_FALSE, reader.type())
        assertTrue(reader.isBool())
        assertEquals(false, reader.getBoolean())
        assertEquals(1, reader.size())
        assertEquals(1, builder.clear())
    }

    @Test
    fun testIntEncoding() {
        val view = JvmPlatform.dataViewOf(ByteArray(256))
        val builder = JbonBuilder(view)
        val reader = Jbon(view)
        // the values -8 to 7 should be encoded in one byte
        builder.writeInt(-8);
        assertTrue(reader.isInt())
        assertEquals(-8, reader.getInt(0))
        assertEquals(1, reader.size())
        assertEquals(1, builder.clear())

        builder.writeInt(7);
        assertTrue(reader.isInt())
        assertEquals(7, reader.getInt(0))
        assertEquals(1, reader.size())
        assertEquals(1, builder.clear())

        // the values below -8 and above 7 should be encoded in two byte
        builder.writeInt(-9);
        assertEquals(-9, view.getInt8(1))
        assertTrue(reader.isInt())
        assertEquals(-9, reader.getInt(0))
        assertEquals(2, reader.size())
        assertEquals(2, builder.clear())

        builder.writeInt(8);
        assertEquals(8, view.getInt8(1))
        assertTrue(reader.isInt())
        assertEquals(8, reader.getInt(0))
        assertEquals(2, reader.size())
        assertEquals(2, builder.clear())

        // a value less than -128 must be stored in three byte
        builder.writeInt(-129)
        assertEquals(-129, view.getInt16(1))
        assertTrue(reader.isInt())
        assertEquals(-129, reader.getInt(0))
        assertEquals(3, reader.size())
        assertEquals(3, builder.clear())

        // a value bigger than 127 must be stored in three byte
        builder.writeInt(128)
        assertEquals(128, view.getInt16(1))
        assertTrue(reader.isInt())
        assertEquals(128, reader.getInt(0))
        assertEquals(3, reader.size())
        assertEquals(3, builder.clear())

        // a value less than -32768 must be stored in five byte
        builder.writeInt(-32769)
        assertEquals(-32769, view.getInt32(1))
        assertTrue(reader.isInt())
        assertEquals(-32769, reader.getInt(0))
        assertEquals(5, reader.size())
        assertEquals(5, builder.clear())

        // a value bigger than 32767 must be stored in three byte
        builder.writeInt(32768)
        assertEquals(32768, view.getInt32(1))
        assertTrue(reader.isInt())
        assertEquals(32768, reader.getInt(0))
        assertEquals(5, reader.size())
        assertEquals(5, builder.clear())
    }

    @Test
    fun testFloat32Encoding() {
        val view = JvmPlatform.dataViewOf(ByteArray(256))
        val builder = JbonBuilder(view)
        val reader = Jbon(view)
        // values -8 to 7 should be encoded in one byte
        for (i in 0..15) {
            val value = TINY_FLOATS[i]
            builder.writeFloat(value)
            assertEquals(TYPE_FLOAT4 xor i, view.getInt8(0).toInt() and 0xff)
            assertTrue(reader.isFloat())
            assertTrue(reader.isDouble())
            assertTrue(reader.isNumber())
            assertEquals(value, reader.getFloat(-100f))
            assertEquals(value.toDouble(), reader.getDouble(-100.0))
            assertEquals(1, reader.size())
            assertEquals(1, builder.clear())
        }
        // all other values are encoded in 5 byte
        builder.writeFloat(1.25f)
        assertEquals(TYPE_FLOAT32, view.getInt8(0).toInt() and 0xff)
        assertEquals(1.25f, view.getFloat32(1))
        assertTrue(reader.isFloat())
        assertFalse(reader.isDouble())
        assertTrue(reader.isNumber())
        assertEquals(1.25f, reader.getFloat(0f))
        assertEquals(1.25, reader.getDouble(0.0))
        assertEquals(1.25, reader.getDouble(0.0, true))
        assertEquals(5, reader.size())
        assertEquals(5, builder.clear())
    }

    @Test
    fun testFloat64Encoding() {
        val view = JvmPlatform.dataViewOf(ByteArray(256))
        val builder = JbonBuilder(view)
        val reader = Jbon(view)
        // values -8 to 7 should be encoded in one byte
        for (i in 0..15) {
            val value = TINY_DOUBLES[i]
            builder.writeDouble(value)
            assertEquals(TYPE_FLOAT4 xor i, view.getInt8(0).toInt() and 0xff)
            assertTrue(reader.isFloat())
            assertTrue(reader.isDouble())
            assertTrue(reader.isNumber())
            assertEquals(value.toFloat(), reader.getFloat(-100f))
            assertEquals(value, reader.getDouble(-100.0))
            assertEquals(1, reader.size())
            assertEquals(1, builder.clear())
        }
        // all other values are encoded in 5 byte
        builder.writeDouble(1.25)
        assertEquals(TYPE_FLOAT64, view.getInt8(0).toInt() and 0xff)
        assertEquals(1.25, view.getFloat64(1))
        assertFalse(reader.isFloat())
        assertTrue(reader.isDouble())
        assertTrue(reader.isNumber())
        assertEquals(1.25, reader.getDouble(0.0))
        assertEquals(1.25f, reader.getFloat(0f))
        assertEquals(0.0f, reader.getFloat(0.0f, true))
        assertEquals(9, reader.size())
        assertEquals(9, builder.clear())
    }

    @Test
    fun testSize32Encoding() {
        val view = JvmPlatform.dataViewOf(ByteArray(256))
        val builder = JbonBuilder(view)
        val reader = Jbon(view)
        // a value between 0 and (including) 10 must result in one byte
        builder.writeSize32(0);
        assertEquals(TYPE_SIZE32, view.getInt8(0).toInt() and 0xff)
        assertEquals(0, reader.getSize32(-1))
        assertEquals(1, builder.clear())

        builder.writeSize32(11);
        assertEquals(11, (view.getInt8(0).toInt() and 0xff) - TYPE_SIZE32)
        assertEquals(1, builder.clear())

        // a value between 12 and 255 must be encoded in two byte
        builder.writeSize32(12);
        assertEquals(TYPE_SIZE32 xor 12, view.getInt8(0).toInt() and 0xff)
        assertEquals(12, view.getInt8(1).toInt() and 0xff)
        assertEquals(2, builder.clear())
        builder.writeSize32(255);
        assertEquals(TYPE_SIZE32 xor 12, view.getInt8(0).toInt() and 0xff)
        assertEquals(255.toByte(), view.getInt8(1))
        assertEquals(2, builder.clear())
        // a value between 256 and 65535 must be encoded in three byte
        builder.writeSize32(256);
        assertEquals(TYPE_SIZE32 xor 13, view.getInt8(0).toInt() and 0xff)
        assertEquals(256, view.getInt16(1))
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

    @Test
    fun testEncodingTwoInts() {
        val view = JvmPlatform.dataViewOf(ByteArray(256))
        val builder = JbonBuilder(view)
        val reader = Jbon(view)

        val firstPos = builder.writeInt(100_000)
        assertEquals(0, firstPos)
        val secondPos = builder.writeInt(1)
        assertEquals(5, secondPos)
        assertEquals(6, builder.end)

        // read values
        assertTrue(reader.isValid())
        assertTrue(reader.isInt())
        assertEquals(100_000, reader.getInt())
        reader.seekBy(reader.size())

        assertTrue(reader.isValid())
        assertTrue(reader.isInt())
        assertEquals(1, reader.getInt())
        reader.seekBy(reader.size())

        // We're now behind the last valid byte, everything else now should be simply null
        assertTrue(reader.isNull())
        assertEquals(1, reader.size())
    }

    @Test
    fun testStringEncoding() {
        val view = JvmPlatform.dataViewOf(ByteArray(256))
        val builder = JbonBuilder(view)
        val reader = Jbon(view)

        // should encode in 1 byte lead-in plus 1 byte character
        builder.writeString("a")
        assertEquals(1 + 1, reader.size())
        assertEquals(1 + 1, builder.clear())

        // a string with up to 10 characters will have a lead-in of only one byte
        builder.writeString("0123456789")
        assertEquals(1 + 10, reader.size())
        assertEquals(1 + 10, builder.clear())

        // a string with 11 characters, will have a two byte lead-in
        builder.writeString("01234567891")
        assertEquals(2 + 11, reader.size())
        assertEquals(2 + 11, builder.clear())

        // This encodes the sigma character, which is unicode 931 and should therefore be encoded in two byte
        // The lead-in for this short string should be only one byte
        builder.writeString("Σ")
        assertEquals(1 + 2, reader.size())
        // We should read the value 931, minus the bias of 128, plus the two high bits being 0b10
        assertEquals((931 - 128) xor 0b1000_0000_0000_0000, view.getInt16(1).toInt() and 0xffff)
        assertEquals(1 + 2, builder.clear())

        // This encodes the grinning face emojii, which is unicode 128512 and should therefore be encoded in three byte
        // The lead-in for this short string should still be only one byte
        builder.writeString("\uD83D\uDE00")
        assertEquals(1 + 3, reader.size())
        var unicode = (view.getInt8(1).toInt() and 0b0011_1111) shl 16
        unicode += view.getInt16(2).toInt() and 0xffff
        assertEquals(128512, unicode)
        assertEquals(1 + 3, builder.clear())
    }

    @Test
    fun testStringReader() {
        val view = JvmPlatform.dataViewOf(ByteArray(256))
        val builder = JbonBuilder(view)
        val reader = Jbon(view)
        val testString = "Hello World!"
        // We need to ensure that the test-string is long enough, otherwise the lead-in does not match
        check(testString.length in 11..255)
        builder.writeString(testString)
        assertTrue(reader.isString())
        // 12 characters, therefore 2 byte lead-in
        assertEquals(2 + testString.length, reader.size())

        // Map the string
        val jbonString = JbonString(reader)
        assertEquals(2 + testString.length, jbonString.size())
        assertEquals(testString.length, jbonString.length())
        // Ensure that all characters are the same as in the original
        // Note: This test only works for BMP codes!
        var i = 0
        while (i < testString.length) {
            val unicode = testString[i++].code
            assertEquals(unicode, jbonString.next())
        }

        // Test toString
        val string = jbonString.toString()
        assertEquals(testString, string)
        assertSame(string, jbonString.toString())
    }
}