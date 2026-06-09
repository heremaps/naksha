package naksha.jbon

import naksha.base.*
import naksha.geo.GeoUtil
import naksha.geo.SpGeometry
import kotlin.js.*
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic
import kotlin.math.floor

/**
 * The JBON **version 2** encoder.
 *
 * This is a fresh emitter that produces bytes conforming to the JBON2 binary specification
 * (`docs/latest/JBON2.md`). It deliberately does not extend [JbEncoder]; the legacy JBON1
 * encoder stays untouched and remains responsible for legacy data, while this class is used
 * for everything newly persisted.
 *
 * The encoder uses the same proven "reserve header / copy-backwards" machinery as JBON1 to
 * keep allocations low, but every lead-in byte is emitted using the JBON2 bit layouts defined
 * in [LibJbon2].
 *
 * Strings are dictionary-compressed using [encodeText] (word splitting + string-references)
 * when a [global] book is provided; the JBON2 string-reference format (`11_aaa_bbs`, 7 append
 * characters) is used.
 *
 * @property global The global book/dictionary to use when encoding; if any.
 */
@Suppress("DuplicatedCode", "MemberVisibilityCanBePrivate", "OPT_IN_USAGE")
@JsExport
open class JbEncoder2(var global: IBook? = null) : Binary() {

    /**
     * Create a new resizable encoder with a new byte-array of the given size backing it.
     * @param size The amount of byte to allocate initially.
     * @param global The global book/dictionary to use when encoding; if any.
     */
    @Suppress("LeakingThis")
    @JsName("forSize")
    constructor(size: Int, global: IBook? = null) : this(global) {
        view = Platform.newDataView(ByteArray(size))
        this.readOnly = false
        this.resize = true
    }

    companion object JbEncoder2Companion {
        @JvmField
        @JsStatic
        val EMPTY_PATH: Array<Any?> = emptyArray()

        /**
         * Characters that belong to a **word** when auto-splitting strings for the local book.
         * Identical to the JBON1 behaviour: letters and the colon.
         */
        @JvmField
        @JsStatic
        val wordUnicode = BooleanArray(128) {
            (it in 'a'.code..'z'.code) || (it in 'A'.code..'Z'.code) || it == ':'.code
        }

        /** The maximal encoding size (in bytes) of a string. */
        @JvmStatic
        @JsStatic
        fun maxSizeOfString(string: String): Int = string.length * 3 + 5

        /** The amount of bytes needed to store the given 32-bit integer value in JBON2. */
        @JvmStatic
        @JsStatic
        fun sizeOfIntEncoding(value: Int): Int = when (value) {
            in -16..15 -> 1
            in -128..127 -> 2
            in -32768..32767 -> 3
            else -> 5
        }
    }

    // -----------------------------------------------------------------------
    // Local book (dictionary) state
    // -----------------------------------------------------------------------

    private var localDictByName: HashMap<String, Int>? = null
    private var localDictByIndex: ArrayList<String>? = null
    private var localDictNextIndex: Int = 0

    /** Current container path, root is empty. */
    var path: Array<Any?> = EMPTY_PATH
        internal set

    /** End pointer into [path]. */
    var pathEnd: Int = 0
        internal set

    /** Optional hook to replace encoded values by members-book references. */
    var memberEncoder: IMemberEncoder? = null
        internal set

    fun withMemberEncoder(memberEncoder: IMemberEncoder?): JbEncoder2 {
        this.memberEncoder = memberEncoder
        return this
    }

    internal fun ensurePathSpace(amount: Int) {
        require(amount >= 0) { "amount must be >= 0: $amount" }
        val required = pathEnd + amount
        if (required <= path.size) return
        // We divide by 8, add one, then multiply by 16.
        // For example when 15 are needed, we add 32. When 1 is needed, we add 16.
        val newSize = path.size + (((amount shr 3) + 1) shl 4)
        path = path.copyOf(newSize)
    }

    internal fun pushPath(index: Int) {
        ensurePathSpace(1)
        path[pathEnd++] = index
    }

    internal fun pushPath(key: String) {
        ensurePathSpace(1)
        path[pathEnd++] = key
    }

    internal fun popPath() {
        require(pathEnd > 0) { "pop, but path is empty: $pathEnd" }
        path[--pathEnd] = null
    }

