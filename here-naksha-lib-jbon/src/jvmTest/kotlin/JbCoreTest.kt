import com.here.naksha.lib.jbon.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import java.nio.charset.StandardCharsets
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

class JbCoreTest : JbAbstractTest() {
    companion object {
        internal val TINY_FLOATS = floatArrayOf(-8f, -7f, -6f, -5f, -4f, -3f, -2f, -1f, 0f, 1f, 2f, 3f, 4f, 5f, 6f, 7f)
        internal val TINY_DOUBLES = doubleArrayOf(-8.0, -7.0, -6.0, -5.0, -4.0, -3.0, -2.0, -1.0, 0.0, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0)
    }

    @Test
    fun testCompression() {
        val originalString = "Hello World LZ4!"
        val originalBytes = originalString.toByteArray(StandardCharsets.UTF_8)
        val compressed = env.lz4Deflate(originalBytes)
        val decompressed = env.lz4Inflate(compressed, originalBytes.size)
        assertArrayEquals(decompressed, originalBytes)
        val decompressedString = String(decompressed, StandardCharsets.UTF_8)
        assertEquals(decompressedString, originalString)
    }

    @Test
    fun testRandomString() {
        val r = env.randomString(100)
        assertEquals(100, r.length)
        var i = 0
        while (i < r.length) {
            val c = r[i++]
            assertTrue(c in '0'..'9' || c in 'a'..'z' || c in 'A'..'Z' || c == '_' || c == '-',
                    "Invalid character: " + c)
        }
    }

    @Test
    fun testDoubleToFloat() {
        assertTrue(env.canBeFloat32(12.0))
        assertTrue(env.canBeFloat32(Float.MAX_VALUE.toDouble()))
        // Note: Technically the conversion is possible, but when widening to double, the exponent is inflated to -149
        //       Even while this is technically correct, our simple method then rejects this, because it only allows
        //       the exponent to be -126 for safe-conversion.
        assertFalse(env.canBeFloat32(Float.MIN_VALUE.toDouble()))
    }

