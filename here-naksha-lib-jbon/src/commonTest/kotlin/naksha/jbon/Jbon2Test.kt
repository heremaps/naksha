package naksha.jbon

import naksha.base.*
import naksha.base.TupleNumber
import naksha.base.Version
import kotlin.math.PI
import kotlin.test.*

/**
 * Comprehensive JBON2 codec tests.
 *
 * Organisation:
 *  - [singleEncode] / [singleDecode]: low-level helpers that bypass the Tuple wrapper so we can
 *    inspect exact byte counts and lead-in bytes.
 *  - [roundTrip]: encodes a JSON feature string through [JbEncoder2.buildTupleFromMap] and decodes
 *    it back through [JbDecoder2]; used for full integration tests.
 *  - [toJson]: encodes through the Tuple path and converts the decoded [AnyObject] to a JSON string
 *    via [Platform.toJSON], verifying JSON round-trip fidelity.
 *
 * Logical-byte types (BYTE_ARRAY / BINARY struct) are intentionally not tested here.
 */
class Jbon2Test {

    // -----------------------------------------------------------------------
    // Low-level helpers
    // -----------------------------------------------------------------------

    /**
     * Encode a single value via [JbEncoder2.encodeValue] and return the encoder so tests can
     * inspect byte count ([JbEncoder2.end]) and individual bytes ([JbEncoder2.getInt8]).
     */
    private fun singleEncode(value: Any?): JbEncoder2 {
        val enc = JbEncoder2()
        enc.encodeValue(value)
        return enc
    }

    /**
     * Decode the first value from the bytes currently in [enc] using a bare [JbDecoder2]
     * (no Tuple wrapper, no local book).
     */
    private fun singleDecode(enc: JbEncoder2): Any? {
        val dec = JbDecoder2()
        val bin = Binary()
        bin.view = Platform.newDataView(ByteArray(enc.end) { enc.getInt8(it) })
        bin.end = enc.end
        dec.view = bin
        dec.offset = 0
        dec.end = enc.end
        return dec.decodeValueAt(0)
    }

    /** Convenience: encode and decode a single value. */
    private fun scalarRoundTrip(value: Any?): Any? {
        val enc = singleEncode(value)
        return singleDecode(enc)
    }

    /**
     * Full Tuple round-trip: encode the given JSON feature string, decode it, and return the
     * feature [AnyObject].  Also verifies the `@JB\x02` file header.
     */
    private fun roundTrip(json: String): AnyObject {
        val map = (Platform.fromJSON(json) as PlatformMap).proxy(AnyObject::class)
        val enc = JbEncoder2()
        val bytes = enc.buildTupleFromMap(map)
        assertEquals('@'.code.toByte(), bytes[0], "header[0] must be '@'")
        assertEquals('J'.code.toByte(),  bytes[1], "header[1] must be 'J'")
        assertEquals('B'.code.toByte(),  bytes[2], "header[2] must be 'B'")
        assertEquals(0x02.toByte(),       bytes[3], "header[3] must be version 0x02")
        val dec = JbDecoder2()
        dec.mapBytes(bytes)
        return dec.toAnyObject()
    }

    /**
     * Encode → Tuple → decode → [Platform.toJSON]; returns the JSON string.
     */
    private fun toJson(json: String): String = Platform.toJSON(roundTrip(json))

    private fun structHeaderSize(lead: Int): Int = when (lead and JB2_STRUCT_SIZE_MASK) {
        JB2_STRUCT_SIZE0 -> 1
        JB2_STRUCT_SIZE8 -> 2
        JB2_STRUCT_SIZE16 -> 3
        else -> 5
    }

    // -----------------------------------------------------------------------
    // Tiny integer  (class 01, type-bit 0) — exactly 1 byte
    // -----------------------------------------------------------------------

    @Test
    fun testTinyIntMinValue() {
        // -16 is the smallest value that fits in 5-bit two's-complement (10000₂).
        val enc = singleEncode(-16)
        assertEquals(1, enc.end, "tiny int -16 must be exactly 1 byte")
        val lead = enc.getInt8(0).toInt() and 0xff
        assertEquals(JB2_CLASS_TINY, lead and JB2_CLASS_MASK, "class must be TINY")
        assertEquals(JB2_TINY_INT,   lead and JB2_TINY_MASK,  "tiny type must be INT")
        assertEquals(-16, singleDecode(enc))
    }

    @Test
    fun testTinyIntMaxValue() {
        val enc = singleEncode(15)
        assertEquals(1, enc.end, "tiny int 15 must be exactly 1 byte")
        assertEquals(15, singleDecode(enc))
    }

    @Test
    fun testTinyIntZero() {
        val enc = singleEncode(0)
        assertEquals(1, enc.end)
        assertEquals(0, singleDecode(enc))
    }

    @Test
    fun testTinyIntNegativeOne() {
        val enc = singleEncode(-1)
        assertEquals(1, enc.end)
        assertEquals(-1, singleDecode(enc))
    }

    @Test
    fun testTinyIntAllValues() {
        // Exhaustively verify every value in [-16, 15].
        for (v in -16..15) {
            val enc = singleEncode(v)
            assertEquals(1, enc.end, "tiny int $v must be 1 byte")
            assertEquals(v, singleDecode(enc), "tiny int round-trip failed for $v")
        }
    }

    // -----------------------------------------------------------------------
    // 8-bit integer  (lead-in JB2_INT8 + 1 payload byte) — 2 bytes total
    // -----------------------------------------------------------------------