    internal fun getLocalDictByString(): HashMap<String, Int> {
        var m = localDictByName
        if (m == null) {
            m = HashMap()
            localDictByName = m
        }
        return m
    }

    private fun getLocalDictByIndex(): ArrayList<String> {
        var l = localDictByIndex
        if (l == null) {
            l = ArrayList()
            localDictByIndex = l
        }
        return l
    }

    /**
     * Clear the encoder, reset [pos]/[end] to `0`, and clear the local book. Leaves the global book intact.
     * @return The old [end].
     */
    open fun clear(): Int {
        localDictByName = null
        localDictByIndex = null
        localDictNextIndex = 0
        if (path.size <= 256) path.fill(null) else path = EMPTY_PATH
        pathEnd = 0
        return reset()
    }

    /**
     * Add the given string to the local book, returning the existing index if already present.
     * @param string The string to add.
     * @return The index of the string in the local book.
     */
    fun addToLocalDictionary(string: String): Int {
        val dict = getLocalDictByString()
        var index = dict[string]
        if (index != null) return index
        index = localDictNextIndex++
        dict[string] = index
        getLocalDictByIndex().add(string)
        return index
    }

    // -----------------------------------------------------------------------
    // Scalars / specials
    // -----------------------------------------------------------------------

    /** Write `undefined`. */
    fun encodeUndefined(): Int {
        val pos = end
        writeInt8(JB2_UNDEFINED.toByte())
        return pos
    }

    /** Write `null`. */
    fun encodeNull(): Int {
        val pos = end
        writeInt8(JB2_NULL.toByte())
        return pos
    }

    /** Write a boolean. */
    fun encodeBool(value: Boolean): Int {
        val pos = end
        writeInt8((if (value) JB2_TRUE else JB2_FALSE).toByte())
        return pos
    }

    /** Write a 32-bit integer using the smallest JBON2 encoding. */
    fun encodeInt32(value: Int): Int {
        val pos = end
        when (value) {
            in -16..15 -> writeInt8((JB2_CLASS_TINY or JB2_TINY_INT or (value and JB2_TINY_VALUE_MASK)).toByte())
            in -128..127 -> {
                writeInt8(JB2_INT8.toByte())
                writeInt8(value.toByte())
            }
            in -32768..32767 -> {
                writeInt8(JB2_INT16.toByte())
                writeInt16(value.toShort())
            }
            else -> {
                writeInt8(JB2_INT32.toByte())
                writeInt32(value)
            }
        }
        return pos
    }

    /** Write a 64-bit integer using the smallest JBON2 encoding. */
    fun encodeInt64(value: Int64): Int {
        if (value >= Int.MIN_VALUE && value <= Int.MAX_VALUE) {
            return encodeInt32(value.toInt())
        }
        val pos = end
        writeInt8(JB2_INT64.toByte())
        writeInt64(value)
        return pos
    }

    /** Write a 32-bit floating point number, using the tiny encoding for small whole numbers. */
    fun encodeFloat32(value: Float): Int {
        val pos = end
        if (value >= -16.0 && value <= 15.0 && value == floor(value)) {
            writeInt8((JB2_CLASS_TINY or JB2_TINY_FLOAT or (value.toInt() and JB2_TINY_VALUE_MASK)).toByte())
            return pos
        }
        writeInt8(JB2_FLOAT32.toByte())
        writeFloat32(value)
        return pos
    }

    /** Write a 64-bit floating point number, using the tiny encoding for small whole numbers. */
    fun encodeFloat64(value: Double): Int {
        val pos = end
        if (value >= -16.0 && value <= 15.0 && value == floor(value)) {
            writeInt8((JB2_CLASS_TINY or JB2_TINY_FLOAT or (value.toInt() and JB2_TINY_VALUE_MASK)).toByte())
            return pos
        }
        writeInt8(JB2_FLOAT64.toByte())
        writeFloat64(value)
        return pos
    }

