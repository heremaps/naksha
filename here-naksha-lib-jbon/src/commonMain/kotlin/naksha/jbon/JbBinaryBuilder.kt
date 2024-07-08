package naksha.jbon;

import naksha.base.*
import naksha.base.PlatformDataViewApi.Companion.dataview_get_size
import naksha.base.PlatformUtil.Companion.defaultDataViewSize
import kotlin.js.*
import kotlin.jvm.JvmStatic
import kotlin.math.floor

/**
 * Creates a new JBON builder using the given view and global dictionary.
 * @property global The global dictionary to use when encoding; if any.
 */
@Suppress("DuplicatedCode", "MemberVisibilityCanBePrivate", "OPT_IN_USAGE")
@JsExport
open class JbBinaryBuilder(var global: JbDict? = null) : Binary() {

    /**
     * Create a new resizable editor with a new byte-array of the given size backing it.
     * @param size The amount of byte to allocate initially.
     * @param global The global dictionary to use when encoding; if any.
     */
    @Suppress("LeakingThis")
    @JsName("forSize")
    constructor(size: Int, global: JbDict? = null) : this(global) {
        view = Platform.newDataView(ByteArray(size))
        this.readOnly = false
        this.resize = true
    }

    /**
     * Creates a new resizable editor about the given data-view.
     * @param binaryView The view for which to create a proxy.
     * @param pos The position in the view to start reading; defaults to `0`.
     * @param end The position in the view to stop reading at (first position to **not** read); defaults to `view.byteLength`.
     * @param global The global dictionary to use when encoding; if any.
     */
    @Suppress("LeakingThis")
    @JsName("forBinary")
    constructor(binaryView: BinaryView, pos: Int = binaryView.pos, end: Int = binaryView.end, global: JbDict? = null) : this(global) {
        this.view = binaryView.view
        this.pos = pos
        this.end = end
        this.readOnly = false
        this.resize = true
    }

    /**
     * Creates a new resizable editor about the given data-view.
     * @param view The view for which to create a proxy.
     * @param pos The position in the view to start reading; defaults to `0`.
     * @param end The position in the view to stop reading at (first position to **not** read); defaults to `view.byteLength`.
     * @param global The global dictionary to use when encoding; if any.
     */
    @Suppress("LeakingThis")
    @JsName("forDataView")
    constructor(view: PlatformDataView, pos: Int = 0, end: Int = dataview_get_size(view), global: JbDict? = null) : this(global) {
        this.view = view
        this.pos = pos
        this.end = end
        this.readOnly = false
        this.resize = true
    }

    /**
     * Creates a new resizable editor with a new data-view about the given byte-array.
     * @param byteArray The byte-array to view.
     * @param offset The first byte to view.
     * @param length The amount of byte to view; defaults to everything from [offset] to `byteArray.size`.
     * @param global The global dictionary to use when encoding; if any.
     */
    @Suppress("LeakingThis")
    @JsName("forUint8Array")
    constructor(byteArray: ByteArray, offset: Int = 0, length: Int = byteArray.size - offset, global: JbDict? = null) : this(global) {
        view = Platform.newDataView(byteArray, offset, length)
        this.pos = 0
        this.end = dataview_get_size(view)
        this.readOnly = false
        this.resize = true
    }

