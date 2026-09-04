package naksha.jbon

import naksha.base.*
import naksha.base.TupleNumber
import naksha.geo.GeoUtil
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.jvm.JvmStatic

/**
 * A minimal JBON **version 2** decoder.
 *
 * This reader is intentionally focused on the subset that [JbEncoder2] produces: the `@JB\x02`
 * file header, primitives, strings (with string-references resolved against a book), and the
 * structures [Array], [Object], [Map], [TagList], [TagMap], [Dictionary], [Book] and [Tuple].
 * It is enough to keep the read path and round-trip tests working; the full JBON2 type set
 * can be added incrementally.
 *
 * The decoder maps a single top-level unit. To decode a stored tuple, call [mapBytes] which will
 * skip the file header (if present), descend into the [Tuple], and expose the feature [Object].
 *
 * @property globalBook The global book used to resolve global references; if any.
 * @property membersBook The members book used to resolve member references ([JB2_REF_BOOK_MEMBERS]);
 *   if any. Entries are arbitrary `Any?` values assembled by the caller (e.g. from database columns).
 *   If an entry is a [ByteArray] it is interpreted as raw TWKB bytes and converted to [SpGeometry]
 *   via [GeoUtil.fromTWKB] before being returned to the caller.
 *   Embedded members books inside a [Tuple] are **not** loaded automatically; the caller must
 *   supply [membersBook] explicitly before decoding.
 */
@Suppress("MemberVisibilityCanBePrivate", "OPT_IN_USAGE", "DuplicatedCode")
@JsExport
open class JbDecoder2(var globalBook: IBook? = null, var membersBook: IBook? = null) {

    /** The underlying binary view. */
    var view: BinaryView = Binary()
        internal set

    /** The current read offset (the lead-in byte of the current unit). */
    var offset: Int = 0
        internal set

    /** The end offset (exclusive) of the readable region. */
    var end: Int = 0
        internal set

    /** The local book (interned strings) resolved while decoding a tuple/feature. */
    var localStrings: List<String>? = null
        internal set

    companion object JbDecoder2Companion {
        /**
         * Verify and skip the JBON2 file header. Returns the offset just after the header, or the
         * original offset if no header is present.
         * @throws IllegalStateException if a `@JB` prefix is found with an unknown version byte.
         */
        @JvmStatic
        fun skipHeader(view: BinaryView, offset: Int): Int {
            if (offset + 4 > view.end) return offset
            val a = view.getInt8(offset).toInt() and 0xff
            val b = view.getInt8(offset + 1).toInt() and 0xff
            val c = view.getInt8(offset + 2).toInt() and 0xff
            if (a == '@'.code && b == 'J'.code && c == 'B'.code) {
                val v = view.getInt8(offset + 3).toInt() and 0xff
                check(v == JB2_VERSION) { "Unsupported JBON version: $v" }
                return offset + 4
            }
            return offset
        }

        /**
         * Decode a single code point at [i] within `[offset,end)`, returning `(codePoint shl 3) or byteLen`,
         * or `-1` on error. Mirrors the JBON2 code-point lead bytes.
         */
        @JvmStatic
        internal fun readCodePoint(view: BinaryView, i: Int, end: Int): Int {
            if (i >= end) return -1
            val lead = view.getInt8(i).toInt() and 0xff
            return when {
                lead and 0b1000_0000 == 0 -> (lead shl 3) or 1 // 0_vvvvvvv ASCII
                lead and JB2_CP_2BYTE_MASK == JB2_CP_2BYTE -> { // 100_vvvvv
                    if (i + 1 >= end) return -1
                    val lo = view.getInt8(i + 1).toInt() and 0xff
                    val biased = ((lead and 0b0001_1111) shl 8) or lo
                    ((biased + JB2_CP_2BYTE_BIAS) shl 3) or 2
                }
                lead and JB2_CP_2BYTE_MASK == JB2_CP_3BYTE -> { // 101_vvvvv
                    if (i + 2 >= end) return -1
                    val lo = view.getInt16(i + 1).toInt() and 0xffff
                    val biased = ((lead and 0b0001_1111) shl 16) or lo
                    ((biased + JB2_CP_3BYTE_BIAS) shl 3) or 3
                }
                else -> -1
            }
        }

        /**
         * Read a string body (no header) from `[offset,end)` into [sb], resolving string-references
         * against the supplied books.
         */
        @JvmStatic
        internal fun readSubstring(
            view: BinaryView,
            offset: Int,
            end: Int,
            sb: StringBuilder,
            globalBook: IBook? = null,
            localStrings: List<String>? = null,
            memberBook: IBook? = null
        ) {
            var i = offset
            while (i < end) {
                val lead = view.getInt8(i).toInt() and 0xff
                if (lead and JB2_SREF_PREFIX_MASK == JB2_SREF) { // 11_aaa_bbs string-reference
                    val sizeBit = lead and JB2_SREF_SIZE_MASK
                    val index: Int
                    if (sizeBit == JB2_SREF_SIZE_SMALL) {
                        index = view.getInt8(i + 1).toInt() and 0xff
                        i += 2
                    } else {
                        val hi = view.getInt8(i + 1).toInt() and 0xff
                        val lo = view.getInt16(i + 2).toInt() and 0xffff
                        index = (hi shl 16) or lo
                        i += 4
                    }
                    val bb = (lead and JB2_SREF_BOOK_MASK) ushr JB2_SREF_BOOK_SHIFT
                    val s = when (bb) {
                        JB2_BOOK_GLOBAL -> globalBook?.getStringAt(index)
                        JB2_BOOK_LOCAL -> localStrings?.get(index)
                        JB2_BOOK_MEMBERS -> memberBook?.getStringAt(index)
                        else -> null // const book holds no addressable strings here
                    }
                    if (s != null) sb.append(s)
                    val add = (lead and JB2_SREF_ADD_MASK) ushr JB2_SREF_ADD_SHIFT
                    val ch = JB2_ADD_CHAR[add]
                    if (ch != 0) sb.append(ch.toChar())
                } else {
                    val cp = readCodePoint(view, i, end)
                    check(cp != -1) { "Invalid code point at offset $i" }
                    i += cp and 0b111
                    val unicode = cp shr 3
                    if (CodePoints.isBmpCodePoint(unicode)) {
                        sb.append(unicode.toChar())
                    } else {
                        sb.append(CodePoints.highSurrogate(unicode))
                        sb.append(CodePoints.lowSurrogate(unicode))
                    }
                }
            }
        }
    }