    /**
     * Write a 56-bit unsigned integer (lead-in `0000_1101`), the value AND-ed with the 56-bit mask
     * and written as a big-endian 64-bit integer with the high byte forced to the lead-in.
     *
     * The 7 payload bytes are written by storing the full 64-bit value `(0x0D shl 56) | (value and mask)`.
     * @param value The value to write; only the lower 56 bits are stored.
     * @return The offset of the value written.
     */
    fun encodeUInt56(value: Int64): Int {
        val pos = end
        val masked = value and Platform.toInt64(JB2_MASK_56_LOW)
        val packed = (Platform.toInt64(JB2_UINT56.toLong()) shl 56) or masked
        writeInt64(packed)
        return pos
    }

    /**
     * Write a 24-bit unsigned integer (lead-in `0000_1110`) as a big-endian 32-bit integer with the
     * high byte forced to the lead-in.
     * @param value The value to write; only the lower 24 bits are stored.
     * @return The offset of the value written.
     */
    fun encodeUInt24(value: Int): Int {
        val pos = end
        val packed = (JB2_UINT24 shl 24) or (value and JB2_MASK_24_LOW)
        writeInt32(packed)
        return pos
    }

    /**
     * Write a Unix-Epoch timestamp (UTC) in milliseconds as a 7-byte value (lead-in `0000_1100`).
     * @param value The timestamp to write.
     * @return The offset of the value written.
     */
    fun encodeTimestamp(value: Int64): Int {
        val pos = end
        val masked = value and Platform.toInt64(JB2_MASK_56_LOW)
        val packed = (Platform.toInt64(JB2_TIMESTAMP.toLong()) shl 56) or masked
        writeInt64(packed)
        return pos
    }

    /**
     * Write a full 32-byte tuple-number value (lead-in `0000_1111`).
     *
     * The component order matches the JBON2 spec layout
     * (lead_in, databaseNumber, catalogNumber, collectionNumber, featureNumber, version).
     * The mapping from the Naksha model is applied by the caller:
     * `databaseNumber = storageNumber`, `catalogNumber = mapNumber`.
     *
     * @param databaseNumber 64-bit database (a.k.a. storage) number.
     * @param catalogNumber 32-bit catalog (a.k.a. map) number.
     * @param collectionNumber 32-bit collection number.
     * @param featureNumber 64-bit feature number.
     * @param version 64-bit version.
     * @return The offset of the value written.
     */
    fun encodeTupleNumber(
        databaseNumber: Int64,
        catalogNumber: Int,
        collectionNumber: Int,
        featureNumber: Int64,
        version: Int64
    ): Int {
        val pos = end
        writeInt8(JB2_TUPLE_NUMBER.toByte())
        writeInt64(databaseNumber)
        writeInt32(catalogNumber)
        writeInt32(collectionNumber)
        writeInt64(featureNumber)
        writeInt64(version)
        return pos
    }

    // -----------------------------------------------------------------------
    // References (`0011_bbss`)
    // -----------------------------------------------------------------------

    /**
     * Write a reference into one of the books.
     * @param index The index into the book; a negative value writes `null` instead.
     * @param book The target book (one of [JB2_REF_BOOK_LOCAL], [JB2_REF_BOOK_MEMBERS], [JB2_REF_BOOK_GLOBAL]).
     * @return The offset of the value written.
     */
    fun encodeRef(index: Int, book: Int): Int {
        val pos = end
        if (index < 0) {
            encodeNull()
            return pos
        }
        require(book == JB2_REF_BOOK_LOCAL || book == JB2_REF_BOOK_MEMBERS || book == JB2_REF_BOOK_GLOBAL) {
            "Invalid book for a normal reference: $book"
        }
        when {
            index < 256 -> {
                writeInt8((JB2_REF or book or JB2_REF_SIZE8).toByte())
                writeInt8(index.toByte())
            }
            index < 65536 -> {
                writeInt8((JB2_REF or book or JB2_REF_SIZE16).toByte())
                writeInt16(index.toShort())
            }
            index < 16777216 -> {
                writeInt8((JB2_REF or book or JB2_REF_SIZE24).toByte())
                writeInt8((index ushr 16).toByte())
                writeInt16(index.toShort())
            }
            else -> {
                writeInt8((JB2_REF or book or JB2_REF_SIZE32).toByte())
                writeInt32(index)
            }
        }
        return pos
    }

    // -----------------------------------------------------------------------
    // Strings
    // -----------------------------------------------------------------------