    @Test
    fun testInt8FirstAboveTiny() {
        // -17 is the first negative value that falls outside the tiny range.
        val enc = singleEncode(-17)
        assertEquals(2, enc.end, "Int8 -17 must be exactly 2 bytes")
        assertEquals(JB2_INT8.toByte(), enc.getInt8(0), "lead-in must be INT8")
        assertEquals(-17, singleDecode(enc))
    }

    @Test
    fun testInt8FirstBelowTiny() {
        // 16 is the first positive value above the tiny range.
        val enc = singleEncode(16)
        assertEquals(2, enc.end, "Int8 16 must be exactly 2 bytes")
        assertEquals(JB2_INT8.toByte(), enc.getInt8(0), "lead-in must be INT8")
        assertEquals(16, singleDecode(enc))
    }

    @Test
    fun testInt8ExtremeMin() {
        val enc = singleEncode(-128)
        assertEquals(2, enc.end, "Int8 -128 must be 2 bytes")
        assertEquals(-128, singleDecode(enc))
    }

    @Test
    fun testInt8ExtremeMax() {
        val enc = singleEncode(127)
        assertEquals(2, enc.end, "Int8 127 must be 2 bytes")
        assertEquals(127, singleDecode(enc))
    }

    // -----------------------------------------------------------------------
    // 16-bit integer — 3 bytes total
    // -----------------------------------------------------------------------

    @Test
    fun testInt16FirstBelow() {
        // -129 is the first value that doesn't fit in Int8.
        val enc = singleEncode(-129)
        assertEquals(3, enc.end, "Int16 -129 must be exactly 3 bytes")
        assertEquals(JB2_INT16.toByte(), enc.getInt8(0), "lead-in must be INT16")
        assertEquals(-129, singleDecode(enc))
    }

    @Test
    fun testInt16FirstAbove() {
        val enc = singleEncode(128)
        assertEquals(3, enc.end, "Int16 128 must be exactly 3 bytes")
        assertEquals(128, singleDecode(enc))
    }

    @Test
    fun testInt16ExtremeMin() {
        val enc = singleEncode(Short.MIN_VALUE.toInt())
        assertEquals(3, enc.end)
        assertEquals(Short.MIN_VALUE.toInt(), singleDecode(enc))
    }

    @Test
    fun testInt16ExtremeMax() {
        val enc = singleEncode(Short.MAX_VALUE.toInt())
        assertEquals(3, enc.end)
        assertEquals(Short.MAX_VALUE.toInt(), singleDecode(enc))
    }

    // -----------------------------------------------------------------------
    // 32-bit integer — 5 bytes total
    // -----------------------------------------------------------------------

    @Test
    fun testInt32FirstBelow() {
        // -32769 is the first value that doesn't fit in Int16.
        val enc = singleEncode(-32769)
        assertEquals(5, enc.end, "Int32 -32769 must be exactly 5 bytes")
        assertEquals(JB2_INT32.toByte(), enc.getInt8(0), "lead-in must be INT32")
        assertEquals(-32769, singleDecode(enc))
    }

    @Test
    fun testInt32FirstAbove() {
        val enc = singleEncode(32768)
        assertEquals(5, enc.end)
        assertEquals(32768, singleDecode(enc))
    }

    @Test
    fun testInt32ExtremeMin() {
        val enc = singleEncode(Int.MIN_VALUE)
        assertEquals(5, enc.end, "Int.MIN_VALUE must be 5 bytes")
        assertEquals(Int.MIN_VALUE, singleDecode(enc))
    }

    @Test
    fun testInt32ExtremeMax() {
        val enc = singleEncode(Int.MAX_VALUE)
        assertEquals(5, enc.end, "Int.MAX_VALUE must be 5 bytes")
        assertEquals(Int.MAX_VALUE, singleDecode(enc))
    }

    // -----------------------------------------------------------------------
    // 64-bit integer — 9 bytes total (only for values outside Int32 range)
    // -----------------------------------------------------------------------

    @Test
    fun testInt64FirstAboveInt32Max() {
        val v = Int.MAX_VALUE.toLong() + 1L
        val enc = singleEncode(v)
        assertEquals(9, enc.end, "Int64 above Int32 max must be 9 bytes")
        assertEquals(JB2_INT64.toByte(), enc.getInt8(0), "lead-in must be INT64")
        val decoded = singleDecode(enc)
        assertIs<Long>(decoded, "decoded value must be Long")
        assertEquals(Int.MAX_VALUE.toLong() + 1L, decoded)
    }

    @Test
    fun testInt64FirstBelowInt32Min() {
        val v = Int.MIN_VALUE.toLong() - 1L
        val enc = singleEncode(v)
        assertEquals(9, enc.end)
        val decoded = singleDecode(enc)
        assertIs<Long>(decoded)
        assertEquals(Int.MIN_VALUE.toLong() - 1L, decoded)
    }

    @Test
    fun testInt64MaxValue() {
        val enc = singleEncode(Long.MAX_VALUE)
        assertEquals(9, enc.end)
        val decoded = singleDecode(enc) as Long
        assertEquals(Long.MAX_VALUE, decoded)
    }

    @Test
    fun testInt64MinValue() {
        val enc = singleEncode(Long.MIN_VALUE)
        assertEquals(9, enc.end)
        val decoded = singleDecode(enc) as Long
        assertEquals(Long.MIN_VALUE, decoded)
    }

    /** Values inside Int32 range must NOT use the 9-byte Int64 encoding. */
    @Test
    fun testInt64FallsBackToInt32ForSmallValues() {
        val enc = singleEncode(Int.MAX_VALUE.toLong())
        assertEquals(5, enc.end, "Int.MAX_VALUE as Long should be encoded as Int32 (5 bytes), not Int64")
        assertEquals(Int.MAX_VALUE, singleDecode(enc))
    }