    /**
     * Map a byte-array, skip the `@JB` header if present, and position at the first unit.
     * @param bytes The bytes to decode.
     * @return this
     */
    @JsName("mapBytes")
    fun mapBytes(bytes: ByteArray): JbDecoder2 {
        val v = Binary()
        v.view = Platform.newDataView(bytes)
        v.end = bytes.size
        this.view = v
        this.end = bytes.size
        this.offset = skipHeader(v, 0)
        return this
    }

    // -----------------------------------------------------------------------
    // Lead-in inspection
    // -----------------------------------------------------------------------

    private fun leadIn(at: Int): Int = view.getInt8(at).toInt() and 0xff

    /** The size-field width contribution and content size of a structure at [at]. */
    private fun structHeaderSize(at: Int): Int {
        return when (leadIn(at) and JB2_STRUCT_SIZE_MASK) {
            JB2_STRUCT_SIZE0 -> 1
            JB2_STRUCT_SIZE8 -> 2
            JB2_STRUCT_SIZE16 -> 3
            else -> 5
        }
    }

    private fun structContentSize(at: Int): Int {
        return when (leadIn(at) and JB2_STRUCT_SIZE_MASK) {
            JB2_STRUCT_SIZE0 -> 0
            JB2_STRUCT_SIZE8 -> view.getInt8(at + 1).toInt() and 0xff
            JB2_STRUCT_SIZE16 -> view.getInt16(at + 1).toInt() and 0xffff
            else -> view.getInt32(at + 1)
        }
    }

    /** The total size (bytes) of the unit starting at [at]. */
    fun unitSize(at: Int): Int {
        val lead = leadIn(at)
        return when (lead and JB2_CLASS_MASK) {
            JB2_CLASS_TINY -> 1
            JB2_CLASS_STRING -> stringUnitSize(at)
            JB2_CLASS_STRUCT -> structHeaderSize(at) + structContentSize(at)
            else -> mixedUnitSize(at) // JB2_CLASS_MIXED
        }
    }