    @OptIn(ExperimentalJsStatic::class)
    companion object {
        /**
         * An array that stores _true_ for every character that should belong to a **word**, when auto-splitting
         * strings for the local dictionary. Ones a character being _false_ is found, the string is split at that
         * point and a new sub-string is created.
         *
         * All characters that do not belong to words, are always individually encoded as unicode points, they
         * can't form entries in the dictionary.
         *
         * By default, digits are not part of words, therefore numbers are not added to the local dictionary.
         * The reason is that we often find strings that have numbers in them, but except for the numbers nothing
         * else changes, for example `urn:here::here:Topology:123245678`.
         */
        @JvmStatic
        @JsStatic
        val wordUnicode = BooleanArray(128) {
            (it >= 'a'.code && it <= 'z'.code)
                    || (it >= 'A'.code && it <= 'Z'.code)
                    || it == ':'.code
        }

        /**
         * Create a new builder with a buffer of the given size.
         * @param size The buffer size to use; if _null_ a default is selected.
         * @param global The global dictionary to use for the builder; if any.
         * @return The builder.
         */
        @JvmStatic
        @Deprecated("There is now a explict real static constructor, use it", ReplaceWith("JbBuilder(size, global)"))
        fun create(size: Int? = null, global: JbDict? = null): JbBinaryBuilder = JbBinaryBuilder(size ?: defaultDataViewSize, global)

        /**
         * Returns the maximal encoding size of a string.
         * @param string The string for which to calculate maximum byte-size.
         * @return The maximum byte-size.
         */
        @JvmStatic
        @JsStatic
        fun maxSizeOfString(string: String): Int = string.length * 3 + 5

        /**
         * Calculate the amount of bytes needed to store the given integer value.
         * @param value The value to store.
         * @return The amount of byte that are needed to encode this integer.
         */
        @JvmStatic
        @JsStatic
        fun sizeOfIntEncoding(value: Int): Int {
            return when (value) {
                in -16..15 -> 1
                in -128..127 -> 2
                in -32768..32767 -> 3
                else -> 5
            }
        }

        /**
         * Calculate the amount of bytes needed to store the given 64-bit integer value.
         * @param value The value to store.
         * @return The amount of byte that are needed to encode this 64-bit integer.
         */
        @JvmStatic
        @JsStatic
        fun sizeOfInt64Encoding(value: Int64): Int {
            if (value >= Int.MAX_VALUE || value <= Int.MIN_VALUE) return 9
            return sizeOfIntEncoding(value.toInt())
        }

        /**
         * Calculate the size of the header of a structure.
         * @param payloadSize The size of the payload in byte.
         * @param variant The variant to encode; if any.
         * @return The size of the header in byte.
         */
        @JvmStatic
        @JsStatic
        fun sizeOfStructHeader(payloadSize: Int, variant: Int? = null): Int {
            var size = 1 // lead-in byte
            size += when (payloadSize) {
                0 -> 0
                in 1..255 -> 1
                in 256..65535 -> 2
                else -> 4
            }
            size += when (variant) {
                null -> 0
                in 0..255 -> 1
                in 256..65535 -> 2
                else -> 4
            }
            return size
        }

        /**
         * Calculate the amount of byte requires to encode the given unicode.
         * @param unicode The code-point.
         * @return The amount of byte requires to encode the code-point; 0 if the code point is invalid.
         */
        @JvmStatic
        @JsStatic
        fun sizeOfUnicode(unicode: Int): Int {
            return when (unicode) {
                in 0..127 -> 1
                in 128..16511 -> 2
                in 16512..2_097_151 -> 3
                else -> 0
            }
        }
    }

    /**
     * When encoding a string that is not in the global dictionary, then we add it into this collection, so that it can
     * be encoded later into the local dictionary. The key is the string that has is needed, the value is the index that
     * is being used to encode it.
     */
    private var localDictByName: HashMap<String, Int>? = null

    /**
     * Needed to resolve the strings being in the local dictionary by index.
     */
    private var localDictByIndex: ArrayList<String>? = null

    internal fun getLocalDictByString(): HashMap<String, Int> {
        var localDictByName = this.localDictByName
        if (localDictByName == null) {
            localDictByName = HashMap()
            this.localDictByName = localDictByName
        }
        return localDictByName
    }

    private fun getLocalDictByIndex(): ArrayList<String> {
        var localDictByIndex = this.localDictByIndex
        if (localDictByIndex == null) {
            localDictByIndex = ArrayList()
            this.localDictByIndex = localDictByIndex
        }
        return localDictByIndex
    }

    /**
     * The next index to use, when adding a string into the local dictionary.
     */
    private var localDictNextIndex: Int = 0

    /**
     * Clear the builder, set [pos] and [end] to `0`, and return the old [end] position. Clears the local dictionary,
     * but leaves the global dictionary intact.
     * @return The old [end].
     */
    open fun clear(): Int {
        localDictByName = null
        localDictByIndex = null
        localDictNextIndex = 0
        return reset()
    }

    /**
     * Write a NULL value.
     * @return The offset of the value written.
     */
    fun encodeNull(): Int {
        val pos = end
        writeInt8(ENC_MIXED_CONST_NULL.toByte())
        return pos
    }

    /**
     * Write an undefined value.
     * @return The offset of the value written.
     */
    fun encodeUndefined(): Int {
        val pos = end;
        writeInt8(ENC_MIXED_CONST_UNDEFINED.toByte())
        return pos
    }