    // -----------------------------------------------------------------------
    // Tiny float (class 01, type-bit 1) — exactly 1 byte, decoded as Double
    // -----------------------------------------------------------------------

    @Test
    fun testTinyFloatZero() {
        val enc = singleEncode(0.0)
        assertEquals(1, enc.end, "tiny float 0.0 must be 1 byte")
        val lead = enc.getInt8(0).toInt() and 0xff
        assertEquals(JB2_CLASS_TINY, lead and JB2_CLASS_MASK)
        assertEquals(JB2_TINY_FLOAT, lead and JB2_TINY_MASK, "type-flag must be FLOAT")
        assertEquals(0.0, singleDecode(enc))
    }

    @Test
    fun testTinyFloatMin() {
        // -16.0 is the minimum whole number that fits in the 5-bit tiny-float encoding.
        val enc = singleEncode(-16.0)
        assertEquals(1, enc.end, "tiny float -16.0 must be 1 byte")
        assertEquals(-16.0, singleDecode(enc))
    }

    @Test
    fun testTinyFloatMax() {
        val enc = singleEncode(15.0)
        assertEquals(1, enc.end, "tiny float 15.0 must be 1 byte")
        assertEquals(15.0, singleDecode(enc))
    }

    @Test
    fun testTinyFloatNegativeOne() {
        val enc = singleEncode(-1.0)
        assertEquals(1, enc.end)
        assertEquals(-1.0, singleDecode(enc))
    }

    @Test
    fun testTinyFloatAllWholeNumbers() {
        for (v in -16..15) {
            val enc = singleEncode(v.toDouble())
            assertEquals(1, enc.end, "tiny float $v.0 must be 1 byte")
            assertEquals(v.toDouble(), singleDecode(enc), "tiny float round-trip failed for $v.0")
        }
    }

    /** Non-whole number must NOT use the tiny encoding. */
    @Test
    fun testTinyFloatFractionalNotTiny() {
        val enc = singleEncode(1.5)
        assertNotEquals(1, enc.end, "1.5 must NOT be encoded as tiny (not a whole number)")
    }

    /** Value just outside the tiny range must NOT be tiny. */
    @Test
    fun testTinyFloatBoundaryExceeded() {
        val enc = singleEncode(16.0)
        assertNotEquals(1, enc.end, "16.0 is outside tiny range and must not be 1 byte")
    }

    // -----------------------------------------------------------------------
    // Float32 — 5 bytes (lead-in + 4 payload bytes)
    // The encoder narrows a Double to Float32 when Platform.canBeFloat32 is true.
    // -----------------------------------------------------------------------

    @Test
    fun testFloat32AutoNarrowing() {
        // 3.5 = 7/2 is representable exactly in float32 (lower 29 mantissa bits are zero).
        assertTrue(Platform.canBeFloat32(3.5), "3.5 must be flag as lossless float32")
        val enc = singleEncode(3.5)
        assertEquals(5, enc.end, "3.5 must be encoded as float32 (5 bytes)")
        assertEquals(JB2_FLOAT32.toByte(), enc.getInt8(0), "lead-in must be FLOAT32")
        assertEquals(3.5, (singleDecode(enc) as Number).toDouble())
    }

    @Test
    fun testFloat32NegativeHalf() {
        assertTrue(Platform.canBeFloat32(-0.5))
        val enc = singleEncode(-0.5)
        assertEquals(5, enc.end)
        assertEquals(-0.5, (singleDecode(enc) as Number).toDouble())
    }

    @Test
    fun testFloat32FloatMaxValue() {
        // Float.MAX_VALUE is exactly representable in float32 by definition.
        val v = Float.MAX_VALUE.toDouble()
        assertTrue(Platform.canBeFloat32(v))
        val enc = singleEncode(v)
        assertEquals(5, enc.end, "Float.MAX_VALUE must be 5 bytes")
        val decoded = (singleDecode(enc) as Number).toDouble()
        assertEquals(v, decoded, 1.0e30, "Float.MAX_VALUE must round-trip within tolerance")
    }

    // -----------------------------------------------------------------------
    // Float64 — 9 bytes (lead-in + 8 payload bytes)
    // Must be used when the double cannot be represented losslessly as float32.
    // -----------------------------------------------------------------------

    @Test
    fun testFloat64Pi() {
        assertFalse(Platform.canBeFloat32(PI), "PI must NOT be representable as float32")
        val enc = singleEncode(PI)
        assertEquals(9, enc.end, "PI must be encoded as float64 (9 bytes)")
        assertEquals(JB2_FLOAT64.toByte(), enc.getInt8(0), "lead-in must be FLOAT64")
        assertEquals(PI, (singleDecode(enc) as Number).toDouble())
    }

    @Test
    fun testFloat64LargeExponent() {
        // 1e300 is far outside float32 exponent range.
        val v = 1.0e300
        assertFalse(Platform.canBeFloat32(v))
        val enc = singleEncode(v)
        assertEquals(9, enc.end)
        assertEquals(v, (singleDecode(enc) as Number).toDouble())
    }

    @Test
    fun testFloat64NegativeLarge() {
        val v = -1.0e200
        assertFalse(Platform.canBeFloat32(v))
        val enc = singleEncode(v)
        assertEquals(9, enc.end)
        assertEquals(v, (singleDecode(enc) as Number).toDouble())
    }

