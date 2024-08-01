package naksha.jbon

import naksha.base.*
import kotlin.test.*

class JbCoreTest {
    companion object {
        internal val TINY_FLOATS = floatArrayOf(-8f, -7f, -6f, -5f, -4f, -3f, -2f, -1f, 0f, 1f, 2f, 3f, 4f, 5f, 6f, 7f)
        internal val TINY_DOUBLES = doubleArrayOf(-8.0, -7.0, -6.0, -5.0, -4.0, -3.0, -2.0, -1.0, 0.0, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0)
        internal val dictManager = JbDictManager()
    }

//    @Test
//    fun testCompression() {
//        val originalString = "Hello World LZ4!"
//        val originalBytes = originalString.
//        val compressed = Platform.lz4Deflate(originalBytes)
//        val decompressed = Platform.lz4Inflate(compressed, originalBytes.size)
//        assertArrayEquals(decompressed, originalBytes)
//        val decompressedString = String(decompressed, StandardCharsets.UTF_8)
//        assertEquals(decompressedString, originalString)
//    }

    @Test
    fun testRandomString() {
        val r = PlatformUtil.randomString(100)
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
        assertTrue(Platform.canBeFloat32(12.0))
        assertTrue(Platform.canBeFloat32(PlatformUtil.FLOAT_MAX))
        // Note: Technically the conversion is possible, but when widening to double, the exponent is inflated to -149
        //       Even while this is technically correct, our simple method then rejects this, because it only allows
        //       the exponent to be -126 for safe-conversion.
        assertFalse(Platform.canBeFloat32(PlatformUtil.FLOAT_MIN))
    }

    @Test
    fun basicTest() {
        val view = Binary()
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
        val raw = Platform.fromJSON("""
{
    "id": "foo",
    "properties": {
        "name": "Tim",
        "age": 99,
        "array": [1, 2, 3, 4, 5]
    }
}""".trimIndent())
        assertTrue(raw is PlatformMap)
        val map = raw.proxy(AnyObject::class)
        assertEquals(2, map.size)
        assertTrue(map.containsKey("id"))
        assertEquals("foo", map["id"])
        assertTrue(map.containsKey("properties"))
        assertTrue(map["properties"] is AnyObject)
        val properties = map["properties"] as AnyObject
        assertEquals(3, properties.size)
        assertEquals("Tim", properties["name"])
        assertEquals(99, properties["age"])
        assertTrue(properties["array"] is AnyList)
        val array = properties["array"] as AnyList
        assertEquals(5, array.size)
        assertEquals(1, array[0])
        assertEquals(2, array[1])
        assertEquals(3, array[2])
        assertEquals(4, array[3])
        assertEquals(5, array[4])
        properties.remove("array")
        assertEquals(2, properties.size)

        // Test stringify.
        val json = Platform.toJSON(map)
        assertEquals(49, json.length)
        assertTrue(json.contains("properties"))
    }

    @Test
    fun testNull() {
        val builder = JbEncoder(256)
        val reader = JbDecoder().mapBinary(builder, 0, 256)
        builder.encodeNull()
        assertEquals(ENC_MIXED_CONST_NULL, builder.getInt8(0).toInt())
        assertEquals(TYPE_NULL, reader.unitType())
        assertTrue(reader.isNull())
        assertNull(reader.readBoolean())
        assertEquals(1, reader.unitSize())
        assertEquals(1, builder.clear())
    }

    @Test
    fun testUndefined() {
        val builder = JbEncoder(256)
        val reader = JbDecoder().mapBinary(builder, 0, 256)
        builder.encodeUndefined()
        assertEquals(ENC_MIXED_CONST_UNDEFINED, reader.binary.getInt8(0).toInt())
        assertEquals(TYPE_UNDEFINED, reader.unitType())
        assertTrue(reader.isUndefined())
        assertNull(reader.readBoolean())
        assertEquals(1, reader.unitSize())
        assertEquals(1, builder.clear())
    }