    /**
     * Write an boolean value.
     * @param value The boolean to write.
     * @return The offset of the value written.
     */
    fun encodeBool(value: Boolean): Int {
        val pos = end;
        if (value) {
            writeInt8(ENC_MIXED_CONST_TRUE.toByte())
        } else {
            writeInt8(ENC_MIXED_CONST_FALSE.toByte())
        }
        return pos
    }

    /**
     * Write an 32-bit integer.
     * @param value The integer to write.
     * @return The offset of the value written.
     */
    fun encodeInt(value: Int): Int {
        val offset = end;
        when (value) {
            in -16..15 -> {
                writeInt8((ENC_TINY or ENC_TINY_INT or (value and 0x1f)).toByte())
            }

            in -128..127 -> {
                writeInt8(ENC_MIXED_SCALAR_INT8.toByte())
                writeInt8(value.toByte())
            }

            in -32768..32767 -> {
                writeInt8(ENC_MIXED_SCALAR_INT16.toByte())
                writeInt16(value.toShort())
            }

            else -> {
                writeInt8(ENC_MIXED_SCALAR_INT32.toByte())
                writeInt32(value)
            }
        }
        return offset
    }

    /**
     * Write an 64-bit integer.
     * @param value The integer to write.
     * @return The offset of the value written.
     */
    fun encodeInt64(value: Int64): Int {
        if (value >= Int.MIN_VALUE && value <= Int.MAX_VALUE) {
            return encodeInt(value.toInt())
        }
        val offset = end;
        writeInt8(ENC_MIXED_SCALAR_INT64.toByte())
        writeInt64(value)
        return offset
    }

    /**
     * Write an 48-bit unsigned integer which represents a Unix-Epoch-Timestamp im milliseconds.
     * @param value The timestamp to write.
     * @return The offset of the value written.
     */
    fun encodeTimestamp(value: Int64): Int {
        val offset = end;
        writeInt8(ENC_MIXED_SCALAR_TIMESTAMP.toByte())
        val hi = (value ushr 32).toShort()
        val lo = value.toInt()
        writeInt16(hi)
        writeInt32(lo)
        return offset
    }

    /**
     * Write a 32-bit floating point number.
     * @param value The value to write.
     * @return The offset of the value written.
     */
    fun encodeFloat(value: Float): Int {
        val pos = end
        if (value >= -16.0 && value <= 15.0 && value == floor(value)) {
            val i = value.toInt() and 0x1f
            writeInt8((ENC_TINY or ENC_TINY_FLOAT or i).toByte())
            return pos
        }
        writeInt8(ENC_MIXED_SCALAR_FLOAT32.toByte())
        writeFloat32(value)
        return pos
    }

    /**
     * Write a 64-bit floating point number.
     * @param value The value to write.
     * @return The offset of the value written.
     */
    fun encodeDouble(value: Double): Int {
        val pos = end
        if (value >= -16.0 && value <= 15.0 && value == floor(value)) {
            val i = value.toInt() and 0x1f
            writeInt8((ENC_TINY or ENC_TINY_FLOAT or i).toByte())
            return pos
        }
        writeInt8(ENC_MIXED_SCALAR_FLOAT64.toByte())
        writeFloat64(value)
        return pos
    }

    /**
     * Write a reference. Note that a negative value represents null.
     * @param index The index into the dictionary.
     * @param global If the reference is into global-dictionary (true) or local (false).
     * @return The offset of the value written.
     */
    fun encodeRef(index: Int, global: Boolean): Int {
        val offset = end
        val globalBit = if (global) ENC_MIXED_REF_GLOBAL_BIT else 0
        if (index < 0) {
            // null
            writeInt8((globalBit or ENC_MIXED_REF_NULL).toByte())
            return offset
        }
        if (index < 16) {
            // tiny reference
            writeInt8(((if (global) ENC_MIXED_REF5_GLOBAL else ENC_MIXED_REF5_LOCAL).toInt() or index).toByte())
            return offset
        }
        val value = index - 16
        if (value < 256) {
            writeInt8((globalBit or ENC_MIXED_REF_INT8).toByte())
            writeInt8(value.toByte())
        } else if (value < 65536) {
            writeInt8((globalBit or ENC_MIXED_REF_INT16).toByte())
            writeInt16(value.toShort())
        } else {
            writeInt8((globalBit or ENC_MIXED_REF_INT32).toByte())
            writeInt32(value)
        }
        return offset
    }