    @Test
    fun testFloat64DoubleMax() {
        val v = Double.MAX_VALUE
        assertFalse(Platform.canBeFloat32(v))
        val enc = singleEncode(v)
        assertEquals(9, enc.end)
        assertEquals(v, (singleDecode(enc) as Number).toDouble())
    }

    @Test
    fun testFloat32NotUsedForImpreciseDouble() {
        // A double value whose lower mantissa bits are set must use float64.
        // 0.3 is a classic example of a value that cannot be exact in binary.
        // The important assertion is that round-trip fidelity is preserved.
        val v = 0.3
        val enc = singleEncode(v)
        val decoded = (singleDecode(enc) as Number).toDouble()
        if (Platform.canBeFloat32(v)) {
            // If the platform considers this float32-safe, just verify round-trip.
            assertEquals(v.toFloat().toDouble(), decoded)
        } else {
            // float64 must preserve exact bits.
            assertEquals(v, decoded)
        }
    }

    // -----------------------------------------------------------------------
    // Boolean & null — 1 byte each
    // -----------------------------------------------------------------------

    @Test
    fun testBooleanTrue() {
        val enc = singleEncode(true)
        assertEquals(1, enc.end)
        assertEquals(JB2_TRUE.toByte(), enc.getInt8(0))
        assertEquals(true, singleDecode(enc))
    }

    @Test
    fun testBooleanFalse() {
        val enc = singleEncode(false)
        assertEquals(1, enc.end)
        assertEquals(JB2_FALSE.toByte(), enc.getInt8(0))
        assertEquals(false, singleDecode(enc))
    }

    @Test
    fun testNull() {
        val enc = singleEncode(null)
        assertEquals(1, enc.end)
        assertEquals(JB2_NULL.toByte(), enc.getInt8(0))
        assertNull(singleDecode(enc))
    }

    // -----------------------------------------------------------------------
    // String — verbatim path (encodeString).
    // Header format: `10ss_ssss` where ssssss is size (0–60 direct) or
    // JB2_STRING_SIZE_BYTE (61→+1 byte), SHORT (62→+2 bytes), INT (63→+4 bytes).
    // -----------------------------------------------------------------------

    /** Helper: encode [s] verbatim and decode it back. */
    private fun stringRoundTrip(s: String): String {
        val enc = JbEncoder2()
        enc.encodeString(s)
        val dec = JbDecoder2()
        val bin = Binary()
        bin.view = Platform.newDataView(ByteArray(enc.end) { enc.getInt8(it) })
        bin.end = enc.end
        dec.view = bin; dec.offset = 0; dec.end = enc.end
        return dec.decodeValueAt(0) as String
    }

    @Test
    fun testEmptyString() {
        val enc = JbEncoder2()
        enc.encodeString("")
        // Empty string: lead = 0x80 (class STRING, inline size = 0)
        assertEquals(1, enc.end, "empty string must be exactly 1 byte")
        assertEquals(JB2_CLASS_STRING.toByte(), enc.getInt8(0))
        assertEquals("", stringRoundTrip(""))
    }

    @Test
    fun testStringTwoAscii() {
        // "hi" → body = 2 bytes, size ≤ 60 → 1-byte header → total 3 bytes
        val enc = JbEncoder2()
        enc.encodeString("hi")
        assertEquals(3, enc.end, "\"hi\" must be 3 bytes (1 header + 2 body)")
        assertEquals((JB2_CLASS_STRING or 2).toByte(), enc.getInt8(0))
        assertEquals("hi", stringRoundTrip("hi"))
    }

    @Test
    fun testStringExactly60ByteBody() {
        val s = "a".repeat(60) // 60 ASCII chars → body = 60 bytes (≤ 60 → inline size)
        val enc = JbEncoder2()
        enc.encodeString(s)
        assertEquals(61, enc.end, "60-char string must be 61 bytes (1 header + 60 body)")
        assertEquals((JB2_CLASS_STRING or 60).toByte(), enc.getInt8(0))
        assertEquals(s, stringRoundTrip(s))
    }

    @Test
    fun testStringExactly61ByteBody() {
        // 61 bytes → first size that requires JB2_STRING_SIZE_BYTE extended header (2 bytes).
        // The size byte stores (61 - bias 61) = 0.
        val s = "b".repeat(61)
        val enc = JbEncoder2()
        enc.encodeString(s)
        assertEquals(63, enc.end, "61-char string must be 63 bytes (2 header + 61 body)")
        assertEquals((JB2_CLASS_STRING or JB2_STRING_SIZE_BYTE).toByte(), enc.getInt8(0))
        assertEquals(0.toByte(), enc.getInt8(1), "size byte for body=61 must be 0")
        assertEquals(s, stringRoundTrip(s))
    }

    @Test
    fun testStringExactly316ByteBody() {
        // 316 bytes → last size that fits in JB2_STRING_SIZE_BYTE; size byte = 316 - 61 = 255.
        val s = "c".repeat(316)
        val enc = JbEncoder2()
        enc.encodeString(s)
        assertEquals(318, enc.end, "316-char string must be 318 bytes (2 header + 316 body)")
        assertEquals(255.toByte(), enc.getInt8(1), "size byte for body=316 must be 255")
        assertEquals(s, stringRoundTrip(s))
    }

    @Test
    fun testStringExactly317ByteBody() {
        // 317 bytes → first size requiring JB2_STRING_SIZE_SHORT (3-byte header).
        val s = "d".repeat(317)
        val enc = JbEncoder2()
        enc.encodeString(s)
        assertEquals(320, enc.end, "317-char string must be 320 bytes (3 header + 317 body)")
        assertEquals((JB2_CLASS_STRING or JB2_STRING_SIZE_SHORT).toByte(), enc.getInt8(0))
        assertEquals(s, stringRoundTrip(s))
    }

