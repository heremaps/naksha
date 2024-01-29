import com.here.naksha.lib.jbon.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.nio.charset.StandardCharsets
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

class JvmJbonTest {

    @Test
    fun basicTest() {
        val view = JvmPlatform.newDataView(ByteArray(256))
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
        val view = JvmPlatform.newDataView(ByteArray(256))
        val builder = JbBuilder(view)
        val reader = JbReader().mapView(view,0)
        builder.writeNull()
        assertEquals(TYPE_NULL, view.getInt8(0).toInt())
        assertEquals(TYPE_NULL, reader.type())
        assertTrue(reader.isNull())
        assertNull(reader.readBoolean())
        assertEquals(1, reader.size())
        assertEquals(1, builder.reset())
    }

    @Test
    fun testUndefined() {
        val view = JvmPlatform.newDataView(ByteArray(256))
        val builder = JbBuilder(view)
        val reader = JbReader().mapView(view,0)
        builder.writeUndefined()
        assertEquals(TYPE_UNDEFINED, view.getInt8(0).toInt())
        assertEquals(TYPE_UNDEFINED, reader.type())
        assertTrue(reader.isUndefined())
        assertNull(reader.readBoolean())
        assertEquals(1, reader.size())
        assertEquals(1, builder.reset())
    }

    @Test
    fun testBoolean() {
        val view = JvmPlatform.newDataView(ByteArray(256))
        val builder = JbBuilder(view)
        val reader = JbReader().mapView(view,0)
        builder.writeBool(true)
        assertEquals(TYPE_BOOL_TRUE, view.getInt8(0).toInt())
        assertEquals(TYPE_BOOL_TRUE, reader.type())
        assertTrue(reader.isBool())
        assertEquals(true, reader.readBoolean())
        assertEquals(1, reader.size())
        assertEquals(1, builder.reset())

        builder.writeBool(false)
        assertEquals(TYPE_BOOL_FALSE, view.getInt8(0).toInt())
        assertEquals(TYPE_BOOL_FALSE, reader.type())
        assertTrue(reader.isBool())
        assertEquals(false, reader.readBoolean())
        assertEquals(1, reader.size())
        assertEquals(1, builder.reset())
    }

    @Test
    fun testIntEncoding() {
        val view = JvmPlatform.newDataView(ByteArray(256))
        val builder = JbBuilder(view)
        val reader = JbReader().mapView(view,0)
        // the values -16 to 15 should be encoded in one byte
        builder.writeInt32(-16);
        assertTrue(reader.isInt())
        assertEquals(-16, reader.readInt32(0))
        assertEquals(1, reader.size())
        assertEquals(1, builder.reset())

        builder.writeInt32(15);
        assertTrue(reader.isInt())
        assertEquals(15, reader.readInt32(0))
        assertEquals(1, reader.size())
        assertEquals(1, builder.reset())

        // the values below -16 and above 15 should be encoded in two byte
        builder.writeInt32(-17);
        assertEquals(-17, view.getInt8(1))
        assertTrue(reader.isInt())
        assertEquals(-17, reader.readInt32(0))
        assertEquals(2, reader.size())
        assertEquals(2, builder.reset())

        builder.writeInt32(16);
        assertEquals(16, view.getInt8(1))
        assertTrue(reader.isInt())
        assertEquals(16, reader.readInt32(0))
        assertEquals(2, reader.size())
        assertEquals(2, builder.reset())

        // a value less than -128 must be stored in three byte
        builder.writeInt32(-129)
        assertEquals(-129, view.getInt16(1))
        assertTrue(reader.isInt())
        assertEquals(-129, reader.readInt32(0))
        assertEquals(3, reader.size())
        assertEquals(3, builder.reset())

        // a value bigger than 127 must be stored in three byte
        builder.writeInt32(128)
        assertEquals(128, view.getInt16(1))
        assertTrue(reader.isInt())
        assertEquals(128, reader.readInt32(0))
        assertEquals(3, reader.size())
        assertEquals(3, builder.reset())

        // a value less than -32768 must be stored in five byte
        builder.writeInt32(-32769)
        assertEquals(-32769, view.getInt32(1))
        assertTrue(reader.isInt())
        assertEquals(-32769, reader.readInt32(0))
        assertEquals(5, reader.size())
        assertEquals(5, builder.reset())

        // a value bigger than 32767 must be stored in three byte
        builder.writeInt32(32768)
        assertEquals(32768, view.getInt32(1))
        assertTrue(reader.isInt())
        assertEquals(32768, reader.readInt32(0))
        assertEquals(5, reader.size())
        assertEquals(5, builder.reset())

        // Test 64-bit integers
        builder.writeInt64(Long.MIN_VALUE)
        assertTrue(reader.isInt())
        assertFalse(reader.isInt32())
        assertEquals(TYPE_INT64, reader.type())
        assertEquals(Long.MIN_VALUE, reader.readInt64(0))
        assertEquals(9, reader.size())
        assertEquals(9, builder.reset())

        builder.writeInt64(Long.MAX_VALUE)
        assertTrue(reader.isInt())
        assertFalse(reader.isInt32())
        assertEquals(TYPE_INT64, reader.type())
        assertEquals(Long.MAX_VALUE, reader.readInt64(0))
        assertEquals(9, reader.size())
        assertEquals(9, builder.reset())

        // This ensures that high and low bits are encoded and decoded correctly in order
        builder.writeInt64(Long.MIN_VALUE + 65535)
        assertTrue(reader.isInt())
        assertFalse(reader.isInt32())
        assertEquals(TYPE_INT64, reader.type())
        assertEquals(Long.MIN_VALUE + 65535, reader.readInt64(0))
        assertEquals(9, reader.size())
        assertEquals(9, builder.reset())
    }