    /**
     * Add the given string to the local dictionary, if the same string is already in the dictionary, the existing will be returned.
     * @param string The string to add.
     * @return The index of the string.
     */
    fun addToLocalDictionary(string: String): Int {
        val localDict = getLocalDictByString()
        var index = localDict[string]
        if (index != null) {
            return index
        }
        index = localDictNextIndex++
        localDict[string] = index
        getLocalDictByIndex().add(string)
        return index
    }

    /**
     * Encodes the given string into this binary. This method does not take references into account, it really only encodes a string.
     * @param string The string to encode.
     * @return The offset of the value written.
     */
    fun encodeString(string: String): Int {
        val start = end
        // We reserve 5 byte for the header.
        var pos = end + 5
        var i = 0
        while (i < string.length) {
            val hi = string[i++]
            var unicode: Int
            if (i < string.length && hi.isHighSurrogate()) {
                val lo = string[i++]
                require(lo.isLowSurrogate())
                unicode = CodePoints.toCodePoint(hi, lo)
            } else {
                unicode = hi.code
            }
            check(unicode in 0..2_097_151)
            pos = writeUnicode(this, pos, unicode)
        }
        // Calculate the size of the string.
        val size = pos - start - 5
        var source = start + 5
        // If the header is smaller than 5 byte, we need copy the data backwards.
        var target = writeStringHeader(this, start, size)
        if (target < source) {
            while (source < pos) {
                // TODO: Optimized this by adding support for a native copy function into P_DataView
                setInt8(target++, getInt8(source++))
            }
            pos = target
        }
        end = pos
        return start
    }

    /**
     * Writes a string reference, normally only used inside a text.
     * @param offset The offset at which write into the view.
     * @param index The index to write.
     * @param isGlobal If the index is into the global dictionary (true) or into the local (false).
     * @param add If nothing, a space, an underscore or a colon should be added (must be 0 to 3).
     * @return The end offset.
     */
    private fun encodeStringRef(offset: Int, index: Int, isGlobal: Boolean, add: Int): Int {
        require(add in 0..3)
        require(index >= 0)
        var pos = offset
        var leadIn = 0b111_00_000 or (add shl 3)
        if (isGlobal) {
            leadIn = leadIn or 0b00000_100
        }
        if (index < 256) {
            setInt8(pos, (leadIn or 1).toByte())
            setInt8(pos + 1, index.toByte())
            pos += 2
        } else if (index < 65536) {
            setInt8(pos, (leadIn or 2).toByte())
            setInt16(pos + 1, index.toShort())
            pos += 3
        } else {
            setInt8(pos, (leadIn or 3).toByte())
            setInt32(pos + 1, index)
            pos += 5
        }
        return pos
    }