    // -----------------------------------------------------------------------
    // String — Unicode code points
    // -----------------------------------------------------------------------

    @Test
    fun testStringCodePoint2ByteLow() {
        // U+0080 (first character outside ASCII) encodes as a 2-byte code point.
        val s = "\u0080"
        assertEquals(3, JbEncoder2().also { it.encodeString(s) }.end, "U+0080 string must be 3 bytes (1 header + 2 body)")
        assertEquals(s, stringRoundTrip(s))
    }

    @Test
    fun testStringCodePoint2ByteHigh() {
        // U+207F (last character in 2-byte range: max biased = 8319, bias=128 → max cp=8319+128-1? check)
        // 2-byte range: 128..8319. U+2080 = 8320 → 3-byte. Test U+207F = 8319.
        val s = "\u207F" // superscript n, U+207F = 8319
        assertEquals(3, JbEncoder2().also { it.encodeString(s) }.end, "U+207F string must be 3 bytes")
        assertEquals(s, stringRoundTrip(s))
    }

    @Test
    fun testStringCodePoint3BytheLow() {
        // U+2080 is the first character in the 3-byte range.
        val s = "\u2080"
        assertEquals(4, JbEncoder2().also { it.encodeString(s) }.end, "U+2080 string must be 4 bytes (1 header + 3 body)")
        assertEquals(s, stringRoundTrip(s))
    }

    @Test
    fun testStringEuroSign() {
        // U+20AC '€' is in the 3-byte range (biased encoding).
        val s = "€"
        assertEquals(4, JbEncoder2().also { it.encodeString(s) }.end, "€ must be 4 bytes (1 header + 3 body)")
        assertEquals(s, stringRoundTrip(s))
    }

    @Test
    fun testStringMixedCodePoints() {
        // ASCII + 2-byte + 3-byte code points in a single string.
        val s = "A\u0080€Z"
        assertEquals(s, stringRoundTrip(s))
    }

    @Test
    fun testStringSurrogatePair() {
        // U+1F600 😀 is encoded as a surrogate pair (U+D83D U+DE00) in the JVM String.
        val s = "\uD83D\uDE00"
        assertEquals(s, stringRoundTrip(s))
    }

    @Test
    fun testStringAllAscii() {
        // Printable ASCII (32–126).
        val s = (32..126).map { it.toChar() }.joinToString("")
        assertEquals(s, stringRoundTrip(s))
    }

    // -----------------------------------------------------------------------
    // String compression — encodeText (word splitting + string-references)
    // -----------------------------------------------------------------------

    @Test
    fun testEncodeTextSimpleWord() {
        // "hello" is a 5-letter word (≥3 chars) that qualifies for interning.
        // The encoder interns it on first occurrence and writes a string-reference instead of
        // the 5 raw bytes — so both occurrences produce the same compact 3-byte encoding
        // (1-byte string header + 2-byte string-ref).
        // That is smaller than the 6-byte verbatim encoding (1 header + 5 ASCII bytes).
        val enc = JbEncoder2()
        val pos1 = enc.encodeText("hello")
        val size1 = enc.end - pos1 // first occurrence → already a string-ref
        assertTrue(size1 < 6, "first occurrence of 'hello' must be compressed to <6 bytes (was $size1)")
        val pos2 = enc.encodeText("hello")
        val size2 = enc.end - pos2 // second occurrence → also a string-ref
        assertEquals(size1, size2, "both occurrences of 'hello' must produce the same compressed size")
    }

    @Test
    fun testEncodeTextRoundTrip() {
        // "hello world" must survive a full Tuple round-trip (string-ref for "hello" and "world").
        val f = roundTrip("""{"id":"hello","properties":{"a":"hello world","b":"hello world"}}""")
        assertEquals("hello world", (f["properties"] as AnyObject)["a"])
        assertEquals("hello world", (f["properties"] as AnyObject)["b"])
    }

    @Test
    fun testEncodeTextWithUnderscoreSeparator() {
        val f = roundTrip("""{"id":"x","properties":{"k":"first_second","k2":"first_second"}}""")
        val p = f["properties"] as AnyObject
        assertEquals("first_second", p["k"])
        assertEquals("first_second", p["k2"])
    }

    @Test
    fun testEncodeTextWithColonSeparator() {
        val f = roundTrip("""{"id":"x","properties":{"k":"urn:here:type","k2":"urn:here:type"}}""")
        val p = f["properties"] as AnyObject
        assertEquals("urn:here:type", p["k"])
        assertEquals("urn:here:type", p["k2"])
    }

    @Test
    fun testEncodeTextNonWordChars() {
        // Digits/symbols are not "word" chars and fall through to verbatim encoding.
        val s = "12345!@#"
        val f = roundTrip("""{"id":"x","properties":{"v":${Platform.toJSON(s)}}}""")
        assertEquals(s, (f["properties"] as AnyObject)["v"])
    }

    @Test
    fun testEncodeTextEmptyString() {
        // An empty string must round-trip correctly even through the text path.
        val f = roundTrip("""{"id":"x","properties":{"e":""}}""")
        assertEquals("", (f["properties"] as AnyObject)["e"])
    }

    @Test
    fun testEncodeTextLongRepeatedString() {
        // A string appearing many times in the feature.
        val word = "coordinate"
        val json = """{"id":"x","properties":{"a":"$word","b":"$word","c":"$word","d":"$word"}}"""
        val f = roundTrip(json)
        val p = f["properties"] as AnyObject
        assertEquals(word, p["a"])
        assertEquals(word, p["b"])
        assertEquals(word, p["c"])
        assertEquals(word, p["d"])
    }