    @Test
    fun testFloat32Encoding() {
        val view = JvmPlatform.newDataView(ByteArray(256))
        val builder = JbBuilder(view)
        val reader = JbReader().mapView(view,0)
        // values -8 to 7 should be encoded in one byte
        for (i in 0..15) {
            val value = TINY_FLOATS[i]
            builder.writeFloat32(value)
            assertEquals(TYPE_FLOAT4 xor i, view.getInt8(0).toInt() and 0xff)
            assertTrue(reader.isFloat32())
            assertTrue(reader.isFloat64())
            assertTrue(reader.isNumber())
            assertEquals(value, reader.readFloat32(-100f))
            assertEquals(value.toDouble(), reader.readDouble(-100.0))
            assertEquals(1, reader.size())
            assertEquals(1, builder.reset())
        }
        // all other values are encoded in 5 byte
        builder.writeFloat32(1.25f)
        assertEquals(TYPE_FLOAT32, view.getInt8(0).toInt() and 0xff)
        assertEquals(1.25f, view.getFloat32(1))
        assertTrue(reader.isFloat32())
        assertFalse(reader.isFloat64())
        assertTrue(reader.isNumber())
        assertEquals(1.25f, reader.readFloat32(0f))
        assertEquals(1.25, reader.readDouble(0.0))
        assertEquals(1.25, reader.readDouble(0.0, true))
        assertEquals(5, reader.size())
        assertEquals(5, builder.reset())
    }

    @Test
    fun testFloat64Encoding() {
        val view = JvmPlatform.newDataView(ByteArray(256))
        val builder = JbBuilder(view)
        val reader = JbReader().mapView(view,0)
        // values -8 to 7 should be encoded in one byte
        for (i in 0..15) {
            val value = TINY_DOUBLES[i]
            builder.writeFloat64(value)
            assertEquals(TYPE_FLOAT4 xor i, view.getInt8(0).toInt() and 0xff)
            assertTrue(reader.isFloat32())
            assertTrue(reader.isFloat64())
            assertTrue(reader.isNumber())
            assertEquals(value.toFloat(), reader.readFloat32(-100f))
            assertEquals(value, reader.readDouble(-100.0))
            assertEquals(1, reader.size())
            assertEquals(1, builder.reset())
        }
        // all other values are encoded in 5 byte
        builder.writeFloat64(1.25)
        assertEquals(TYPE_FLOAT64, view.getInt8(0).toInt() and 0xff)
        assertEquals(1.25, view.getFloat64(1))
        assertFalse(reader.isFloat32())
        assertTrue(reader.isFloat64())
        assertTrue(reader.isNumber())
        assertEquals(1.25, reader.readDouble(0.0))
        assertEquals(1.25f, reader.readFloat32(0f))
        assertEquals(0.0f, reader.readFloat32(0.0f, true))
        assertEquals(9, reader.size())
        assertEquals(9, builder.reset())
    }