    /**
     * Encodes the given string as text, that means using the local and global dictionary, if the latter is available.
     * @param string The string to encode.
     * @return The offset of the value written.
     */
    fun encodeText(string: String): Int {
        val sb = StringBuilder()
        val start = end
        val global = this.global
        // We reserve the header.
        // One byte for lead-in, plus, worst case is, that each UTF-16 character uses 3 byte
        val headerReservedSize = 1 + when (string.length * 3) {
            in 0..60 -> 0
            in 61..316 -> 1
            in 317..65535 -> 2
            else -> 4
        }
        var pos = end + headerReservedSize
        var i = 0
        var wordStart = -1
        // Allocate maximum size needed, we know, we do not need any allocations in between.
        char_loop@ while (i < string.length) {
            val hi = string[i++]
            var unicode: Int
            if (i < string.length && hi.isHighSurrogate()) {
                val lo = string[i++]
                require(lo.isLowSurrogate())
                unicode = CodePoints.toCodePoint(hi, lo)
            } else {
                unicode = hi.code
            }
            check(unicode in 0..2_097_151)
            // Test if we found a code that is a word-code.
            val isWordCode = unicode < 128 && wordUnicode[unicode]
            if (isWordCode && wordStart < 0) {
                // Start word detection.
                wordStart = pos
            }
            if (wordStart >= 0 && (!isWordCode || i == string.length)) {
                // We only compress words being 3 or more byte
                var size = pos - wordStart
                if (isWordCode && size >= 2) {
                    // We only enter this when
                    // - We are at the end of the string
                    // - The current unicode (last character) is a valid word-code
                    // - We have at least two more bytes in the current word.
                    pos = writeUnicode(this, pos, unicode)
                    // We know that the unicode is only one byte (is < 128)
                    size++
                }
                if (size >= 3) {
                    sb.clear()
                    JbReader.readSubstring(this, wordStart, pos, sb)
                    val subString = sb.toString()
                    val isGlobal: Boolean
                    var index = -1
                    if (global != null) {
                        index = global.indexOf(subString)
                        if (index < 0) {
                            // Let's try for URNs, which are for example "urn:here:mom:Topology:123456".
                            // In that case, "urn:here:mom:Topology:" is always the same.
                            var reversePos = pos - 1
                            val stopAt = wordStart + 3
                            while (reversePos > stopAt) {
                                val c = getInt8(reversePos).toInt() and 0xff
                                if (':'.code == c) {
                                    // We found a colon at reversePos
                                    // Try to look it up in the global dictionary (reversePos is excluded below).
                                    sb.clear()
                                    JbReader.readSubstring(this, wordStart, reversePos, sb)
                                    val prefix = sb.toString()
                                    index = global.indexOf(prefix)
                                    if (index >= 0) {
                                        // Found the prefix in the global dict, now we encode the prefix as reference.
                                        pos = encodeStringRef(wordStart, index, true, ADD_COLON)
                                        // Seek back behind the colon.
                                        i = reversePos + 1
                                        // A new word starts
                                        wordStart = -1
                                        // Continue normal reading.
                                        continue@char_loop
                                    }
                                }
                                reversePos--
                            }
                            // When we reach this, we did not find a colon.
                        }
                    }
                    if (index < 0) {
                        index = addToLocalDictionary(subString)
                        isGlobal = false
                    } else {
                        isGlobal = true
                    }
                    check(index >= 0)
                    val add = when (unicode) {
                        ' '.code -> ADD_SPACE
                        '_'.code -> ADD_UNDERSCORE
                        ':'.code -> ADD_COLON
                        else -> ADD_NOTHING
                    }
                    // Moved back to where the word started and encode the reference instead
                    pos = encodeStringRef(wordStart, index, isGlobal, add)
                    wordStart = -1
                    // If we're currently on a space or underscore, we do not need to encode it, it is embedded.
                    // If we hit the end of the string, we have added the code already.
                    if (add > 0 || i == string.length) continue
                }
                // Word ends now.
                wordStart = -1
            }
            pos = writeUnicode(this, pos, unicode)
        }
        // Calculate the size of the string.
        val size = pos - start - headerReservedSize
        var source = start + headerReservedSize
        // If the header is smaller than 5 byte, we need copy the data backwards.
        var target = writeStringHeader(this, start, size)
        if (target < source) {
            while (source < pos) {
                // TODO: Optimized this by adding support for a native copy function into P_DataView
                setInt8(target++, getInt8(source++))
            }
            pos = target
        }
        end = pos
        return start
    }

    private fun writeUnicode(view: BinaryView, offset: Int, unicode: Int): Int {
        require(unicode in 0..2_097_151)
        var pos = offset
        when (unicode) {
            in 0..127 -> view.setInt8(pos++, unicode.toByte())
            in 128..16511 -> { // 0 -> 2^14-1 biased by 128
                // BIAS the unicode value
                val biased = unicode - 128
                // Encode the higher 6 bit
                view.setInt8(pos++, ((biased ushr 8) or 0b10_000000).toByte())
                // Encode the lower 8 bit
                view.setInt8(pos++, (biased and 0xff).toByte())
            }

            else -> {
                // Full code point encoding
                // Encode the top 5 bit
                view.setInt8(pos++, ((unicode ushr 16) or 0b110_00000).toByte())
                // Encode the lower 16 bit (big endian)
                view.setInt16(pos, (unicode and 0xffff).toShort())
                pos += 2
            }
        }
        return pos
    }

    private fun writeStringHeader(view: BinaryView, offset: Int, size: Int): Int {
        require(size >= 0) { "The string must not be of a size less than zero: $size" }
        if (size < 61) {
            view.setInt8(offset, (ENC_STRING or size).toByte())
            return offset + 1
        }
        if (size < 316) { // 255 + 61
            view.setInt8(offset, (ENC_STRING or 61).toByte())
            view.setInt8(offset + 1, (size - 61).toByte())
            return offset + 2
        }
        if (size < 65536) {
            view.setInt8(offset, (ENC_STRING or 62).toByte())
            view.setInt16(offset + 1, size.toShort())
            return offset + 3
        } else {
            view.setInt8(offset, (ENC_STRING or 63).toByte())
            view.setInt32(offset + 1, size)
            return offset + 5
        }
    }