    private fun writeUnicode(view: BinaryView, offset: Int, unicode: Int): Int {
        require(unicode in 0..2_105_471)
        var pos = offset
        when (unicode) {
            in 0..127 -> view.setInt8(pos++, unicode.toByte())
            in 128..8319 -> { // biased by 128, 13-bit
                val biased = unicode - JB2_CP_2BYTE_BIAS
                view.setInt8(pos++, ((biased ushr 8) or JB2_CP_2BYTE).toByte())
                view.setInt8(pos++, (biased and 0xff).toByte())
            }
            else -> { // biased by 8320, 21-bit
                val biased = unicode - JB2_CP_3BYTE_BIAS
                view.setInt8(pos++, ((biased ushr 16) or JB2_CP_3BYTE).toByte())
                view.setInt16(pos, (biased and 0xffff).toShort())
                pos += 2
            }
        }
        return pos
    }

    private fun writeStringHeader(view: BinaryView, offset: Int, size: Int): Int {
        require(size >= 0) { "The string must not have a size less than zero: $size" }
        if (size <= 60) {
            view.setInt8(offset, (JB2_CLASS_STRING or size).toByte())
            return offset + 1
        }
        if (size <= 316) { // 255 + 61
            view.setInt8(offset, (JB2_CLASS_STRING or JB2_STRING_SIZE_BYTE).toByte())
            view.setInt8(offset + 1, (size - JB2_STRING_SIZE_BIAS).toByte())
            return offset + 2
        }
        if (size < 65536) {
            view.setInt8(offset, (JB2_CLASS_STRING or JB2_STRING_SIZE_SHORT).toByte())
            view.setInt16(offset + 1, size.toShort())
            return offset + 3
        }
        view.setInt8(offset, (JB2_CLASS_STRING or JB2_STRING_SIZE_INT).toByte())
        view.setInt32(offset + 1, size)
        return offset + 5
    }

    /**
     * Encode a string verbatim (no string-references / no dictionary compression).
     * @param string The string to encode.
     * @return The offset of the value written.
     */
    fun encodeString(string: String): Int {
        val start = end
        var pos = end + 5 // reserve max header
        var i = 0
        while (i < string.length) {
            val hi = string[i++]
            val unicode: Int = if (i < string.length && hi.isHighSurrogate()) {
                val lo = string[i++]
                require(lo.isLowSurrogate())
                CodePoints.toCodePoint(hi, lo)
            } else hi.code
            check(unicode in 0..2_105_471)
            pos = writeUnicode(this, pos, unicode)
        }
        val size = pos - start - 5
        var source = start + 5
        var target = writeStringHeader(this, start, size)
        if (target < source) {
            while (source < pos) setInt8(target++, getInt8(source++))
            pos = target
        }
        end = pos
        return start
    }

    /**
     * Write a string-reference inside a string code-point stream.
     * @param offset The offset at which to write.
     * @param index The book index to reference.
     * @param book The target book ([JB2_REF_BOOK_LOCAL], [JB2_REF_BOOK_MEMBERS], [JB2_REF_BOOK_GLOBAL], [JB2_REF_BOOK_CONST]).
     * @param add One of the [JB2_ADD_NOTHING]..[JB2_ADD_UNDERSCORE] append codes (0..7).
     * @return The end offset.
     */
    private fun encodeStringRef(offset: Int, index: Int, book: Int, add: Int): Int {
        require(add in 0..7)
        require(index >= 0)
        var pos = offset
        // book is one of JB2_REF_BOOK_* (the `bb` value shifted into bits 2..3); for the
        // string-ref `bb` lives in bits 1..2, so derive the 2-bit book value first.
        val bb = (book and JB2_REF_BOOK_MASK) ushr 2
        var leadIn = JB2_SREF or (add shl JB2_SREF_ADD_SHIFT) or (bb shl JB2_SREF_BOOK_SHIFT)
        if (index < 256) {
            setInt8(pos, (leadIn or JB2_SREF_SIZE_SMALL).toByte())
            setInt8(pos + 1, index.toByte())
            pos += 2
        } else {
            setInt8(pos, (leadIn or JB2_SREF_SIZE_LARGE).toByte())
            setInt8(pos + 1, (index ushr 16).toByte())
            setInt16(pos + 2, index.toShort())
            pos += 4
        }
        return pos
    }