    @Test
    fun testBoolean() {
        val builder = JbEncoder(256)
        val reader = JbDecoder().mapBinary(builder, 0, 256)
        builder.encodeBool(true)
        assertEquals(ENC_MIXED_CONST_TRUE, reader.binary.getInt8(0).toInt())
        assertEquals(TYPE_BOOL, reader.unitType())
        assertTrue(reader.isBool())
        assertEquals(true, reader.readBoolean())
        assertEquals(1, reader.unitSize())
        assertEquals(1, builder.clear())

        builder.encodeBool(false)
        assertEquals(ENC_MIXED_CONST_FALSE, reader.binary.getInt8(0).toInt())
        assertEquals(TYPE_BOOL, reader.unitType())
        assertTrue(reader.isBool())
        assertEquals(false, reader.readBoolean())
        assertEquals(1, reader.unitSize())
        assertEquals(1, builder.clear())
    }

    @Test
    fun testIntEncoding() {
        val builder = JbEncoder(256)
        val reader = JbDecoder().mapBinary(builder, 0, 256)
        // the values -16 to 15 should be encoded in one byte
        builder.encodeInt32(-16);
        assertTrue(reader.isInt())
        assertEquals(-16, reader.decodeInt32(0))
        assertEquals(1, reader.unitSize())
        assertEquals(1, builder.clear())
        reader.reset()

        builder.encodeInt32(15);
        assertTrue(reader.isInt())
        assertEquals(15, reader.decodeInt32(0))
        assertEquals(1, reader.unitSize())
        assertEquals(1, builder.clear())
        reader.reset()

        // the values below -16 and above 15 should be encoded in two byte
        builder.encodeInt32(-17);
        assertEquals(-17, reader.binary.getInt8(1))
        assertTrue(reader.isInt())
        assertEquals(-17, reader.decodeInt32(0))
        assertEquals(2, reader.unitSize())
        assertEquals(2, builder.clear())
        reader.reset()

        builder.encodeInt32(16);
        assertEquals(16, reader.binary.getInt8(1))
        assertTrue(reader.isInt())
        assertEquals(16, reader.decodeInt32(0))
        assertEquals(2, reader.unitSize())
        assertEquals(2, builder.clear())
        reader.reset()

        // a value less than -128 must be stored in three byte
        builder.encodeInt32(-129)
        assertEquals(-129, reader.binary.getInt16(1))
        assertTrue(reader.isInt())
        assertEquals(-129, reader.decodeInt32(0))
        assertEquals(3, reader.unitSize())
        assertEquals(3, builder.clear())
        reader.reset()

        // a value bigger than 127 must be stored in three byte
        builder.encodeInt32(128)
        assertEquals(128, reader.binary.getInt16(1))
        assertTrue(reader.isInt())
        assertEquals(128, reader.decodeInt32(0))
        assertEquals(3, reader.unitSize())
        assertEquals(3, builder.clear())
        reader.reset()

        // a value less than -32768 must be stored in five byte
        builder.encodeInt32(-32769)
        assertEquals(-32769, reader.binary.getInt32(1))
        assertTrue(reader.isInt())
        assertEquals(-32769, reader.decodeInt32(0))
        assertEquals(5, reader.unitSize())
        assertEquals(5, builder.clear())
        reader.reset()

        // a value bigger than 32767 must be stored in three byte
        builder.encodeInt32(32768)
        assertEquals(32768, reader.binary.getInt32(1))
        assertTrue(reader.isInt())
        assertEquals(32768, reader.decodeInt32(0))
        assertEquals(5, reader.unitSize())
        assertEquals(5, builder.clear())
        reader.reset()

        // Test 64-bit integers
        builder.encodeInt64(Platform.INT64_MIN_VALUE)
        assertTrue(reader.isInt())
        assertFalse(reader.isInt32())
        assertEquals(TYPE_INT, reader.unitType())
        assertEquals(Platform.INT64_MIN_VALUE, reader.decodeInt64())
        assertEquals(9, reader.unitSize())
        assertEquals(9, builder.clear())
        reader.reset()

        builder.encodeInt64(Platform.INT64_MAX_VALUE)
        assertTrue(reader.isInt())
        assertFalse(reader.isInt32())
        assertEquals(TYPE_INT, reader.unitType())
        assertEquals(Platform.INT64_MAX_VALUE, reader.decodeInt64())
        assertEquals(9, reader.unitSize())
        assertEquals(9, builder.clear())
        reader.reset()

        // This ensures that high and low bits are encoded and decoded correctly in order
        builder.encodeInt64(Platform.INT64_MIN_VALUE + 65535)
        assertTrue(reader.isInt())
        assertFalse(reader.isInt32())
        assertEquals(TYPE_INT, reader.unitType())
        assertEquals(Platform.INT64_MIN_VALUE + 65535, reader.decodeInt64())
        assertEquals(9, reader.unitSize())
        assertEquals(9, builder.clear())
        reader.reset()
    }