    private fun mixedUnitSize(at: Int): Int {
        val lead = leadIn(at)
        if (lead and JB2_REF_PREFIX_MASK == JB2_REF) {
            return when (lead and JB2_REF_SIZE_MASK) {
                JB2_REF_SIZE8 -> 2
                JB2_REF_SIZE16 -> 3
                JB2_REF_SIZE24 -> 4
                else -> 5
            }
        }
        return when (lead) {
            JB2_UNDEFINED, JB2_NULL, JB2_FALSE, JB2_TRUE -> 1
            JB2_INT8 -> 2
            JB2_INT16 -> 3
            JB2_INT32 -> 5
            JB2_INT64 -> 9
            JB2_FLOAT8 -> 2
            JB2_FLOAT16 -> 3
            JB2_FLOAT32 -> 5
            JB2_FLOAT64 -> 9
            JB2_TIMESTAMP -> 8
            JB2_UINT56 -> 8
            JB2_UINT24 -> 4
            JB2_TUPLE_NUMBER -> 33
            else -> throw IllegalStateException("Unknown mixed lead-in: ${lead.toString(2)}")
        }
    }

    private fun stringUnitSize(at: Int): Int {
        val sizeField = leadIn(at) and JB2_STRING_SIZE_MASK
        return when (sizeField) {
            JB2_STRING_SIZE_BYTE -> 2 + (view.getInt8(at + 1).toInt() and 0xff) + JB2_STRING_SIZE_BIAS
            JB2_STRING_SIZE_SHORT -> 3 + (view.getInt16(at + 1).toInt() and 0xffff)
            JB2_STRING_SIZE_INT -> 5 + view.getInt32(at + 1)
            else -> 1 + sizeField
        }
    }

    private fun stringHeaderSize(at: Int): Int = when (leadIn(at) and JB2_STRING_SIZE_MASK) {
        JB2_STRING_SIZE_BYTE -> 2
        JB2_STRING_SIZE_SHORT -> 3
        JB2_STRING_SIZE_INT -> 5
        else -> 1
    }

    // -----------------------------------------------------------------------
    // Value decoding
    // -----------------------------------------------------------------------

    /** Decode the value of the unit at [at] into a platform-native value. */
    fun decodeValueAt(at: Int): Any? {
        val lead = leadIn(at)
        return when (lead and JB2_CLASS_MASK) {
            JB2_CLASS_TINY -> {
                // Extract the 5-bit value from bits 4-0 (masking off the 2-bit class and 1-bit tiny-type flag).
                val raw = lead and JB2_TINY_VALUE_MASK
                val v = (raw shl 27) shr 27 // arithmetic sign-extend 5-bit → 32-bit
                if (lead and JB2_TINY_MASK == JB2_TINY_FLOAT) v.toDouble() else v
            }
            JB2_CLASS_STRING -> decodeStringAt(at)
            JB2_CLASS_STRUCT -> decodeStructAt(at)
            else -> decodeMixedAt(at)
        }
    }

    private fun decodeMixedAt(at: Int): Any? {
        val lead = leadIn(at)
        if (lead and JB2_REF_PREFIX_MASK == JB2_REF) return decodeRefAt(at)
        return when (lead) {
            JB2_UNDEFINED -> null
            JB2_NULL -> null
            JB2_FALSE -> false
            JB2_TRUE -> true
            JB2_INT8 -> view.getInt8(at + 1).toInt()
            JB2_INT16 -> view.getInt16(at + 1).toInt()
            JB2_INT32 -> view.getInt32(at + 1)
            JB2_INT64 -> view.getInt64(at + 1)
            JB2_FLOAT32 -> view.getFloat32(at + 1)
            JB2_FLOAT64 -> view.getFloat64(at + 1)
            JB2_TIMESTAMP, JB2_UINT56 -> view.getInt64(at) and JB2_MASK_56_LOW
            JB2_UINT24 -> view.getInt32(at) and JB2_MASK_24_LOW
            JB2_TUPLE_NUMBER -> {
                // lead-in at `at`, data starts at `at + 1`: 8 + 4 + 4 + 8 + 8 = 32 bytes
                val db = view.getInt64(at + 1)
                val cat = view.getInt32(at + 9)
                val col = view.getInt32(at + 13)
                val feat = view.getInt64(at + 17)
                val ver = view.getInt64(at + 25)
                TupleNumber(db, cat, col, feat, ver)
            }
            else -> throw IllegalStateException("Unsupported mixed lead-in for decode: ${lead.toString(2)}")
        }
    }