    /**
     * Encode a string using the local and global books for compression (word splitting +
     * string-references). Falls back to verbatim encoding for short/uncompressible strings.
     * @param string The string to encode.
     * @return The offset of the value written.
     */
    fun encodeText(string: String): Int {
        val sb = StringBuilder()
        val start = end
        val global = this.global
        val headerReservedSize = 1 + when (string.length * 3) {
            in 0..60 -> 0
            in 61..316 -> 1
            in 317..65535 -> 2
            else -> 4
        }
        var pos = end + headerReservedSize
        var i = 0
        var wordStart = -1
        char_loop@ while (i < string.length) {
            val hi = string[i++]
            val unicode: Int = if (i < string.length && hi.isHighSurrogate()) {
                val lo = string[i++]
                require(lo.isLowSurrogate())
                CodePoints.toCodePoint(hi, lo)
            } else hi.code
            check(unicode in 0..2_105_471)
            val isWordCode = unicode < 128 && wordUnicode[unicode]
            if (isWordCode && wordStart < 0) wordStart = pos
            if (wordStart >= 0 && (!isWordCode || i == string.length)) {
                var size = pos - wordStart
                if (isWordCode && size >= 2) {
                    pos = writeUnicode(this, pos, unicode)
                    size++
                }
                if (size >= 3) {
                    sb.clear()
                    JbDecoder2.readSubstring(this, wordStart, pos, sb)
                    val subString = sb.toString()
                    var index = -1
                    var book = JB2_REF_BOOK_LOCAL
                    if (global != null) {
                        index = global.indexOf(subString)
                        if (index < 0) {
                            // Try URN-style prefixes ending in a colon.
                            var reversePos = pos - 1
                            val stopAt = wordStart + 3
                            while (reversePos > stopAt) {
                                val c = getInt8(reversePos).toInt() and 0xff
                                if (':'.code == c) {
                                    sb.clear()
                                    JbDecoder2.readSubstring(this, wordStart, reversePos, sb)
                                    val prefix = sb.toString()
                                    val pidx = global.indexOf(prefix)
                                    if (pidx >= 0) {
                                        pos = encodeStringRef(wordStart, pidx, JB2_REF_BOOK_GLOBAL, JB2_ADD_COLON)
                                        i = reversePos + 1
                                        wordStart = -1
                                        continue@char_loop
                                    }
                                }
                                reversePos--
                            }
                        } else {
                            book = JB2_REF_BOOK_GLOBAL
                        }
                    }
                    if (index < 0) {
                        index = addToLocalDictionary(subString)
                        book = JB2_REF_BOOK_LOCAL
                    }
                    check(index >= 0)
                    val add = when (unicode) {
                        ' '.code -> JB2_ADD_SPACE
                        '.'.code -> JB2_ADD_DOT
                        ':'.code -> JB2_ADD_COLON
                        ','.code -> JB2_ADD_COMMA
                        ';'.code -> JB2_ADD_SEMICOLON
                        '-'.code -> JB2_ADD_MINUS
                        '_'.code -> JB2_ADD_UNDERSCORE
                        else -> JB2_ADD_NOTHING
                    }
                    pos = encodeStringRef(wordStart, index, book, add)
                    wordStart = -1
                    if (add > 0 || i == string.length) continue
                }
                wordStart = -1
            }
            pos = writeUnicode(this, pos, unicode)
        }
        val size = pos - start - headerReservedSize
        var source = start + headerReservedSize
        var target = writeStringHeader(this, start, size)
        if (target < source) {
            while (source < pos) setInt8(target++, getInt8(source++))
            pos = target
        }
        end = pos
        return start
    }

    // -----------------------------------------------------------------------
    // Structures (`11ss_tttt`)
    // -----------------------------------------------------------------------

    /**
     * Start a structure, reserving the maximum header size (lead-in + 4-byte size). The structure
     * content can simply be written after this call, then closed with [endStruct].
     * @return The offset where the structure was started.
     */
    fun startStruct(): Int {
        val start = end
        end += 5 // 1 byte lead-in + up to 4 byte size
        return start
    }