    /**
     * Starts a structure and returns the offset where the structure was started, needed to close the structure. The content of the
     * structure can be written simply after having started the structure. This method just adds 5 (maximum header size) to the [end].
     * @return The offset where the structure was started.
     */
    fun startStruct(): Int {
        val start = end
        // We need 1-byte lead-in
        // optional: 4 byte for the size.
        // optional: 4 byte for the variant.
        end += 9
        return start
    }

    /**
     * Write the header of a structure to the [end] of the builder.
     * @param structType The type of the structure.
     * @param variant The variant to encode.
     * @param payloadSize The size of the payload in byte.
     * @return The start of the header.
     */
    internal fun writeStructHeader(structType: Int, variant: Int?, payloadSize: Int): Int {
        val start = end
        require(payloadSize >= 0) { "Structure size must be greater/equal zero, but was: $payloadSize" }
        var leadIn = ENC_STRUCT or structType
        end++ // We write the lead-in later
        if (payloadSize == 0) {
            leadIn = leadIn or ENC_STRUCT_SIZE0
        } else if (payloadSize < 256) {
            leadIn = leadIn or ENC_STRUCT_SIZE8
            writeInt8(payloadSize.toByte())
        } else if (payloadSize < 65536) {
            leadIn = leadIn or ENC_STRUCT_SIZE16
            writeInt16(payloadSize.toShort())
        } else {
            leadIn = leadIn or ENC_STRUCT_SIZE32
            writeInt32(payloadSize)
        }
        if (variant == null) {
            leadIn = leadIn or ENC_STRUCT_VARIANT0
        } else if (variant < 256) {
            leadIn = leadIn or ENC_STRUCT_VARIANT8
            writeInt8(variant.toByte())
        } else if (variant < 65536) {
            leadIn = leadIn or ENC_STRUCT_VARIANT16
            writeInt16(variant.toShort())
        } else {
            leadIn = leadIn or ENC_STRUCT_VARIANT32
            writeInt32(variant)
        }
        setInt8(start, leadIn.toByte())
        return start
    }

    /**
     * Ends a structure that has previously been started with [startStruct]. If necessary (most of the time) copy the structure
     * content backwards, so that the header is exactly before the structure content.
     * @param structType The type of the structure.
     * @param variant The variant.
     * @param start The start of the array as returned by [startArray].
     * @return The start of the structure (same value as given).
     */
    fun endStruct(structType: Int, variant: Int?, start: Int): Int {
        val payloadSize = end - start - 9
        require(payloadSize >= 0) { "Structure size must be greater/equal zero, but was: $payloadSize" }
        val contentStart = start + 9
        val contentEnd = end
        end = start
        writeStructHeader(structType, variant, payloadSize)
        // Copy from where the content backwards.
        var source = contentStart
        if (end < source) {
            var end = this.end
            while (source < contentEnd) {
                setInt8(end++, getInt8(source++))
            }
            this.end = end
        }
        return start
    }

    /**
     * Ends a custom variant structure that has previously been started with [startStruct]. If necessary (most of the time) copy
     * the structure content backwards, so that the header is exactly before the structure content.
     * @param start The start of the array as returned by [startStruct].
     * @param variant The variant to write.
     * @return The start of the structure (same value as given).
     */
    fun endCustomStruct(start: Int, variant: Int): Int = endStruct(ENC_STRUCT_VARIANT_CUSTOM, variant, start)

    /**
     * Starts an array and returns the offset where the array was started, needed to close the array.
     * The content of the array can be written simply after having started the array.
     * @return The offset where the array was started.
     */
    fun startArray(): Int = startStruct()

    /**
     * Ends an array.
     * @param start The start of the array as returned by [startArray].
     * @return The start of the array again.
     */
    fun endArray(start: Int): Int = endStruct(ENC_STRUCT_ARRAY, null, start)

    /**
     * Starts a map and returns the offset where the map was started, needed to close the map.
     * The content of the map can be written simply after having started the map.
     * @return The offset where the map was started.
     */
    fun startMap(): Int = startStruct()

    /**
     * Writes the given key into a map, requires that [startMap] has been called. Uses the global and local dictionaries.
     *
     * @param key The key to write.
     * @return The previously written key.
     */
    fun writeKey(key: String): Int {
        val start = end
        val global = this.global
        var index: Int
        if (global != null) {
            index = global.indexOf(key)
            if (index >= 0) {
                encodeRef(index, true)
                return start
            }
        }
        index = addToLocalDictionary(key)
        encodeRef(index, false)
        return start
    }