    @Test
    fun testFloat32Encoding() {
        val builder = JbEncoder(1024)
        val reader = JbDecoder().mapBinary(builder, 0, 1024)
        for (i in -16..15) {
            builder.encodeFloat32(i.toFloat())
            assertEquals(i, ((reader.binary.getInt8(0).toInt() shl 27) shr 27))
            assertEquals(TYPE_FLOAT, reader.unitType())
            assertTrue(reader.isFloat32())
            assertTrue(reader.isFloat64())
            assertTrue(reader.isNumber())
            assertEquals(i.toFloat(), reader.decodeFloat32(-100f))
            assertEquals(i.toDouble(), reader.decodeFloat64(-100.0))
            assertEquals(1, reader.unitSize())
            assertEquals(1, builder.clear())
            reader.reset()
        }
        // all other values are encoded in 5 byte
        builder.encodeFloat32(1.25f)
        assertEquals(ENC_MIXED_SCALAR_FLOAT32, reader.binary.getInt8(0).toInt() and 0xff)
        assertEquals(1.25f, reader.binary.getFloat32(1))
        assertTrue(reader.isFloat32())
        assertTrue(reader.isFloat64())
        assertTrue(reader.isNumber())
        assertEquals(1.25f, reader.decodeFloat32())
        assertEquals(1.25, reader.decodeFloat64())
        assertEquals(1.25, reader.decodeFloat64(readStrict = true))
        assertEquals(5, reader.unitSize())
        assertEquals(5, builder.clear())
        reader.reset()
    }

    @Test
    fun testFloat64Encoding() {
        val builder = JbEncoder(1024)
        val reader = JbDecoder().mapBinary(builder, 0, 1024)
        for (i in -16..15) {
            builder.encodeFloat64(i.toDouble())
            assertEquals(i, ((reader.binary.getInt8(0).toInt() shl 27) shr 27))
            assertTrue(reader.isFloat32())
            assertTrue(reader.isFloat64())
            assertTrue(reader.isNumber())
            assertEquals(i.toFloat(), reader.decodeFloat32())
            assertEquals(i.toDouble(), reader.decodeFloat64())
            assertEquals(1, reader.unitSize())
            assertEquals(1, builder.clear())
            reader.reset()
        }
        // all other values are encoded in 5 byte
        builder.encodeFloat64(1.25)
        assertEquals(ENC_MIXED_SCALAR_FLOAT64, reader.binary.getInt8(0).toInt() and 0xff)
        assertEquals(1.25, reader.binary.getFloat64(1))
        assertFalse(reader.isFloat32())
        assertTrue(reader.isFloat64())
        assertTrue(reader.isNumber())
        assertEquals(1.25, reader.decodeFloat64())
        assertEquals(1.25f, reader.decodeFloat32())
        assertTrue(reader.decodeFloat32(readStrict = true).isNaN())
        assertEquals(9, reader.unitSize())
        assertEquals(9, builder.clear())
    }