    /**
     * Write a structure header at [end] for the given type and content size.
     * @param structType The 4-bit structure type (e.g. [JB2_STRUCT_OBJECT]).
     * @param contentSize The size of the content in bytes.
     * @return The start offset of the header.
     */
    internal fun writeStructHeader(structType: Int, contentSize: Int): Int {
        val start = end
        require(contentSize >= 0) { "Structure size must be >= 0, but was: $contentSize" }
        var leadIn = JB2_CLASS_STRUCT or (structType and JB2_STRUCT_TYPE_MASK)
        end++ // lead-in written later
        when {
            contentSize == 0 -> leadIn = leadIn or JB2_STRUCT_SIZE0
            contentSize < 256 -> {
                leadIn = leadIn or JB2_STRUCT_SIZE8
                writeInt8(contentSize.toByte())
            }
            contentSize < 65536 -> {
                leadIn = leadIn or JB2_STRUCT_SIZE16
                writeInt16(contentSize.toShort())
            }
            else -> {
                leadIn = leadIn or JB2_STRUCT_SIZE32
                writeInt32(contentSize)
            }
        }
        setInt8(start, leadIn.toByte())
        return start
    }

    /**
     * Close a structure previously started with [startStruct], copying content backwards so the
     * header sits exactly before it.
     * @param structType The 4-bit structure type.
     * @param start The start offset returned by [startStruct].
     * @return The start offset of the structure.
     */
    fun endStruct(structType: Int, start: Int): Int {
        val contentSize = end - start - 5
        require(contentSize >= 0) { "Structure size must be >= 0, but was: $contentSize" }
        val contentStart = start + 5
        val contentEnd = end
        end = start
        writeStructHeader(structType, contentSize)
        var source = contentStart
        if (end < source) {
            var e = this.end
            while (source < contentEnd) setInt8(e++, getInt8(source++))
            this.end = e
        } else {
            // Header exactly consumed the 5 reserved bytes (contentSize >= 65536);
            // content is already in place — just advance end past it.
            this.end = contentEnd
        }
        return start
    }

    /** Start an [Array]. */
    fun startArray(): Int = startStruct()

    /** Close an [Array]. */
    fun endArray(start: Int): Int = endStruct(JB2_STRUCT_ARRAY, start)

    /** Start an [Object] (string-keyed map). */
    fun startObject(): Int = startStruct()

    /** Close an [Object]. */
    fun endObject(start: Int): Int = endStruct(JB2_STRUCT_OBJECT, start)

    /** Start a [Map] (arbitrary primitive keys). */
    fun startMap(): Int = startStruct()

    /** Close a [Map]. */
    fun endMap(start: Int): Int = endStruct(JB2_STRUCT_MAP, start)

    /**
     * Write an object key. Uses the global book reference if available, otherwise interns the key
     * into the local book and writes a local reference. Keys are book references, never inline
     * strings, mirroring the JBON1 behaviour.
     * @param key The key to write.
     * @return The offset of the key written.
     */
    fun writeKey(key: String): Int {
        val start = end
        val global = this.global
        if (global != null) {
            val index = global.indexOf(key)
            if (index >= 0) {
                encodeRef(index, JB2_REF_BOOK_GLOBAL)
                return start
            }
        }
        val index = addToLocalDictionary(key)
        encodeRef(index, JB2_REF_BOOK_LOCAL)
        return start
    }

    // -----------------------------------------------------------------------
    // Recursive value encoding
    // -----------------------------------------------------------------------

    /**
     * Write an object recursively (string keys).
     * @param map The map to write.
     * @return The offset of the value written.
     */
    fun encodeObject(map: MapProxy<String, *>): Int {
        val start = startObject()
        for (entry in map) {
            val key = entry.key
            val value = entry.value
            writeKey(key)
            pushPath(key)
            try {
                encodeValue(value)
            } finally {
                popPath()
            }
        }
        endObject(start)
        return start
    }

    /**
     * When invoking [buildTupleFromMap] this captures the XYZ namespace reference, if found.
     */
    var xyz: MapProxy<String, *>? = null