    @Test
    fun testEncodingTwoInts() {
        val view = JvmPlatform.newDataView(ByteArray(256))
        val builder = JbBuilder(view)
        val reader = JbReader().mapView(view,0)

        val firstPos = builder.writeInt32(100_000)
        assertEquals(0, firstPos)
        val secondPos = builder.writeInt32(1)
        assertEquals(5, secondPos)
        assertEquals(6, builder.end)

        // read values
        assertTrue(reader.ok())
        assertTrue(reader.isInt())
        assertEquals(100_000, reader.readInt32())
        reader.addOffset(reader.size())

        assertTrue(reader.ok())
        assertTrue(reader.isInt())
        assertEquals(1, reader.readInt32())
        reader.addOffset(reader.size())

        // We're now behind the last valid byte, everything else now should be simply null
        assertTrue(reader.isNull())
        assertEquals(1, reader.size())
    }

    @Test
    fun testStringEncoding() {
        val view = JvmPlatform.newDataView(ByteArray(256))
        val builder = JbBuilder(view)
        val reader = JbReader().mapView(view,0)

        // should encode in 1 byte lead-in plus 1 byte character
        builder.writeString("a")
        assertEquals(1 + 1, reader.size())
        assertEquals(1 + 1, builder.reset())

        // a string with up to 12 characters will have a lead-in of only one byte
        builder.writeString("123456789012")
        assertEquals(1 + 12, reader.size())
        assertEquals(1 + 12, builder.reset())

        // a string with 13 characters, will have a two byte lead-in
        builder.writeString("1234567890123")
        assertEquals(2 + 13, reader.size())
        assertEquals(2 + 13, builder.reset())

        // This encodes the sigma character, which is unicode 931 and should therefore be encoded in two byte
        // The lead-in for this short string should be only one byte
        builder.writeString("Σ")
        assertEquals(1 + 2, reader.size())
        // We should read the value 931, minus the bias of 128, plus the two high bits being 0b10
        assertEquals((931 - 128) xor 0b1000_0000_0000_0000, view.getInt16(1).toInt() and 0xffff)
        assertEquals(1 + 2, builder.reset())

        // This encodes the grinning face emojii, which is unicode 128512 and should therefore be encoded in three byte
        // The lead-in for this short string should still be only one byte
        builder.writeString("\uD83D\uDE00")
        assertEquals(1 + 3, reader.size())
        var unicode = (view.getInt8(1).toInt() and 0b0011_1111) shl 16
        unicode += view.getInt16(2).toInt() and 0xffff
        assertEquals(128512, unicode)
        assertEquals(1 + 3, builder.reset())
    }

    @Test
    fun testStringReader() {
        val view = JvmPlatform.newDataView(ByteArray(256))
        val builder = JbBuilder(view)
        val reader = JbReader().mapView(view,0)
        //                123456789012345
        val testString = "Hello my World!"
        // We need to ensure that the test-string is long enough, otherwise the lead-in does not match
        check(testString.length in 13..255)
        builder.writeString(testString)
        assertTrue(reader.isString())
        // 2 byte lead-in
        assertEquals(2 + testString.length, reader.size())

        // Map the string
        val jbString = JbString().mapReader(reader)
        assertEquals(2 + testString.length, jbString.mapSize())
        assertEquals(testString.length, jbString.length())
        // Ensure that all characters are the same as in the original
        // Note: This test only works for BMP codes!
        var i = 0
        while (i < testString.length) {
            val unicode = testString[i++].code
            assertEquals(unicode, jbString.readCodePoint(true))
        }

        // Test toString
        val string = jbString.toString()
        assertEquals(testString, string)
        assertSame(string, jbString.toString())

        // Test the getString method
        val testInternal = reader.readString()
        assertEquals(testString, testInternal)
    }

    @Test
    fun testReference() {
        val view = JvmPlatform.newDataView(ByteArray(256))
        val builder = JbBuilder(view)
        val reader = JbReader().mapView(view,0)

        // Write null reference (encoded in one byte).
        builder.writeRef(-1, true)
        assertTrue(reader.isRef())
        assertTrue(reader.isGlobalRef())
        assertFalse(reader.isLocalRef())
        assertEquals(-1, reader.readRef())
        assertEquals(1, reader.size())
        assertEquals(1, builder.reset())

        // Write zero reference (encoded in one byte).
        builder.writeRef(0, true)
        assertTrue(reader.isRef())
        assertTrue(reader.isGlobalRef())
        assertFalse(reader.isLocalRef())
        assertEquals(0, reader.readRef())
        assertEquals(1, reader.size())
        assertEquals(1, builder.reset())

        // Write two byte reference.
        builder.writeRef(65535 + 16, false)
        assertTrue(reader.isRef())
        assertFalse(reader.isGlobalRef())
        assertTrue(reader.isLocalRef())
        assertEquals(65535 + 16, reader.readRef())
        assertEquals(3, reader.size())
        assertEquals(3, builder.reset())

        // Write four byte reference.
        builder.writeRef(65536 + 16, false)
        assertTrue(reader.isRef())
        assertFalse(reader.isGlobalRef())
        assertTrue(reader.isLocalRef())
        assertEquals(65536 + 16, reader.readRef())
        assertEquals(5, reader.size())
        assertEquals(5, builder.reset())
    }