    /**
     * Ends a map.
     * @param start The start of the map as returned by [startMap].
     * @return The start of the map again.
     */
    fun endMap(start: Int): Int = endStruct(ENC_STRUCT_MAP, null, start)

    /**
     * Write the local dictionary into the builder.
     * @param id The identifier to write into the dictionary.
     * @return The start of the local dictionary.
     */
    fun encodeLocalDictionary(id: String? = null): Int {
        val start = startStruct()
        if (id != null) encodeString(id) else encodeNull()
        val localStringById = this.localDictByIndex
        if (localStringById != null) {
            for (string in localStringById) {
                encodeString(string)
            }
        }
        // TODO: Improve, so that endStruct is not always copy data (make it optional)
        //       It can as well adjust the structure header, so place it in-front or copy structure back!
        return endStruct(ENC_STRUCT_DICTIONARY, null, start)
    }

    /**
     * Creates a global dictionary out of the local dictionary of this builder. Requires an empty builder, so call [reset] before calling
     * this method.
     * @param id The unique identifier of the dictionary.
     * @return The dictionary.
     */
    fun buildDictionary(id: String): ByteArray {
        check(end == 0) { "The builder must be empty to create a global dictionary from the local, end: $end" }
        val start = encodeLocalDictionary(id)
        return byteArray.copyOfRange(start, end)
    }

    /**
     * Expects a GeoJSON feature as input and convert it into JBON. The first being the JBON feature,
     * the second being the XYZ namespace, the third being the geometry.
     * @param map The GeoJSON feature to convert into JBON.
     * @return The JBON representation of the feature, the XYZ-namespace and the geometry.
     */
    fun buildFeatureFromMap(map: MapProxy<String, *>): ByteArray {
        clear()
        val id: String? = map.getAs("id", String::class)
        xyz = null
        val start = startMap()
        for (entry in map) {
            val key = entry.key
            val value = entry.value
            if ("id" == key || "geometry" == key) continue
            writeKey(entry.key)
            if ("properties" == key && value is MapProxy<*, *>) {
                @Suppress("UNCHECKED_CAST")
                encodeMap(value as MapProxy<String, *>, true)
            } else {
                encodeValue(value)
            }
        }
        endMap(start)
        return buildFeature(id)
    }

    /**
     * When invoking [buildFeatureFromMap] this is used to capture the XYZ namespace reference, if any is found.
     */
    var xyz: MapProxy<String, *>? = null

    /**
     * Writes a map recursively.
     * @param map The map to write.
     * @param ignoreXyzNs If the key _@ns:com:here:xyz_ should be ignored (a reference is added to [xyz]).
     * @return The offset of the value written.
     */
    fun encodeMap(map: MapProxy<String, *>, ignoreXyzNs: Boolean = false): Int {
        val start = startMap()
        for (entry in map) {
            val key = entry.key
            val value = entry.value
            if (ignoreXyzNs && ("@ns:com:here:xyz" == key)) {
                @Suppress("UNCHECKED_CAST")
                if (value is MapProxy<*, *>) xyz = value as MapProxy<String, *>
                continue
            }
            writeKey(entry.key)
            encodeValue(entry.value)
        }
        endMap(start)
        return start
    }

    /**
     * Writes an array recursively.
     * @param array The array to write.
     * @return The offset of the value written.
     */
    fun encodeArray(array: Array<Any?>): Int {
        val start = startArray()
        for (value in array) encodeValue(value)
        endArray(start)
        return start
    }

    /**
     * Writes an array recursively.
     * @param array The array to write.
     * @return The offset of the value written.
     */
    fun encodeList(array: ListProxy<*>): Int {
        val start = startArray()
        for (value in array) encodeValue(value)
        endArray(start)
        return start
    }