    /** Write an array recursively. */
    fun encodeArray(array: Array<Any?>): Int {
        val start = startArray()
        var i = 0
        while (i < array.size) {
            pushPath(i)
            try {
                encodeValue(array[i])
            } finally {
                popPath()
            }
            i++
        }
        endArray(start)
        return start
    }

    /** Write an array (list proxy) recursively. */
    fun encodeList(array: ListProxy<*>): Int {
        val start = startArray()
        var i = 0
        while (i < array.size) {
            pushPath(i)
            try {
                encodeValue(array[i])
            } finally {
                popPath()
            }
            i++
        }
        endArray(start)
        return start
    }

    /**
     * Write a [JB2_STRUCT_TWKB] structure from raw TWKB bytes.
     *
     * The spec requires `ss != 00` (empty TWKB is invalid), so [bytes] must be non-empty.
     * @param bytes The raw TWKB bytes to embed.
     * @return The start offset of the written structure.
     * @throws IllegalArgumentException if [bytes] is empty.
     */
    fun encodeTwkb(bytes: ByteArray): Int {
        require(bytes.isNotEmpty()) { "TWKB bytes must not be empty" }
        val start = end
        writeStructHeader(JB2_STRUCT_TWKB, bytes.size)
        var i = 0
        while (i < bytes.size) setInt8(end++, bytes[i++])
        return start
    }

    /**
     * Write a [JB2_STRUCT_BYTE_ARRAY] structure from raw bytes.
     *
     * The spec requires `ss != 00` (empty ByteArray is invalid), so [bytes] must be non-empty.
     * @param bytes The raw bytes to embed.
     * @return The start offset of the written structure.
     * @throws IllegalArgumentException if [bytes] is empty.
     */
    fun encodeByteArray(bytes: ByteArray): Int {
        require(bytes.isNotEmpty()) { "ByteArray must not be empty" }
        val start = end
        writeStructHeader(JB2_STRUCT_BYTE_ARRAY, bytes.size)
        var i = 0
        while (i < bytes.size) setInt8(end++, bytes[i++])
        return start
    }

    /**
     * Encode an [SpGeometry] as a [JB2_STRUCT_TWKB] structure.
     *
     * Calls [GeoUtil.toTWKB] to obtain the TWKB byte representation of the geometry and then
     * delegates to [encodeTwkb]. On the JS platform [GeoUtil.toTWKB] is a stub that returns
     * `null`; in that case [encodeNull] is emitted instead.
     * @param geometry The geometry to encode.
     * @return The start offset of the written value.
     */
    fun encodeGeometry(geometry: SpGeometry): Int {
        val bytes = GeoUtil.toTWKB(geometry)
        return if (bytes != null && bytes.isNotEmpty()) encodeTwkb(bytes) else encodeNull()
    }

    /**
     * Write an arbitrary value, recursing into maps and arrays.
     * @param value The value to write.
     * @return The offset of the value written.
     */
    @Suppress("UNCHECKED_CAST")
    fun encodeValue(value: Any?): Int {
        val start = end
        val m = memberEncoder
        if (m != null) {
            val memberIndex = m.encode(path, pathEnd, value)
            if (memberIndex >= 0) {
                encodeRef(memberIndex, JB2_REF_BOOK_MEMBERS)
                return start
            }
        }
        when (value) {
            is Char -> if (global != null) encodeText(value.toString()) else encodeString(value.toString())
            is String -> if (global != null) encodeText(value) else encodeString(value)
            is Boolean -> encodeBool(value)
            is Byte -> encodeInt32(value.toInt())
            is Short -> encodeInt32(value.toInt())
            is Int -> encodeInt32(value)
            is Long -> encodeInt64(value.toInt64())
            is Int64 -> encodeInt64(value)
            is Float -> encodeFloat32(value)
            is Double -> if (Platform.canBeFloat32(value)) encodeFloat32(value.toFloat()) else encodeFloat64(value)
            is SpGeometry -> encodeGeometry(value)
            is ByteArray -> if (value.isNotEmpty()) encodeByteArray(value) else encodeNull()
            is MapProxy<*, *> -> encodeObject(value as MapProxy<String, *>)
            is ListProxy<*> -> encodeList(value)
            is Array<*> -> encodeArray(value as Array<Any?>)
            null -> encodeNull()
            else -> throw IllegalArgumentException("Could not encode value for type: ${value::class}")
        }
        return start
    }