    @Test
    fun testEncodingTwoInts() {
        val builder = JbEncoder(1024)
        val reader = JbDecoder().mapBinary(builder, 0, 1024)

        val firstPos = builder.encodeInt32(100_000)
        assertEquals(0, firstPos)
        val secondPos = builder.encodeInt32(1)
        assertEquals(5, secondPos)
        assertEquals(6, builder.end)

        // read values
        assertTrue(reader.ok())
        assertTrue(reader.isInt())
        assertEquals(100_000, reader.decodeInt32())
        reader.pos += reader.unitSize()

        assertTrue(reader.ok())
        assertTrue(reader.isInt())
        assertEquals(1, reader.decodeInt32())
        reader.pos += reader.unitSize()

        // We're now behind the last valid byte, all values are zero, therefore they should be integer value 0.
        assertTrue(reader.isUndefined())
        assertEquals(1, reader.unitSize())

        // Let's limit the reader to the end of the builder and retry
        reader.end = builder.end
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
        val builder = JbEncoder(256)
        val reader = JbDecoder().mapBinary(builder, 0, 256)

        // should encode in 1 byte lead-in plus 1 byte character
        builder.encodeString("a")
        assertEquals(1 + 1, reader.unitSize())
        assertEquals(1 + 1, builder.clear())
        reader.reset()

        // a string with up to 12 characters will have a lead-in of only one byte
        builder.encodeString("123456789012")
        assertEquals(1 + 12, reader.unitSize())
        assertEquals(1 + 12, builder.clear())
        reader.reset()

        // a string with 61 characters, will have a two byte lead-in
        builder.encodeString("1234567890123456789012345678901234567890123456789012345678901")
        assertEquals(2, reader.unitHeaderSize())
        assertEquals(61, reader.unitPayloadSize())
        assertEquals(2 + 61, reader.unitSize())
        assertEquals(2 + 61, builder.clear())
        reader.reset()

        // This encodes the sigma character, which is unicode 931 and should therefore be encoded in two byte
        // The lead-in for this short string should be only one byte
        builder.encodeString("Σ")
        assertEquals(1 + 2, reader.unitSize())
        // We should read the value 931, minus the bias of 128, plus the two high bits being 0b10
        assertEquals((931 - 128) xor 0b1000_0000_0000_0000, builder.getInt16(1).toInt() and 0xffff)
        assertEquals(1 + 2, builder.clear())
        reader.reset()

        // This encodes the grinning face emojii, which is unicode 128512 and should therefore be encoded in three byte
        // The lead-in for this short string should still be only one byte
        builder.encodeString("\uD83D\uDE00")
        assertEquals(1 + 3, reader.unitSize())
        var unicode = (reader.binary.getInt8(1).toInt() and 0b0011_1111) shl 16
        unicode += reader.binary.getInt16(2).toInt() and 0xffff
        assertEquals(128512, unicode)
        assertEquals(1 + 3, builder.clear())
        reader.reset()
    }


    @Test
    fun testReference() {
        val builder = JbEncoder(1024)
        val reader = JbDecoder().mapBinary(builder, 0, 1024)

        // Write null reference (encoded in one byte).
        builder.encodeRef(-1, true)
        assertTrue(reader.isRef())
        assertTrue(reader.isGlobalRef())
        assertFalse(reader.isLocalRef())
        assertEquals(-1, reader.decodeRef())
        assertEquals(1, reader.unitSize())
        assertEquals(1, builder.clear())
        reader.reset()

        // Write zero reference (encoded in one byte).
        builder.encodeRef(0, true)
        assertTrue(reader.isRef())
        assertTrue(reader.isGlobalRef())
        assertFalse(reader.isLocalRef())
        assertEquals(0, reader.decodeRef())
        assertEquals(1, reader.unitSize())
        assertEquals(1, builder.clear())
        reader.reset()

        // Write two byte reference.
        builder.encodeRef(65535 + 16, false)
        assertTrue(reader.isRef())
        assertFalse(reader.isGlobalRef())
        assertTrue(reader.isLocalRef())
        assertEquals(65535 + 16, reader.decodeRef())
        assertEquals(3, reader.unitSize())
        assertEquals(3, builder.clear())
        reader.reset()

        // Write four byte reference.
        builder.encodeRef(65536 + 16, false)
        assertTrue(reader.isRef())
        assertFalse(reader.isGlobalRef())
        assertTrue(reader.isLocalRef())
        assertEquals(65536 + 16, reader.decodeRef())
        assertEquals(5, reader.unitSize())
        assertEquals(5, builder.clear())
    }