    @Test
    fun basicTest() {
        val view = JbSession.get().newDataView(ByteArray(256))
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

    @Suppress("UNCHECKED_CAST")
    @Test
    fun testJson() {
        // Test parse.
        val raw = env.parse("""
{
    "id": "foo",
    "properties": {
        "name": "Tim",
        "age": 99
    }
}""".trimIndent())
        assertTrue(raw is HashMap<*, *>)
        val map = raw as HashMap<String, *>
        assertEquals(2, map.size)
        assertTrue(map.containsKey("id"))
        assertEquals("foo", map["id"])
        assertTrue(map.containsKey("properties"))
        assertTrue(map["properties"] is HashMap<*, *>)
        val properties = map["properties"] as HashMap<String, *>
        assertEquals(2, properties.size)
        assertEquals("Tim", properties["name"])
        assertEquals(99, properties["age"])

        // Test stringify.
        val json = env.stringify(map, false)
        assertEquals(49, json.length)
        assertTrue(json.contains("properties"))
    }

    @Test
    fun testNull() {
        val view = JbSession.get().newDataView(ByteArray(256))
        val builder = JbBuilder(view)
        val reader = JbReader().mapView(view, 0)
        builder.writeNull()
        assertEquals(ENC_MIXED_CONST_NULL, view.getInt8(0).toInt())
        assertEquals(TYPE_NULL, reader.unitType())
        assertTrue(reader.isNull())
        assertNull(reader.readBoolean())
        assertEquals(1, reader.unitSize())
        assertEquals(1, builder.clear())
    }

    @Test
    fun testUndefined() {
        val view = JbSession.get().newDataView(ByteArray(256))
        val builder = JbBuilder(view)
        val reader = JbReader().mapView(view, 0)
        builder.writeUndefined()
        assertEquals(ENC_MIXED_CONST_UNDEFINED, view.getInt8(0).toInt())
        assertEquals(TYPE_UNDEFINED, reader.unitType())
        assertTrue(reader.isUndefined())
        assertNull(reader.readBoolean())
        assertEquals(1, reader.unitSize())
        assertEquals(1, builder.clear())
    }

    @Test
    fun testBoolean() {
        val builder = JbBuilder()
        val view = builder.view()
        val reader = JbReader().mapView(view, 0)
        builder.writeBool(true)
        assertEquals(ENC_MIXED_CONST_TRUE, view.getInt8(0).toInt())
        assertEquals(TYPE_BOOL, reader.unitType())
        assertTrue(reader.isBool())
        assertEquals(true, reader.readBoolean())
        assertEquals(1, reader.unitSize())
        assertEquals(1, builder.clear())

        builder.writeBool(false)
        assertEquals(ENC_MIXED_CONST_FALSE, view.getInt8(0).toInt())
        assertEquals(TYPE_BOOL, reader.unitType())
        assertTrue(reader.isBool())
        assertEquals(false, reader.readBoolean())
        assertEquals(1, reader.unitSize())
        assertEquals(1, builder.clear())
    }

    @Test
    fun testIntEncoding() {
        val int64 = JvmBigInt64Api()
        val view = JbSession.get().newDataView(ByteArray(256))
        val builder = JbBuilder(view)
        val reader = JbReader().mapView(view, 0)
        // the values -16 to 15 should be encoded in one byte
        builder.writeInt(-16);
        assertTrue(reader.isInt())
        assertEquals(-16, reader.readInt32(0))
        assertEquals(1, reader.unitSize())
        assertEquals(1, builder.clear())
        reader.reset()

        builder.writeInt(15);
        assertTrue(reader.isInt())
        assertEquals(15, reader.readInt32(0))
        assertEquals(1, reader.unitSize())
        assertEquals(1, builder.clear())
        reader.reset()

        // the values below -16 and above 15 should be encoded in two byte
        builder.writeInt(-17);
        assertEquals(-17, view.getInt8(1))
        assertTrue(reader.isInt())
        assertEquals(-17, reader.readInt32(0))
        assertEquals(2, reader.unitSize())
        assertEquals(2, builder.clear())
        reader.reset()

        builder.writeInt(16);
        assertEquals(16, view.getInt8(1))
        assertTrue(reader.isInt())
        assertEquals(16, reader.readInt32(0))
        assertEquals(2, reader.unitSize())
        assertEquals(2, builder.clear())
        reader.reset()

        // a value less than -128 must be stored in three byte
        builder.writeInt(-129)
        assertEquals(-129, view.getInt16(1))
        assertTrue(reader.isInt())
        assertEquals(-129, reader.readInt32(0))
        assertEquals(3, reader.unitSize())
        assertEquals(3, builder.clear())
        reader.reset()

        // a value bigger than 127 must be stored in three byte
        builder.writeInt(128)
        assertEquals(128, view.getInt16(1))
        assertTrue(reader.isInt())
        assertEquals(128, reader.readInt32(0))
        assertEquals(3, reader.unitSize())
        assertEquals(3, builder.clear())
        reader.reset()

        // a value less than -32768 must be stored in five byte
        builder.writeInt(-32769)
        assertEquals(-32769, view.getInt32(1))
        assertTrue(reader.isInt())
        assertEquals(-32769, reader.readInt32(0))
        assertEquals(5, reader.unitSize())
        assertEquals(5, builder.clear())
        reader.reset()

        // a value bigger than 32767 must be stored in three byte
        builder.writeInt(32768)
        assertEquals(32768, view.getInt32(1))
        assertTrue(reader.isInt())
        assertEquals(32768, reader.readInt32(0))
        assertEquals(5, reader.unitSize())
        assertEquals(5, builder.clear())
        reader.reset()

        // Test 64-bit integers
        builder.writeInt64(int64.MIN_VALUE())
        assertTrue(reader.isInt())
        assertFalse(reader.isInt32())
        assertEquals(TYPE_INT, reader.unitType())
        assertEquals(int64.MIN_VALUE(), reader.readInt64())
        assertEquals(9, reader.unitSize())
        assertEquals(9, builder.clear())
        reader.reset()

        builder.writeInt64(int64.MAX_VALUE())
        assertTrue(reader.isInt())
        assertFalse(reader.isInt32())
        assertEquals(TYPE_INT, reader.unitType())
        assertEquals(int64.MAX_VALUE(), reader.readInt64())
        assertEquals(9, reader.unitSize())
        assertEquals(9, builder.clear())
        reader.reset()

        // This ensures that high and low bits are encoded and decoded correctly in order
        builder.writeInt64(int64.MIN_VALUE() addi 65535)
        assertTrue(reader.isInt())
        assertFalse(reader.isInt32())
        assertEquals(TYPE_INT, reader.unitType())
        assertEquals(int64.MIN_VALUE() addi 65535, reader.readInt64())
        assertEquals(9, reader.unitSize())
        assertEquals(9, builder.clear())
        reader.reset()
    }

    @Test
    fun testFloat32Encoding() {
        val builder = JbBuilder()
        val view = builder.view()
        val reader = JbReader().mapView(view, 0)
        for (i in -16..15) {
            builder.writeFloat(i.toFloat())
            assertEquals(i, ((view.getInt8(0).toInt() shl 27) shr 27))
            assertEquals(TYPE_FLOAT, reader.unitType())
            assertTrue(reader.isFloat32())
            assertTrue(reader.isFloat64())
            assertTrue(reader.isNumber())
            assertEquals(i.toFloat(), reader.readFloat32(-100f))
            assertEquals(i.toDouble(), reader.readFloat64(-100.0))
            assertEquals(1, reader.unitSize())
            assertEquals(1, builder.clear())
            reader.reset()
        }
        // all other values are encoded in 5 byte
        builder.writeFloat(1.25f)
        assertEquals(ENC_MIXED_SCALAR_FLOAT32, view.getInt8(0).toInt() and 0xff)
        assertEquals(1.25f, view.getFloat32(1))
        assertTrue(reader.isFloat32())
        assertTrue(reader.isFloat64())
        assertTrue(reader.isNumber())
        assertEquals(1.25f, reader.readFloat32())
        assertEquals(1.25, reader.readFloat64())
        assertEquals(1.25, reader.readFloat64(readStrict = true))
        assertEquals(5, reader.unitSize())
        assertEquals(5, builder.clear())
        reader.reset()
    }

    @Test
    fun testFloat64Encoding() {
        val view = JbSession.get().newDataView(ByteArray(256))
        val builder = JbBuilder(view)
        val reader = JbReader().mapView(view, 0)
        for (i in -16..15) {
            builder.writeDouble(i.toDouble())
            assertEquals(i, ((view.getInt8(0).toInt() shl 27) shr 27))
            assertTrue(reader.isFloat32())
            assertTrue(reader.isFloat64())
            assertTrue(reader.isNumber())
            assertEquals(i.toFloat(), reader.readFloat32())
            assertEquals(i.toDouble(), reader.readFloat64())
            assertEquals(1, reader.unitSize())
            assertEquals(1, builder.clear())
            reader.reset()
        }
        // all other values are encoded in 5 byte
        builder.writeDouble(1.25)
        assertEquals(ENC_MIXED_SCALAR_FLOAT64, view.getInt8(0).toInt() and 0xff)
        assertEquals(1.25, view.getFloat64(1))
        assertFalse(reader.isFloat32())
        assertTrue(reader.isFloat64())
        assertTrue(reader.isNumber())
        assertEquals(1.25, reader.readFloat64())
        assertEquals(1.25f, reader.readFloat32())
        assertTrue(reader.readFloat32(readStrict = true).isNaN())
        assertEquals(9, reader.unitSize())
        assertEquals(9, builder.clear())
    }

    @Test
    fun testEncodingTwoInts() {
        val view = JbSession.get().newDataView(ByteArray(256))
        val builder = JbBuilder(view)
        val reader = JbReader().mapView(view, 0)

        val firstPos = builder.writeInt(100_000)
        assertEquals(0, firstPos)
        val secondPos = builder.writeInt(1)
        assertEquals(5, secondPos)
        assertEquals(6, builder.end)

        // read values
        assertTrue(reader.ok())
        assertTrue(reader.isInt())
        assertEquals(100_000, reader.readInt32())
        reader.addOffset(reader.unitSize())

        assertTrue(reader.ok())
        assertTrue(reader.isInt())
        assertEquals(1, reader.readInt32())
        reader.addOffset(reader.unitSize())

        // We're now behind the last valid byte, all values are zero, therefore they should be integer value 0.
        assertTrue(reader.isUndefined())
        assertEquals(1, reader.unitSize())

        // Let's limit the reader to the end of the builder and retry
        reader.setEnd(builder.end)
        reader.reset()
        assertTrue(reader.isInt())
        assertEquals(TYPE_INT, reader.unitType())
        assertTrue(reader.nextUnit()) // skip first int

        assertTrue(reader.isInt())
        assertEquals(TYPE_INT, reader.unitType())
        assertFalse(reader.nextUnit()) // skip second int, should result in invalid position

        assertEquals(TYPE_UNDEFINED, reader.unitType())
        assertTrue(reader.eof())
        assertFalse(reader.ok())
    }

    @Test
    fun testStringEncoding() {
        val builder = JbBuilder()
        val view = builder.view()
        val reader = JbReader().mapView(view, 0)

        // should encode in 1 byte lead-in plus 1 byte character
        builder.writeString("a")
        assertEquals(1 + 1, reader.unitSize())
        assertEquals(1 + 1, builder.clear())
        reader.reset()

        // a string with up to 12 characters will have a lead-in of only one byte
        builder.writeString("123456789012")
        assertEquals(1 + 12, reader.unitSize())
        assertEquals(1 + 12, builder.clear())
        reader.reset()

        // a string with 61 characters, will have a two byte lead-in
        builder.writeString("1234567890123456789012345678901234567890123456789012345678901")
        assertEquals(2, reader.unitHeaderSize())
        assertEquals(61, reader.unitPayloadSize())
        assertEquals(2 + 61, reader.unitSize())
        assertEquals(2 + 61, builder.clear())
        reader.reset()

        // This encodes the sigma character, which is unicode 931 and should therefore be encoded in two byte
        // The lead-in for this short string should be only one byte
        builder.writeString("Σ")
        assertEquals(1 + 2, reader.unitSize())
        // We should read the value 931, minus the bias of 128, plus the two high bits being 0b10
        assertEquals((931 - 128) xor 0b1000_0000_0000_0000, view.getInt16(1).toInt() and 0xffff)
        assertEquals(1 + 2, builder.clear())
        reader.reset()

        // This encodes the grinning face emojii, which is unicode 128512 and should therefore be encoded in three byte
        // The lead-in for this short string should still be only one byte
        builder.writeString("\uD83D\uDE00")
        assertEquals(1 + 3, reader.unitSize())
        var unicode = (view.getInt8(1).toInt() and 0b0011_1111) shl 16
        unicode += view.getInt16(2).toInt() and 0xffff
        assertEquals(128512, unicode)
        assertEquals(1 + 3, builder.clear())
        reader.reset()
    }


    @Test
    fun testReference() {
        val view = JbSession.get().newDataView(ByteArray(256))
        val builder = JbBuilder(view)
        val reader = JbReader().mapView(view, 0)

        // Write null reference (encoded in one byte).
        builder.writeRef(-1, true)
        assertTrue(reader.isRef())
        assertTrue(reader.isGlobalRef())
        assertFalse(reader.isLocalRef())
        assertEquals(-1, reader.readRef())
        assertEquals(1, reader.unitSize())
        assertEquals(1, builder.clear())
        reader.reset()

        // Write zero reference (encoded in one byte).
        builder.writeRef(0, true)
        assertTrue(reader.isRef())
        assertTrue(reader.isGlobalRef())
        assertFalse(reader.isLocalRef())
        assertEquals(0, reader.readRef())
        assertEquals(1, reader.unitSize())
        assertEquals(1, builder.clear())
        reader.reset()

        // Write two byte reference.
        builder.writeRef(65535 + 16, false)
        assertTrue(reader.isRef())
        assertFalse(reader.isGlobalRef())
        assertTrue(reader.isLocalRef())
        assertEquals(65535 + 16, reader.readRef())
        assertEquals(3, reader.unitSize())
        assertEquals(3, builder.clear())
        reader.reset()

        // Write four byte reference.
        builder.writeRef(65536 + 16, false)
        assertTrue(reader.isRef())
        assertFalse(reader.isGlobalRef())
        assertTrue(reader.isLocalRef())
        assertEquals(65536 + 16, reader.readRef())
        assertEquals(5, reader.unitSize())
        assertEquals(5, builder.clear())
    }

    @Test
    fun testDictionaryCreation() {
        val builder = JbBuilder()

        val foo = builder.addToLocalDictionary("foo")
        assertEquals(0, foo)
        val bar = builder.addToLocalDictionary("bar")
        assertEquals(1, bar)
        val foo2 = builder.addToLocalDictionary("foo")
        assertEquals(0, foo2)
        val bar2 = builder.addToLocalDictionary("bar")
        assertEquals(1, bar2)

        // Encode a dictionary.
        val dictId = "test"
        val dictArray = builder.buildDictionary(dictId)
        val dictView = JbSession.get().newDataView(dictArray)
        val dictReader = JbReader().mapView(dictView, 0)
        assertEquals(TYPE_DICTIONARY, dictReader.unitType())
        // size
        dictReader.addOffset(1)
        assertEquals(13, dictView.getInt8(dictReader.offset()))

        // id
        assertTrue(dictReader.nextUnit())
        assertTrue(dictReader.isString())
        assertEquals(dictId, dictReader.readString())

        // foo
        assertTrue(dictReader.nextUnit())
        assertTrue(dictReader.isString())
        assertEquals("foo", dictReader.readString())

        // bar
        assertTrue(dictReader.nextUnit())
        assertTrue(dictReader.isString())
        assertEquals("bar", dictReader.readString())

        // eof
        assertFalse(dictReader.nextUnit())

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
        val view = JbSession.get().newDataView(ByteArray(8192))
        val builder = JbBuilder(view)
        val reader = JbReader().mapView(view, 0)

        // We assume that this stores three words in the local dictionary:
        // 0 = Hello
        // 1 = World
        // 2 = Again
        builder.writeText("Hello World Hello Again")
        assertTrue(reader.isString())
        val localDictionary = builder.getLocalDictByString()
        assertEquals(3, localDictionary.size)
        assertEquals(0, localDictionary["Hello"])
        assertEquals(1, localDictionary["World"])
        assertEquals(2, localDictionary["Again"])
        // We expect that the text is encoded with:
        // Lead-in (1 byte) including size, then two byte per word (2 * 4 = 8)
        assertEquals(9, builder.end)
    }

    @Test
    fun testSmallTextFeature() {
        val builder = JbBuilder()
        builder.writeText("Hello World Hello Test")
        val featureBytes = builder.buildFeature(null, 0)
        val feature = JbFeature()
        feature.mapBytes(featureBytes)
        val view = feature.reader.view()
        // We expect the following layout:
        // feature lead-in (1 byte)
        assertEquals(ENC_STRUCT, view.getInt8(0).toInt() and ENC_STRUCT)
        assertEquals(ENC_STRUCT_VARIANT_FEATURE, view.getInt8(0).toInt() and ENC_STRUCT_TYPE_MASK)
        // feature size (1 byte)
        assertEquals(31, view.getInt8(1).toInt() and 0xff)
        // feature variant (1 byte)
        assertEquals(0, view.getInt8(2).toInt() and 0xff)
        // global dictionary id null (1 byte)
        assertEquals(ENC_MIXED_CONST_NULL, view.getInt8(3).toInt() and 0xff)
        // feature id null (1 byte)
        assertEquals(ENC_MIXED_CONST_NULL, view.getInt8(4).toInt() and 0xff)
        // dictionary lead-in (1 byte)
        assertEquals(ENC_STRUCT, view.getInt8(5).toInt() and 0xff and ENC_STRUCT)
        assertEquals(ENC_STRUCT_DICTIONARY, view.getInt8(5).toInt() and 0xff and ENC_STRUCT_TYPE_MASK)
        // dictionary size (1 byte)
        assertEquals(18, view.getInt8(6).toInt() and 0xff)
        // no variant (header-size: 2, 7+18 = 25)
        // dictionary id null (1 byte)
        assertEquals(ENC_MIXED_CONST_NULL, view.getInt8(7).toInt() and 0xff)
        // first dictionary entry:
        // string lead-in (1 byte) with length (5) "Hello" = 6 byte total
        assertEquals(ENC_STRING, view.getInt8(8).toInt() and ENC_MASK)
        assertEquals(5, view.getInt8(8).toInt() and 0b0011_1111)
        // string lead-in (1 byte) with length (5) "World" = 6 byte total
        assertEquals(ENC_STRING, view.getInt8(14).toInt() and ENC_MASK)
        assertEquals(5, view.getInt8(14).toInt() and 0b0011_1111)
        // string lead-in (1 byte) with length (4) "Test" = 5 byte total
        assertEquals(ENC_STRING, view.getInt8(20).toInt() and ENC_MASK)
        assertEquals(4, view.getInt8(20).toInt() and 0b0011_1111)
        // payload of feature, being a string lead-in (1 byte)
        assertEquals(ENC_STRING, view.getInt8(25).toInt() and 0xff and ENC_STRING)
        // the string should be:
        // lead-in (with embedded size of 8) - 1 byte
        // local-dict-ref (with 1 byte index and space) - 2 byte ("Hello")
        // local-dict-ref (with 1 byte index and space) - 2 byte ("World")
        // local-dict-ref (with 1 byte index and space) - 2 byte ("Hello")
        // local-dict-ref (with 1 byte index) - 2 byte ("Test")
        // = 9 byte total
        assertEquals(8, view.getInt8(25).toInt() and 0b0011_1111)
        assertEquals(34, featureBytes.size)

        // the local dictionary of the feature, should currently be at position 8 (first dictionary entry)
        val localDict = feature.reader.localDict
        assertNotNull(localDict)
        assertEquals(8, localDict.reader.offset())

        // the local dictionary should end at position 25, where the payload starts
        assertEquals(25, localDict.reader.end())

        // the feature map should now be as well at position 25, the payload
        assertEquals(25, feature.reader.offset())

        // Binary encoding is right, test reading it.

        assertTrue(feature.reader.isString())
        // Currently nothing should be in the dictionary.
        assertEquals(-1, feature.reader.localDict!!.length())
        // Decode the text.
        assertEquals("Hello World Hello Test", feature.reader.readString())
        // Now the dictionary should be filled.
        assertEquals(3, feature.reader.localDict!!.length())

        // TODO: Fix this
        // Create a second text mapper and map it to the reader of the feature.
        // We expect that they share the local dictionary.
        // There, after mapping, the dictionary should instantly be filled.
// assertEquals(3, text2.localDict().length())

        // Use the text reader, should return the same.
//        val readText = feature.reader.readText()
//        assertEquals("Hello World Hello Test", readText)
        // We assume, that calling the reader multiple times returns the same string instance.
//        assertSame(readText, feature.reader.readText())
    }

    @Test
    fun testBigFeature() {
        // Read the topology, then parse and serialize to remove white spaces.
        var topologyBytes = JbCoreTest::class.java.getResource("/topology.json")!!.readBytes()
        assertEquals(29659, topologyBytes.size)
        var topology = String(topologyBytes, StandardCharsets.UTF_8)
        topology = env.stringify(env.parse(topology))
        topologyBytes = topology.toByteArray(StandardCharsets.UTF_8)
        // After this only 16kb should be left
        assertEquals(16073, topologyBytes.size)
        // Convert this as string into a binary.
        val view = JbSession.get().newDataView(ByteArray(65535))
        val builder = JbBuilder(view)
        builder.writeText(topology)
        val featureArray = builder.buildFeature(null)
        val featureView = JbSession.get().newDataView(featureArray)

        // Encode a dictionary.
        builder.reset()
        val dictId = "test"
        val dictArray = builder.buildDictionary(dictId)
        val dictView = JbSession.get().newDataView(dictArray)

        // Test the dictionary class.
        val dict = JbDict().mapView(dictView, 0)
        dict.loadAll()

        val view2 = JbSession.get().newDataView(ByteArray(65535))
        val builder2 = JbBuilder(view2, dict)
        builder2.writeText(topology)
        val featureArray2 = builder2.buildFeature(null)
        val featureView2 = JbSession.get().newDataView(featureArray2)

        // Simple test using low level reader.
        val reader = JbReader().mapView(featureView, 0)
        assertEquals(TYPE_FEATURE, reader.unitType())
        assertEquals(11682, reader.unitSize())

        // Simple test using low level reader.
        val reader2 = JbReader().mapView(featureView2, 0)
        assertEquals(TYPE_FEATURE, reader2.unitType())
        assertEquals(8341, reader2.unitSize())

        // Use the feature reader.
        val feature = JbFeature().mapView(featureView, 0)
        assertEquals(TYPE_STRING, feature.reader.unitType())
        assertTrue(feature.reader.isString())
        // TODO: Fix me!
//        val text = JbText().mapReader(feature.reader)
//        val topologyRestored = text.toString()
//        assertEquals(topology, topologyRestored)
//        assertSame(topologyRestored, text.toString())
    }

    @Test
    fun testArray() {
        val view = JbSession.get().newDataView(ByteArray(8192))
        val builder = JbBuilder(view)
        val arrayStart = builder.startArray()
        builder.writeString("foo")
        builder.writeString("bar")
        builder.endArray(arrayStart)

        val reader = JbReader()
        reader.mapView(view, 0)
        assertTrue(reader.isArray())
        // 1-byte lead-in, 1-byte size, 4 byte for string "foo" and 4 byte for string "bar"
        assertEquals(2 + 4 + 4, reader.unitSize())
        // Therefore, the size should be encoded in a byte with the value being 8.
        assertEquals(8, view.getInt8(1))
        // Read string "foo"
        reader.setOffset(2)
        assertTrue(reader.isString())
        assertEquals("foo", reader.readString())
        // Read string "bar"
        reader.setOffset(2 + 4)
        assertTrue(reader.isString())
        assertEquals("bar", reader.readString())

        // Test the array class, should basically allow the same.
        val array = JbArray()
        array.mapView(view, 0)
        assertEquals(2, array.length())
        // We should be able to read "foo"
        array.seek(0)
        assertEquals(0, array.pos())
        assertTrue(array.value().isString())
        assertEquals("foo", array.value().readString())

        // We should be able to read "bar"
        array.seek(1)
        assertEquals(1, array.pos())
        assertTrue(array.value().isString())
        assertEquals("bar", array.value().readString())

        // We should fail to move to 2
        array.seek(2)
        assertEquals(-1, array.pos())

        // Loop through the array
        array.reset()
        var i = 0
        while (i < array.length()) {
            array.seek(i)
            assertEquals(i, array.pos())
            assertTrue(array.value().isString())
            i++
        }
        assertEquals(2, i)

        // Iterate the array.
        array.seek(0)
        i = 0
        while (array.ok()) {
            array.next()
            i++
        }
        assertEquals(-1, array.pos())
        assertEquals(2, i)
    }

    @Test
    fun testMap() {
        val builder = JbSession.get().newBuilder()
        val view = builder.view()
        val reader = JbReader()

        val start = builder.startMap()
        builder.writeKey("foo")
        builder.writeInt(1)
        builder.writeKey("bar")
        builder.writeBool(true)
        builder.endMap(start)

        // Manual encoding checks.
        reader.mapView(view, 0)
        assertTrue(reader.isMap())
        reader.addOffset(1)
        // We expect the size being:
        // 1 byte lead-in
        // 1 byte size (4)
        // 1 byte foo reference
        // 1 byte int
        // 1 byte bar reference
        // 1 byte int
        // = 6 byte total size, 2 byte header, 4 byte content, unitSize = 5
        assertEquals(4, view.getInt8(reader.offset()).toInt() and 0xff)

        // Now, we have a 6-byte map that we want to wrap into a feature.
        // We need to embed the local dictionary, which should be:
        // 1 byte lead-in
        // 1 byte size (8)
        // 1 byte variant (0)
        // 1 byte lead-in of first string (with embedded size of 3)
        // 3 byte string (foo)
        // 1 byte lead-in of second string (with embedded size of 3)
        // 3 byte string (bar)
        // = 10 byte total size, 2 byte header, 8 byte content
        // Eventually, the feature will wrap the local dictionary and the map:
        // 1 byte lead-in
        // 2 byte size (19)
        // 1 byte (null), no global dictionary
        // 1 byte (null), no id of the feature
        // 11 byte embedded local dictionary
        // 6 byte embedded map (content-size)
        // = 21 byte total, 15-byte header (includes local dict), 6-byte content
        val mapData = builder.buildFeature(null)
        assertEquals(22, mapData.size)
        val feature = JbFeature()
        feature.mapBytes(mapData)
        assertEquals(null, feature.id())
        assertTrue(feature.reader.isMap())
        val map = JbMap()
        map.mapReader(feature.reader)

        assertTrue(map.first())
        assertTrue(map.ok())
        assertEquals("foo", map.key())
        assertEquals(1, map.value().readInt32())

        assertTrue(map.next())
        assertTrue(map.ok())
        assertEquals("bar", map.key())
        assertEquals(true, map.value().readBoolean())

        assertFalse(map.next())
        assertFalse(map.ok())

        // Test key selection.
        assertTrue(map.selectKey("foo"))
        assertEquals(0, map.pos())
        assertEquals(1, map.value().readInt32())

        assertTrue(map.selectKey("bar"))
        assertEquals(1, map.pos())
        assertEquals(true, map.value().readBoolean())

        // Test key access by index.
        map.seek(0)
        assertTrue(map.ok())
        assertEquals("foo", map.key())
        map.seek(1)
        assertTrue(map.ok())
        assertEquals("bar", map.key())
        map.seek(2)
        assertFalse(map.ok())
    }

    @Test
    fun testJbonTimestamp() {
        val nowLong = 1707491351417L
        val nowBigInt64 = BigInt64(nowLong)
        val view = JbSession.get().newDataView(ByteArray(256))
        val builder = JbBuilder(view)
        val reader = JbReader().mapView(view, 0)
        builder.writeTimestamp(nowBigInt64)
        assertEquals(ENC_MIXED_SCALAR_TIMESTAMP, view.getInt8(0).toInt())
        assertEquals(TYPE_TIMESTAMP, reader.unitType())
        assertEquals((nowLong ushr 32).toShort(), view.getInt16(reader.offset() + 1))
        assertEquals(nowLong.toInt(), view.getInt32(reader.offset() + 3))
        assertTrue(reader.isTimestamp())
        val ts = reader.readTimestamp()
        assertEquals(nowLong, ts.toLong())
        assertEquals(7, reader.unitSize())
        assertEquals(7, builder.clear())
    }

    @Test
    fun testTimestamp() {
        val millis = BigInt64(1707985967244)
        val ts = JbTimestamp.fromMillis(millis)
        assertEquals(millis, ts.ts)
        assertEquals(2024, ts.year)
        assertEquals(2, ts.month)
        assertEquals(15, ts.day)
        assertEquals(8, ts.hour)
        assertEquals(32, ts.minute)
        assertEquals(47, ts.second)
        assertEquals(244, ts.millis)
    }

    @Test
    fun testBuildingCollectionWithOnlyId() {
        val builder = JbBuilder.create()
        val featureJson = """{"id":"bar"}"""
        val featureMap = asMap(env.parse(featureJson))
        val featureBytes = builder.buildFeatureFromMap(featureMap)
        val feature = JbFeature()
        feature.mapBytes(featureBytes)
        assertEquals("bar", feature.id())
    }
}