    // -----------------------------------------------------------------------
    // Local book (Dictionary structure) emission
    // -----------------------------------------------------------------------

    /**
     * Write the local book as a [Dictionary] structure (`11ss_0101`) at [end]. The dictionary
     * payload is the optional id followed by the interned strings (in index order).
     * @param id The dictionary id; `null` for an anonymous local book.
     * @return The start offset of the dictionary structure.
     */
    private fun encodeLocalDictionary(id: String?): Int {
        val payloadStart = end + 5 // reserve header
        end = payloadStart
        if (id != null) encodeString(id) else encodeNull()
        val byIndex = localDictByIndex
        if (byIndex != null) for (s in byIndex) encodeString(s)
        val payloadEnd = end
        val payloadSize = payloadEnd - payloadStart
        // Write header into the reserved space and copy backwards if smaller.
        val headerStart = payloadStart - 5
        end = headerStart
        writeStructHeader(JB2_STRUCT_DICTIONARY, payloadSize)
        val headerEnd = end
        if (headerEnd < payloadStart) {
            var source = payloadStart
            var target = headerEnd
            while (source < payloadEnd) setInt8(target++, getInt8(source++))
            end = target
        } else {
            end = payloadEnd
        }
        return headerStart
    }

    // -----------------------------------------------------------------------
    // Tuple assembly
    // -----------------------------------------------------------------------

    /**
     * Assemble a JBON2 [Tuple] from a GeoJSON feature map.
     *
     * The produced bytes start with the `@JB\x02` file header (when [withHeader] is true), then a
     * [Tuple] structure (`11ss_1000`) whose content is the feature [Object] followed by the
     * embedded `local` [Book]. This is the minimal-but-conformant tuple; members/global books may
     * be appended by the caller in a later step.
     *
     * @param map The GeoJSON feature.
     * @param withHeader Whether to prepend the `@JB\x02` file header.
     * @return The JBON2 tuple bytes.
     */
    fun buildTupleFromMap(map: MapProxy<String, *>, withHeader: Boolean = true): ByteArray {
        clear()
        xyz = null
        // Encode the feature object into a temporary region first.
        val featureStart = encodeObject(map)
        return assembleTuple(featureStart, withHeader)
    }

    /**
     * Assemble a [Tuple] given that the feature object has already been written starting at
     * [featureStart] and ending at the current [end].
     *
     * Layout produced (content of the tuple structure):
     *   - feature [Object]
     *   - local [Book] (as a [Dictionary] holding interned strings)
     *
     * @param featureStart The offset where the feature object starts (usually `0`).
     * @param withHeader Whether to prepend the `@JB\x02` file header.
     * @return The JBON2 tuple bytes.
     */
    private fun assembleTuple(featureStart: Int, withHeader: Boolean): ByteArray {
        val featureEnd = end
        val featureSize = featureEnd - featureStart

        // Append the local book (dictionary) right after the feature.
        val localBookStart = end
        encodeLocalDictionary(null)
        val localBookEnd = end
        val localBookSize = localBookEnd - localBookStart

        val tupleContentSize = featureSize + localBookSize

        // Write the tuple header after everything else, then we copy into the final array.
        val tupleHeaderStart = end
        writeStructHeader(JB2_STRUCT_TUPLE, tupleContentSize)
        val tupleHeaderEnd = end
        val tupleHeaderSize = tupleHeaderEnd - tupleHeaderStart

        val headerSize = if (withHeader) JB2_MAGIC.size else 0
        val targetSize = headerSize + tupleHeaderSize + tupleContentSize
        val target = ByteArray(targetSize)
        val targetView = DataViewProxy(target)
        var t = 0

        if (withHeader) {
            for (b in JB2_MAGIC) targetView.setInt8(t++, b)
        }
        // tuple header
        var source = tupleHeaderStart
        while (source < tupleHeaderEnd) targetView.setInt8(t++, getInt8(source++))
        // feature object
        source = featureStart
        while (source < featureEnd) targetView.setInt8(t++, getInt8(source++))
        // local book
        source = localBookStart
        while (source < localBookEnd) targetView.setInt8(t++, getInt8(source++))
        return target
    }
}