    @Test
    fun testDictionaryCreation() {
        val builder = JbEncoder()

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
        val dictView = DataViewProxy(dictArray)
        val dictReader = JbDecoder().mapBinary(dictView)
        assertEquals(TYPE_DICTIONARY, dictReader.unitType())
        // size
        dictReader.pos += 1
        assertEquals(13, dictView.getInt8(dictReader.pos))

        // id
        assertTrue(dictReader.nextUnit())
        assertTrue(dictReader.isString())
        assertEquals(dictId, dictReader.decodeString())

        // foo
        assertTrue(dictReader.nextUnit())
        assertTrue(dictReader.isString())
        assertEquals("foo", dictReader.decodeString())

        // bar
        assertTrue(dictReader.nextUnit())
        assertTrue(dictReader.isString())
        assertEquals("bar", dictReader.decodeString())

        // eof
        assertFalse(dictReader.nextUnit())

        // Test the dictionary class.
        val dict = JbDictionary().mapBinary(dictView, 0)
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
        val builder = JbEncoder(8192)
        val reader = JbDecoder().mapBinary(builder)

        // We assume that this stores three words in the local dictionary:
        // 0 = Hello
        // 1 = World
        // 2 = Again
        builder.encodeText("Hello World Hello Again")
        reader.mapBinary(builder)
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
        val builder = JbEncoder()
        builder.encodeText("Hello World Hello Test")
        val featureBytes = builder.buildFeature(null, 0)
        val feature = JbRecordDecoder(dictManager)
        feature.mapBytes(featureBytes)
        val binary = feature.reader.binary
        // We expect the following layout:
        // feature lead-in (1 byte)
        assertEquals(ENC_STRUCT, binary.getInt8(0).toInt() and ENC_STRUCT)
        assertEquals(ENC_STRUCT_VARIANT_FEATURE, binary.getInt8(0).toInt() and ENC_STRUCT_TYPE_MASK)
        // feature size (1 byte)
        assertEquals(31, binary.getInt8(1).toInt() and 0xff)
        // feature variant (1 byte)
        assertEquals(0, binary.getInt8(2).toInt() and 0xff)
        // global dictionary id null (1 byte)
        assertEquals(ENC_MIXED_CONST_NULL, binary.getInt8(3).toInt() and 0xff)
        // feature id null (1 byte)
        assertEquals(ENC_MIXED_CONST_NULL, binary.getInt8(4).toInt() and 0xff)
        // dictionary lead-in (1 byte)
        assertEquals(ENC_STRUCT, binary.getInt8(5).toInt() and 0xff and ENC_STRUCT)
        assertEquals(ENC_STRUCT_DICTIONARY, binary.getInt8(5).toInt() and 0xff and ENC_STRUCT_TYPE_MASK)
        // dictionary size (1 byte)
        assertEquals(18, binary.getInt8(6).toInt() and 0xff)
        // no variant (header-size: 2, 7+18 = 25)
        // dictionary id null (1 byte)
        assertEquals(ENC_MIXED_CONST_NULL, binary.getInt8(7).toInt() and 0xff)
        // first dictionary entry:
        // string lead-in (1 byte) with length (5) "Hello" = 6 byte total
        assertEquals(ENC_STRING, binary.getInt8(8).toInt() and ENC_MASK)
        assertEquals(5, binary.getInt8(8).toInt() and 0b0011_1111)
        // string lead-in (1 byte) with length (5) "World" = 6 byte total
        assertEquals(ENC_STRING, binary.getInt8(14).toInt() and ENC_MASK)
        assertEquals(5, binary.getInt8(14).toInt() and 0b0011_1111)
        // string lead-in (1 byte) with length (4) "Test" = 5 byte total
        assertEquals(ENC_STRING, binary.getInt8(20).toInt() and ENC_MASK)
        assertEquals(4, binary.getInt8(20).toInt() and 0b0011_1111)
        // payload of feature, being a string lead-in (1 byte)
        assertEquals(ENC_STRING, binary.getInt8(25).toInt() and 0xff and ENC_STRING)
        // the string should be:
        // lead-in (with embedded size of 8) - 1 byte
        // local-dict-ref (with 1 byte index and space) - 2 byte ("Hello")
        // local-dict-ref (with 1 byte index and space) - 2 byte ("World")
        // local-dict-ref (with 1 byte index and space) - 2 byte ("Hello")
        // local-dict-ref (with 1 byte index) - 2 byte ("Test")
        // = 9 byte total
        assertEquals(8, binary.getInt8(25).toInt() and 0b0011_1111)
        assertEquals(34, featureBytes.size)

        // the local dictionary of the feature, should currently be at position 8 (first dictionary entry)
        val localDict = feature.reader.localDict
        assertNotNull(localDict)
        assertEquals(8, localDict.reader.pos)

        // the local dictionary should end at position 25, where the payload starts
        assertEquals(25, localDict.reader.end)

        // the feature map should now be as well at position 25, the payload
        assertEquals(25, feature.reader.pos)

        // Binary encoding is right, test reading it.

        assertTrue(feature.reader.isString())
        // Currently nothing should be in the dictionary.
        assertEquals(-1, feature.reader.localDict!!.length())
        // Decode the text.
        assertEquals("Hello World Hello Test", feature.reader.decodeString())
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

//    @Test
//    fun testBigFeature() {
//        // Read the topology, then parse and serialize to remove white spaces.
//        var topologyBytes = JbCoreTest::class.java.getResource("/topology.json")!!.readBytes()
//        assertEquals(29659, topologyBytes.size)
//        var topology = String(topologyBytes, StandardCharsets.UTF_8)
//        topology = env.stringify(env.parse(topology))
//        topologyBytes = topology.toByteArray(StandardCharsets.UTF_8)
//        // After this only 16kb should be left
//        assertEquals(16073, topologyBytes.size)
//        // Convert this as string into a binary.
//        val view = JbSession.get().newDataView(ByteArray(65535))
//        val builder = JbBuilder(view)
//        builder.writeText(topology)
//        val featureArray = builder.buildFeature(null)
//        val featureView = JbSession.get().newDataView(featureArray)
//
//        // Encode a dictionary.
//        builder.reset()
//        val dictId = "test"
//        val dictArray = builder.buildDictionary(dictId)
//        val dictView = JbSession.get().newDataView(dictArray)
//
//        // Test the dictionary class.
//        val dict = JbDict().mapView(dictView, 0)
//        dict.loadAll()
//
//        val view2 = JbSession.get().newDataView(ByteArray(65535))
//        val builder2 = JbBuilder(view2, dict)
//        builder2.writeText(topology)
//        val featureArray2 = builder2.buildFeature(null)
//        val featureView2 = JbSession.get().newDataView(featureArray2)
//
//        // Simple test using low level reader.
//        val reader = JbReader().mapView(featureView, 0)
//        assertEquals(TYPE_FEATURE, reader.unitType())
//        assertEquals(11682, reader.unitSize())
//
//        // Simple test using low level reader.
//        val reader2 = JbReader().mapView(featureView2, 0)
//        assertEquals(TYPE_FEATURE, reader2.unitType())
//        assertEquals(8341, reader2.unitSize())
//
//        // Use the feature reader.
//        val feature = JbFeature(dictManager).mapView(featureView, 0)
//        assertEquals(TYPE_STRING, feature.reader.unitType())
//        assertTrue(feature.reader.isString())
//    }

    @Test
    fun testArray() {
        val builder = JbEncoder(8192)
        val arrayStart = builder.startArray()
        builder.encodeString("foo")
        builder.encodeString("bar")
        builder.endArray(arrayStart)

        val reader = JbDecoder().mapBinary(builder, 0, builder.byteLength)
        assertTrue(reader.isArray())
        // 1-byte lead-in, 1-byte size, 4 byte for string "foo" and 4 byte for string "bar"
        assertEquals(2 + 4 + 4, reader.unitSize())
        // Therefore, the size should be encoded in a byte with the value being 8.
        assertEquals(8, reader.binary.getInt8(1))
        // Read string "foo"
        reader.pos = 2
        assertTrue(reader.isString())
        assertEquals("foo", reader.decodeString())
        // Read string "bar"
        reader.pos = 2 + 4
        assertTrue(reader.isString())
        assertEquals("bar", reader.decodeString())

        // Test the array class, should basically allow the same.
        val array = JbArrayDecoder().mapBinary(builder, 0)
        assertEquals(2, array.length())
        // We should be able to read "foo"
        array.seek(0)
        assertEquals(0, array.pos())
        assertTrue(array.value().isString())
        assertEquals("foo", array.value().decodeString())

        // We should be able to read "bar"
        array.seek(1)
        assertEquals(1, array.pos())
        assertTrue(array.value().isString())
        assertEquals("bar", array.value().decodeString())

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
        val builder = JbEncoder()
        val reader = JbDecoder().mapBinary(builder)

        val mapStartPos = builder.startMap()
        builder.writeKey("foo")
        builder.encodeInt32(1)
        builder.writeKey("bar")
        builder.encodeBool(true)
        builder.endMap(mapStartPos)

        // Manual encoding checks.
        reader.mapBinary(builder, 0, builder.byteLength)
        assertTrue(reader.isMap())
        reader.pos += 1
        // We expect the size being:
        // 1 byte lead-in
        // 1 byte size (4)
        // 1 byte foo reference
        // 1 byte int
        // 1 byte bar reference
        // 1 byte int
        // = 6 byte total size, 2 byte header, 4 byte content, unitSize = 5
        assertEquals(4, builder.getInt8(reader.pos).toInt() and 0xff)

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
        val feature = JbRecordDecoder(dictManager)
        feature.mapBytes(mapData)
        assertEquals(null, feature.id())
        assertTrue(feature.reader.isMap())
        val map = JbMapDecoder()
        map.mapReader(feature.reader)

        assertTrue(map.first())
        assertTrue(map.ok())
        assertEquals("foo", map.key())
        assertEquals(1, map.value().decodeInt32())

        assertTrue(map.next())
        assertTrue(map.ok())
        assertEquals("bar", map.key())
        assertEquals(true, map.value().readBoolean())

        assertFalse(map.next())
        assertFalse(map.ok())

        // Test key selection.
        assertTrue(map.selectKey("foo"))
        assertEquals(0, map.pos())
        assertEquals(1, map.value().decodeInt32())

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
        val nowBigInt64 = Int64(nowLong)
        val builder = JbEncoder(1024)
        val reader = JbDecoder().mapBinary(builder, 0, 1024)
        builder.encodeTimestamp(nowBigInt64)
        assertEquals(ENC_MIXED_SCALAR_TIMESTAMP, builder.getInt8(0).toInt())
        assertEquals(TYPE_TIMESTAMP, reader.unitType())
        assertEquals((nowLong ushr 32).toShort(), builder.getInt16(reader.pos + 1))
        assertEquals(nowLong.toInt(), builder.getInt32(reader.pos + 3))
        assertTrue(reader.isTimestamp())
        val ts = reader.decodeTimestamp()
        assertEquals(nowLong, ts.toLong())
        assertEquals(7, reader.unitSize())
        assertEquals(7, builder.clear())
    }

    @Test
    fun testTimestamp() {
        val millis = Int64(1707985967244)
        val ts = Timestamp.fromMillis(millis)
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
        val builder = JbEncoder()
        val featureJson = """{"id":"bar"}"""
        val featureMap = Platform.fromJSON(featureJson) as PlatformMap
        val featureBytes = builder.buildFeatureFromMap(featureMap.proxy(AnyObject::class))
        val feature = JbRecordDecoder(dictManager)
        feature.mapBytes(featureBytes)
        assertEquals("bar", feature.id())
    }

    @Test
    fun testSelectPath() {
        val builder = JbEncoder()
        val featureJson = """{"id":"bar","properties":{"foo": "hello","bar":[0,1,2,3,4]}}"""
        val featureMap = Platform.fromJSON(featureJson) as PlatformMap
        val featureBytes = builder.buildFeatureFromMap(featureMap.proxy(AnyObject::class))
        val feature = JbFeatureDecoder(dictManager)
        feature.mapBytes(featureBytes)
        assertTrue(feature.selectPath("properties", "foo"))
        assertEquals("hello", feature.reader.decodeValue())

        assertTrue(feature.selectPath("properties", "bar", 0))
        assertEquals(0, feature.reader.decodeValue())

        assertTrue(feature.selectPath("properties", "bar", 1))
        assertEquals(1, feature.reader.decodeValue())

        assertTrue(feature.selectPath("properties", "bar", 2))
        assertEquals(2, feature.reader.decodeValue())

        assertTrue(feature.selectPath("properties", "bar", 3))
        assertEquals(3, feature.reader.decodeValue())

        assertTrue(feature.selectPath("properties", "bar", 4))
        assertEquals(4, feature.reader.decodeValue())
    }
}