    private fun decodeRefAt(at: Int): Any? {
        val lead = leadIn(at)
        val index = when (lead and JB2_REF_SIZE_MASK) {
            JB2_REF_SIZE8 -> view.getInt8(at + 1).toInt() and 0xff
            JB2_REF_SIZE16 -> view.getInt16(at + 1).toInt() and 0xffff
            JB2_REF_SIZE24 -> ((view.getInt8(at + 1).toInt() and 0xff) shl 16) or (view.getInt16(at + 2).toInt() and 0xffff)
            else -> view.getInt32(at + 1)
        }
        return when (lead and JB2_REF_BOOK_MASK) {
            JB2_REF_BOOK_GLOBAL -> globalBook?.get(index)
            JB2_REF_BOOK_LOCAL -> localStrings?.getOrNull(index)
            JB2_REF_BOOK_MEMBERS -> membersBook?.get(index)
            else -> null // const
        }
    }

    private fun decodeStringAt(at: Int): String {
        val hs = stringHeaderSize(at)
        val total = stringUnitSize(at)
        val sb = StringBuilder()
        readSubstring(view, at + hs, at + total, sb, globalBook, localStrings, membersBook)
        return sb.toString()
    }


    private fun decodeStructAt(at: Int): Any? {
        val type = leadIn(at) and JB2_STRUCT_TYPE_MASK
        val hs = structHeaderSize(at)
        val contentSize = structContentSize(at)
        val contentStart = at + hs
        val contentEnd = contentStart + contentSize
        return when (type) {
            JB2_STRUCT_ARRAY, JB2_STRUCT_TAG_LIST -> {
                val list = AnyList()
                var p = contentStart
                while (p < contentEnd) {
                    list.add(decodeValueAt(p))
                    p += unitSize(p)
                }
                list
            }
            JB2_STRUCT_OBJECT, JB2_STRUCT_MAP, JB2_STRUCT_TAG_MAP -> {
                val obj = AnyObject()
                var p = contentStart
                while (p < contentEnd) {
                    val key = decodeValueAt(p)?.toString() ?: "null"
                    p += unitSize(p)
                    if (p >= contentEnd) break
                    obj[key] = decodeValueAt(p)
                    p += unitSize(p)
                }
                obj
            }
            JB2_STRUCT_TUPLE -> {
                // First child is the feature object, then the local book (dictionary).
                resolveLocalBook(contentStart, contentEnd)
                decodeValueAt(contentStart)
            }
            JB2_STRUCT_TWKB -> {
                // Content bytes are raw TWKB; convert to SpGeometry via GeoUtil.
                val bytes = ByteArray(contentSize) { view.getInt8(contentStart + it) }
                GeoUtil.fromTWKB(bytes)
            }
            JB2_STRUCT_BYTE_ARRAY -> {
                // Content bytes are raw ByteArray.
                ByteArray(contentSize) { view.getInt8(contentStart + it) }
            }
            JB2_STRUCT_DICTIONARY -> {
                readDictionaryStrings(at)
                null
            }
            else -> throw IllegalStateException("Unsupported struct type for decode: $type")
        }
    }

    /** Locate the local book (the last [Dictionary] in the tuple content) and load its strings. */
    private fun resolveLocalBook(contentStart: Int, contentEnd: Int) {
        var p = contentStart
        while (p < contentEnd) {
            val lead = leadIn(p)
            if (lead and JB2_CLASS_MASK == JB2_CLASS_STRUCT &&
                lead and JB2_STRUCT_TYPE_MASK == JB2_STRUCT_DICTIONARY
            ) {
                readDictionaryStrings(p)
            }
            p += unitSize(p)
        }
    }

    private fun readDictionaryStrings(at: Int) {
        val hs = structHeaderSize(at)
        val contentSize = structContentSize(at)
        val contentStart = at + hs
        val contentEnd = contentStart + contentSize
        val strings = ArrayList<String>()
        var p = contentStart
        // First entry is the id (string or null); skip it.
        if (p < contentEnd) {
            // skip id
            p += unitSize(p)
        }
        while (p < contentEnd) {
            val s = decodeValueAt(p)
            strings.add(s?.toString() ?: "")
            p += unitSize(p)
        }
        localStrings = strings
    }

    /**
     * Decode the top-level tuple into an [AnyObject] feature.
     * @return the decoded feature.
     * @throws IllegalStateException if the top-level unit does not decode to an object.
     */
    fun toAnyObject(): AnyObject {
        val v = decodeValueAt(offset)
        return v as? AnyObject ?: throw IllegalStateException("Top-level JBON2 unit is not an object: $v")
    }
}