    @Test
    fun testEncodeTextMixedLanguage() {
        // String combining ASCII words and non-ASCII characters.
        val s = "München city"
        val f = roundTrip("""{"id":"x","properties":{"city":${Platform.toJSON(s)}}}""")
        assertEquals(s, (f["properties"] as AnyObject)["city"])
    }

    @Test
    fun testEncodeTextVeryLongString() {
        // 200-char string that exercises the SHORT (2-byte) size header for compressed content.
        val s = "word ".repeat(40) // 200 chars; "word" interned, space is ADD_SPACE
        val f = roundTrip("""{"id":"x","properties":{"t":${Platform.toJSON(s)}}}""")
        assertEquals(s, (f["properties"] as AnyObject)["t"])
    }

    // -----------------------------------------------------------------------
    // Full Tuple round-trips — feature-level integration
    // -----------------------------------------------------------------------

    @Test
    fun testIdOnly() {
        val f = roundTrip("""{"id":"bar"}""")
        assertEquals("bar", f["id"])
    }

    @Test
    fun testTinyIntEdgesInFeature() {
        val f = roundTrip("""{"id":"x","properties":{"mn":-16,"mx":15,"z":0,"n1":-1,"p1":1}}""")
        val p = f["properties"] as AnyObject
        assertEquals(-16, p["mn"])
        assertEquals(15,  p["mx"])
        assertEquals(0,   p["z"])
        assertEquals(-1,  p["n1"])
        assertEquals(1,   p["p1"])
    }

    @Test
    fun testInt8EdgeCasesInFeature() {
        val f = roundTrip("""{"id":"x","properties":{"a":-17,"b":16,"c":-128,"d":127}}""")
        val p = f["properties"] as AnyObject
        assertEquals(-17, p["a"])
        assertEquals(16,  p["b"])
        assertEquals(-128, p["c"])
        assertEquals(127, p["d"])
    }

    @Test
    fun testInt16EdgeCasesInFeature() {
        val f = roundTrip("""{"id":"x","properties":{"a":-32768,"b":32767,"c":-129,"d":128}}""")
        val p = f["properties"] as AnyObject
        assertEquals(-32768, p["a"])
        assertEquals(32767,  p["b"])
        assertEquals(-129,   p["c"])
        assertEquals(128,    p["d"])
    }

    @Test
    fun testInt32EdgeCasesInFeature() {
        val f = roundTrip("""{"id":"x","properties":{"a":-32769,"b":32768,"c":${Int.MAX_VALUE},"d":${Int.MIN_VALUE}}}""")
        val p = f["properties"] as AnyObject
        assertEquals(-32769,       p["a"])
        assertEquals(32768,        p["b"])
        assertEquals(Int.MAX_VALUE, p["c"])
        assertEquals(Int.MIN_VALUE, p["d"])
    }

    @Test
    fun testFloatsInFeature() {
        val f = roundTrip("""{"id":"x","properties":{"f32":3.5,"f64":3.141592653589793}}""")
        val p = f["properties"] as AnyObject
        assertEquals(3.5, (p["f32"] as Number).toDouble())
        assertEquals(PI, (p["f64"] as Number).toDouble())
    }

    @Test
    fun testNestedAndArray() {
        val f = roundTrip("""{"id":"a","properties":{"arr":[0,1,2,3,4],"nested":{"k":"v"}}}""")
        val p = f["properties"] as AnyObject
        val arr = p["arr"] as AnyList
        assertEquals(5, arr.size)
        assertEquals(0, arr[0])
        assertEquals(4, arr[4])
        assertEquals("v", (p["nested"] as AnyObject)["k"])
    }

    @Test
    fun testLargeArrayWithMixedTypes() {
        val json = """{"id":"x","properties":{"arr":[-16,-1,0,1,15,-17,16,-128,127,-129,128,-32768,32767,true,false,null,"str"]}}"""
        val f = roundTrip(json)
        val arr = (f["properties"] as AnyObject)["arr"] as AnyList
        assertEquals(17, arr.size)
        assertEquals(-16,  arr[0]);  assertEquals(-1, arr[1])
        assertEquals(0,    arr[2]);  assertEquals(1,  arr[3])
        assertEquals(15,   arr[4]);  assertEquals(-17, arr[5])
        assertEquals(16,   arr[6]);  assertEquals(-128, arr[7])
        assertEquals(127,  arr[8]);  assertEquals(-129, arr[9])
        assertEquals(128,  arr[10]); assertEquals(-32768, arr[11])
        assertEquals(32767, arr[12]); assertEquals(true, arr[13])
        assertEquals(false, arr[14]); assertNull(arr[15])
        assertEquals("str", arr[16])
    }

    @Test
    fun testStringRepetitionInFeature() {
        val f = roundTrip("""{"id":"hello","properties":{"a":"hello","b":"hello","c":"world"}}""")
        val p = f["properties"] as AnyObject
        assertEquals("hello", p["a"])
        assertEquals("hello", p["b"])
        assertEquals("world", p["c"])
    }

    // -----------------------------------------------------------------------
    // TupleNumber — 33 bytes (lead-in 0000_1111 + 8+4+4+8+8 payload)
    // -----------------------------------------------------------------------