    /**
     * Writes an arbitrary value, recursive if a map or array are provided.
     * @param value The value to write.
     * @return The offset of the value written.
     * @throws IllegalArgumentException If the given value is not writable.
     */
    @Suppress("UNCHECKED_CAST")
    fun encodeValue(value: Any?): Int {
        val start = end
        when (value) {
            is Char -> encodeString(value.toString())
            is String -> encodeString(value)
            is Boolean -> encodeBool(value)
            is Byte -> encodeInt(value.toInt())
            is Short -> encodeInt(value.toInt())
            is Int -> encodeInt(value)
            is Long -> encodeInt64(value.toInt64())
            is Int64 -> encodeInt64(value)
            is Float -> encodeFloat(value)
            is Double -> if (Platform.canBeFloat32(value)) encodeFloat(value.toFloat()) else encodeDouble(value)
            is MapProxy<*, *> -> encodeMap(value as MapProxy<String, *>)
            is ListProxy<*> -> encodeList(value)
            is Array<*> -> encodeArray(value as Array<Any?>)
            null -> encodeNull()
            else -> throw IllegalArgumentException()
        }
        return start
    }

    /**
     * Creates a feature out of this builder and the current local dictionary.
     * @param id The unique identifier of the feature, may be null.
     * @param variant The variant to write.
     * @return The feature.
     */
    fun buildFeature(id: String? = null, variant: Int = 0): ByteArray {
        // The content (was already written).
        val startOfFeaturePayload = 0
        check(end > 0) { "Can't build empty feature" }
        val endOfFeaturePayload = end
        val sizeOfFeaturePayload = endOfFeaturePayload - startOfFeaturePayload

        // Write the local dictionary (without header)
        val startOfLocalDictPayload = end
        encodeNull() // The local dictionary does not have an ID.
        val localDictByIndex = this.localDictByIndex
        if (localDictByIndex != null) {
            for (string in localDictByIndex) {
                encodeString(string)
            }
        }
        val endOfLocalDictPayload = end
        val sizeOfLocalDictPayload = endOfLocalDictPayload - startOfLocalDictPayload

        // Write the local dictionary header.
        val startOfLocalDictHeader = end
        writeStructHeader(ENC_STRUCT_DICTIONARY, null, sizeOfLocalDictPayload)
        val endOfLocalDictHeader = end
        val sizeOfLocalDictHeader = endOfLocalDictHeader - startOfLocalDictHeader

        // Write the feature-id, we will eventually copy that into the target.
        val startOfFeatureId = end
        if (id != null) {
            if (global != null) encodeText(id) else encodeString(id)
        } else {
            encodeNull()
        }
        val endOfFeatureId = end
        val sizeOfFeatureId = endOfFeatureId - startOfFeatureId

        // Write the id of the global dictionary.
        val startOfGlobalDictId = end
        val featureId: String? = global?.id()
        if (featureId != null) {
            encodeString(featureId)
        } else {
            encodeNull()
        }
        val endOfGlobalDictId = end
        val sizeOfGlobalDictId = endOfGlobalDictId - startOfGlobalDictId

        // Calculate the inner size
        val sizeOfFeature = sizeOfGlobalDictId + sizeOfFeatureId +
                sizeOfLocalDictHeader + sizeOfLocalDictPayload +
                sizeOfFeaturePayload

        // Write the header of the feature.
        val startOfFeatureHeader = end
        writeStructHeader(ENC_STRUCT_VARIANT_FEATURE, variant, sizeOfFeature)
        val endOfFeatureHeader = end
        val sizeOfFeatureHeader = endOfFeatureHeader - startOfFeatureHeader

        // Calculate the outer size
        val targetSize = sizeOfFeatureHeader + sizeOfFeature

        // Now, copy everything together into a target array.
        val targetArray = ByteArray(targetSize)
        val targetView = DataViewProxy(targetArray)
        var target = 0

        // Copy feature header.
        var source = startOfFeatureHeader
        while (source < endOfFeatureHeader) {
            targetView.setInt8(target++, getInt8(source++))
        }
        // Copy the global dictionary id.
        source = startOfGlobalDictId
        while (source < endOfGlobalDictId) {
            targetView.setInt8(target++, getInt8(source++))
        }
        // Copy the feature id.
        source = startOfFeatureId
        while (source < endOfFeatureId) {
            targetView.setInt8(target++, getInt8(source++))
        }
        // Copy header of local dictionary.
        source = startOfLocalDictHeader
        while (source < endOfLocalDictHeader) {
            targetView.setInt8(target++, getInt8(source++))
        }
        // Copy local dict content.
        source = startOfLocalDictPayload
        while (source < endOfLocalDictPayload) {
            targetView.setInt8(target++, getInt8(source++))
        }
        // Copy the feature payload.
        source = startOfFeaturePayload
        while (source < endOfFeaturePayload) {
            targetView.setInt8(target++, getInt8(source++))
        }
        return targetArray
    }
}