    @Test
    fun testDictionaryCreation() {
        val buildView = JvmPlatform.newDataView(ByteArray(8192))
        val builder = JbBuilder(buildView)

        val foo = builder.writeToLocalDictionary("foo")
        assertEquals(0, foo)
        val bar = builder.writeToLocalDictionary("bar")
        assertEquals(1, bar)
        val foo2 = builder.writeToLocalDictionary("foo")
        assertEquals(0, foo2)
        val bar2 = builder.writeToLocalDictionary("bar")
        assertEquals(1, bar2)

        // Encode a dictionary.
        val dictId = "test"
        val dictArray = builder.buildDictionary(dictId)
        val dictView = JvmPlatform.newDataView(dictArray)
        val dictReader = JbReader().mapView(dictView,0)
        assertEquals(TYPE_GLOBAL_DICTIONARY, dictReader.type())
        // size
        dictReader.addOffset(1)
        assertTrue(dictReader.isInt())
        assertEquals(13, dictReader.readInt32())

        // id
        assertTrue(dictReader.next())
        assertTrue(dictReader.isString())
        val stringReader = JbString().mapReader(dictReader)
        assertEquals(dictId, stringReader.toString())

        // foo
        assertTrue(dictReader.next())
        assertTrue(dictReader.isString())
        stringReader.mapReader(dictReader)
        assertEquals("foo", stringReader.toString())

        // bar
        assertTrue(dictReader.next())
        assertTrue(dictReader.isString())
        stringReader.mapReader(dictReader)
        assertEquals("bar", stringReader.toString())

        // eof
        assertFalse(dictReader.next())

        // Test the dictionary class.
        val dict = JbDict().mapView(dictView, 0)
        assertEquals(-1, dict.length())
        dict.loadAll()
        assertEquals(2, dict.length())
        assertEquals(dictId, dict.id())
        assertEquals("foo", dict.get(0))
        assertEquals("bar", dict.get(1))
        assertEquals(0, dict.indexOf("foo"))
        assertEquals(1, dict.indexOf("bar"))
        assertEquals(-1, dict.indexOf(dictId))
        assertEquals(-1, dict.indexOf("notFound"))
    }

    @Test
    fun testText() {
        val view = JvmPlatform.newDataView(ByteArray(8192))
        val builder = JbBuilder(view)
        val reader = JbReader().mapView(view,0)

        // We assume that this stores three words in the local dictionary:
        // 0 = Hello
        // 1 = World
        // 2 = Again
        builder.writeText("Hello World Hello Again")
        assertTrue(reader.isText())
        val localDictionary = builder.getLocalDictByString()
        assertEquals(3, localDictionary.size)
        assertEquals(0, localDictionary["Hello"])
        assertEquals(1, localDictionary["World"])
        assertEquals(2, localDictionary["Again"])
        // We expect that the text is encoded with:
        // Lead-in (1 byte), size (1 byte), then two byte per word (2 * 4 = 8)
        assertEquals(10, builder.end)
    }

    @Test
    fun testFeature() {
        // Read the topology, then parse and serialize to remove white spaces.
        var topologyBytes = JvmJbonTest::class.java.getResource("/topology.json")!!.readBytes()
        assertEquals(29659, topologyBytes.size)
        var topology = String(topologyBytes, StandardCharsets.UTF_8)
        topology = JvmPlatform.stringify(JvmPlatform.parse(topology))
        topologyBytes = topology.toByteArray(StandardCharsets.UTF_8)
        // After this only 16kb should be left
        assertEquals(16073, topologyBytes.size)
        // Convert this as string into a binary.
        val view = JvmPlatform.newDataView(ByteArray(65535))
        val builder = JbBuilder(view)
        builder.writeText(topology)
        val featureArray = builder.buildFeature(null)
        val reader = JbReader().mapView(JvmPlatform.newDataView(featureArray),0)
        assertEquals(TYPE_FEATURE, reader.type())
        assertEquals(11775, reader.size())
    }
}