    @Test
    fun testTupleNumberHead() {
        val tn = TupleNumber(0L, 0, 0, 0L, Version.HEAD.number)
        val enc = singleEncode(tn)
        assertEquals(33, enc.end, "TupleNumber must be exactly 33 bytes")
        assertEquals(JB2_TUPLE_NUMBER.toByte(), enc.getInt8(0), "lead-in must be TUPLE_NUMBER")
        val decoded = singleDecode(enc) as TupleNumber
        assertEquals(tn.databaseNumber, decoded.databaseNumber)
        assertEquals(tn.catalogNumber, decoded.catalogNumber)
        assertEquals(tn.collectionNumber, decoded.collectionNumber)
        assertEquals(tn.featureNumber, decoded.featureNumber)
        assertEquals(tn.version, decoded.version)
    }

    @Test
    fun testTupleNumberRealisticValues() {
        // Simulate realistic values: large 64-bit storage number, negative catalog/collection from MD5 hash
        val tn = TupleNumber(
            databaseNumber = 0x8000000012345678UL.toLong(),
            catalogNumber = -1234567890,
            collectionNumber = -987654321,
            featureNumber = 0x80000000ABCDEF00UL.toLong(),
            version = 42L
        )
        val enc = singleEncode(tn)
        assertEquals(33, enc.end)
        assertEquals(JB2_TUPLE_NUMBER.toByte(), enc.getInt8(0))
        val decoded = singleDecode(enc) as TupleNumber
        assertEquals(tn.databaseNumber, decoded.databaseNumber)
        assertEquals(tn.catalogNumber, decoded.catalogNumber)
        assertEquals(tn.collectionNumber, decoded.collectionNumber)
        assertEquals(tn.featureNumber, decoded.featureNumber)
        assertEquals(tn.version, decoded.version)
    }

    @Test
    fun testTupleNumberAllExtremes() {
        val tn = TupleNumber(
            databaseNumber = Long.MIN_VALUE,
            catalogNumber = Int.MIN_VALUE,
            collectionNumber = Int.MAX_VALUE,
            featureNumber = Long.MAX_VALUE,
            version = Long.MIN_VALUE
        )
        val enc = singleEncode(tn)
        assertEquals(33, enc.end)
        val decoded = singleDecode(enc) as TupleNumber
        assertEquals(tn.databaseNumber, decoded.databaseNumber)
        assertEquals(tn.catalogNumber, decoded.catalogNumber)
        assertEquals(tn.collectionNumber, decoded.collectionNumber)
        assertEquals(tn.featureNumber, decoded.featureNumber)
        assertEquals(tn.version, decoded.version)
    }

    @Test
    fun testTupleNumberUnitSize() {
        // Verify that JbDecoder2.unitSize() correctly reports 33 bytes for TUPLE_NUMBER
        val tn = TupleNumber(1L, 2, 3, 4L, 5L)
        val enc = singleEncode(tn)
        val dec = JbDecoder2()
        val bin = Binary()
        bin.view = Platform.newDataView(ByteArray(enc.end) { enc.getInt8(it) })
        bin.end = enc.end
        dec.view = bin
        dec.end = enc.end
        assertEquals(33, dec.unitSize(0), "unitSize of TUPLE_NUMBER must be 33")
    }

    @Test
    fun testTupleNumberExplicitEncodeDecode() {
        // Test the low-level encodeTupleNumber/decode path directly, bypassing encodeValue
        val db = 100L
        val cat = -200
        val col = 300
        val feat = -400L
        val ver = 500L

        val enc = JbEncoder2()
        enc.encodeTupleNumber(db, cat, col, feat, ver)
        assertEquals(33, enc.end, "encodeTupleNumber must produce 33 bytes")

        val decoded = singleDecode(enc) as TupleNumber
        assertEquals(db, decoded.databaseNumber)
        assertEquals(cat, decoded.catalogNumber)
        assertEquals(col, decoded.collectionNumber)
        assertEquals(feat, decoded.featureNumber)
        assertEquals(ver, decoded.version)
    }

    @Test
    fun testGeometryIsIncluded() {
        // Geometry encoded as a plain AnyObject (from JSON) is included in the feature object
        // as a regular JBON2 Object structure (not TWKB, because the value is not an SpGeometry).
        val json = """{"id":"x","geometry":{"type":"Point","coordinates":[0.0,0.0]},"properties":{"name":"test"}}"""
        val f = roundTrip(json)
        // geometry must appear in the decoded feature object
        assertNotNull(f["geometry"], "geometry must be included in the JBON2 feature object")
        assertEquals("test", (f["properties"] as AnyObject)["name"])
    }

    // -----------------------------------------------------------------------
    // JSON conversion — Platform.toJSON(decoded AnyObject) fidelity
    // -----------------------------------------------------------------------

    @Test
    fun testJsonConversionIdOnly() {
        val json = toJson("""{"id":"myId"}""")
        assertTrue(json.contains("\"id\""),   "JSON must contain 'id' key")
        assertTrue(json.contains("\"myId\""), "JSON must contain 'myId' value")
    }

    @Test
    fun testJsonConversionIntegers() {
        val json = toJson("""{"id":"x","properties":{"tiny":-16,"i8":127,"i16":32767,"i32":${Int.MAX_VALUE}}}""")
        assertTrue(json.contains("-16"),        "tiny int -16 must appear in JSON")
        assertTrue(json.contains("127"),        "int8 127 must appear in JSON")
        assertTrue(json.contains("32767"),      "int16 32767 must appear in JSON")
        assertTrue(json.contains("2147483647"), "int32 max must appear in JSON")
    }

    @Test
    fun testJsonConversionFloat() {
        val json = toJson("""{"id":"x","properties":{"f":3.5,"pi":3.141592653589793}}""")
        assertTrue(json.contains("3.5") || json.contains("3.50"), "3.5 must appear in JSON")
        assertTrue(json.contains("3.14159"), "PI prefix must appear in JSON")
    }

    @Test
    fun testJsonConversionBooleans() {
        val json = toJson("""{"id":"x","properties":{"yes":true,"no":false}}""")
        assertTrue(json.contains("true"),  "true must appear in JSON")
        assertTrue(json.contains("false"), "false must appear in JSON")
    }

    @Test
    fun testJsonConversionString() {
        val json = toJson("""{"id":"x","properties":{"greeting":"hello world"}}""")
        assertTrue(json.contains("hello world"), "string value must appear in JSON")
    }

    @Test
    fun testJsonConversionArray() {
        val json = toJson("""{"id":"x","properties":{"arr":[1,2,3]}}""")
        assertTrue(json.contains("\"arr\""))
        assertTrue(json.contains("1") && json.contains("2") && json.contains("3"))
    }

    @Test
    fun testJsonConversionNestedObject() {
        val json = toJson("""{"id":"x","properties":{"outer":{"inner":"value"}}}""")
        assertTrue(json.contains("\"inner\""), "nested key must appear")
        assertTrue(json.contains("\"value\""), "nested value must appear")
    }

    @Test
    fun testJsonConversionUnicodeEuro() {
        val json = toJson("""{"id":"x","properties":{"cur":"€"}}""")
        // The Euro sign must appear either as literal UTF-8 or as a unicode escape.
        assertTrue(
            json.contains("€") || json.contains("\\u20ac") || json.contains("\\u20AC"),
            "Euro sign must appear in JSON output"
        )
    }

    @Test
    fun testJsonConversionLargeInt() {
        // 2147483648 = Int.MAX_VALUE + 1; decoded as Int or Long; either way must appear as a number.
        val f = roundTrip("""{"id":"x","properties":{"big":2147483648}}""")
        val json = Platform.toJSON(f)
        assertTrue(json.contains("2147483648"), "large integer must appear numerically in JSON, not as a class name")
    }

    @Test
    fun testJsonConversionDoesNotThrow() {
        // A feature with all supported scalar types must convert to JSON without throwing.
        val f = roundTrip("""{"id":"x","properties":{"a":-16,"b":127,"c":32767,"d":2147483647,"e":3.5,"f":3.14,"g":true,"h":false,"i":null,"j":"text"}}""")
        val json = Platform.toJSON(f)
        assertNotNull(json)
        assertTrue(json.isNotEmpty())
    }

    // -----------------------------------------------------------------------
    // Member encoder + path tracking
    // -----------------------------------------------------------------------

    @Test
    fun testMemberEncoderPathTrackingNested() {
        val root = AnyObject()
        val a = AnyObject()
        val b = AnyList()
        val cObj = AnyObject()
        cObj["c"] = 1
        b.add(cObj)
        b.add(2)
        a["b"] = b
        root["a"] = a

        var cPath: Array<Any?>? = null
        var cValue: Any? = null

        val enc = JbEncoder2().withMemberEncoder(IMemberEncoder { path, pathEnd, value ->
            if (pathEnd > 0 && path[pathEnd - 1] == "c" && value == 1) {
                cPath = path.copyOf(pathEnd)
                cValue = value
            }
            -1
        })

        enc.encodeValue(root)

        assertNotNull(cPath)
        assertEquals(4, cPath!!.size)
        assertEquals("a", cPath!![0])
        assertEquals("b", cPath!![1])
        assertEquals(0, cPath!![2])
        assertEquals("c", cPath!![3])
        assertEquals(1, cValue)
        assertEquals(0, enc.pathEnd)
    }

    @Test
    fun testMemberEncoderPathResize() {
        var value: Any? = AnyObject().also { (it as AnyObject)["leaf"] = 1 }
        for (i in 19 downTo 0) {
            val obj = AnyObject()
            obj["k$i"] = value
            value = obj
        }

        var leafPath: Array<Any?>? = null
        val enc = JbEncoder2().withMemberEncoder(IMemberEncoder { path, pathEnd, v ->
            if (pathEnd > 0 && path[pathEnd - 1] == "leaf" && v == 1) leafPath = path.copyOf(pathEnd)
            -1
        })

        enc.encodeValue(value)

        assertNotNull(leafPath)
        assertEquals(21, leafPath!!.size)
        assertEquals("k0", leafPath!![0])
        assertEquals("k19", leafPath!![19])
        assertEquals("leaf", leafPath!![20])
        assertEquals(0, enc.pathEnd)
    }

    @Test
    fun testMemberEncoderShortCircuitToMembersRef() {
        val obj = AnyObject()
        obj["x"] = 123

        val enc = JbEncoder2().withMemberEncoder(IMemberEncoder { path, pathEnd, _ ->
            if (pathEnd > 0 && path[pathEnd - 1] == "x") 7 else -1
        })
        enc.encodeValue(obj)

        val lead0 = enc.getInt8(0).toInt() and 0xff
        val headerSize = structHeaderSize(lead0)
        val keyOffset = headerSize
        val keyLead = enc.getInt8(keyOffset).toInt() and 0xff
        val keySize = when (keyLead and JB2_REF_SIZE_MASK) {
            JB2_REF_SIZE8 -> 2
            JB2_REF_SIZE16 -> 3
            JB2_REF_SIZE24 -> 4
            else -> 5
        }

        val valueOffset = keyOffset + keySize
        val expectedLead = JB2_REF or JB2_REF_BOOK_MEMBERS or JB2_REF_SIZE8
        assertEquals(expectedLead.toByte(), enc.getInt8(valueOffset))
        assertEquals(7, enc.getInt8(valueOffset + 1).toInt() and 0xff)
    }
}
