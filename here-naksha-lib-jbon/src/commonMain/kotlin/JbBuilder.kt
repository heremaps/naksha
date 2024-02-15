@file:OptIn(ExperimentalJsExport::class)

package com.here.naksha.lib.jbon;

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.math.floor

/**
 * Creates a new JBON builder using the given view and optional dictionary.
 */
@Suppress("DuplicatedCode")
@JsExport
open class JbBuilder(val view: IDataView, val global: JbDict? = null) {
    companion object {
        val wordUnicode = BooleanArray(128) {
            (it >= 'a'.code && it <= 'z'.code) || (it >= 'A'.code && it <= 'Z'.code) || it == ':'.code
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
    private var localNextIndex: Int = 0

    /**
     * The current end in the view.
     */
    var end: Int = 0

    /**
     * Reset the builder to the start and return the end position that was overridden.
     * @return The overridden end position.
     */
    fun reset(): Int {
        val old = end
        end = 0
        localDictByName = null
        localDictByIndex = null
        localNextIndex = 0
        return old
    }

    /**
     * Reset the builder to the start and return the end position that was overridden. Leave the local dictionary alone.
     * @return The overridden end position.
     */
    fun resetView(): Int {
        val old = end
        end = 0
        return old
    }

    /**
     * Write a NULL value.
     * @return The offset of the value written.
     */
    fun writeNull(): Int {
        val pos = end;
        view.setInt8(end++, TYPE_NULL.toByte())
        return pos;
    }

    /**
     * Write an undefined value.
     * @return The offset of the value written.
     */
    fun writeUndefined(): Int {
        val pos = end;
        view.setInt8(end++, TYPE_UNDEFINED.toByte())
        return pos;
    }

    /**
     * Write an boolean value.
     * @param value The boolean to write.
     * @return The offset of the value written.
     */
    fun writeBool(value: Boolean): Int {
        val pos = end;
        if (value) {
            view.setInt8(end++, TYPE_BOOL_TRUE.toByte())
        } else {
            view.setInt8(end++, TYPE_BOOL_FALSE.toByte())
        }
        return pos;
    }

    /**
     * Write an 32-bit integer.
     * @param value The integer to write.
     * @return The offset of the value written.
     */
    fun writeInt32(value: Int): Int {
        val offset = end;
        when (value) {
            in 0..15 -> {
                // encode uint4
                view.setInt8(offset, (TYPE_UINT4 or value).toByte())
                end += 1
            }

            in -16..-1 -> {
                // encode sint4
                view.setInt8(offset, (TYPE_SINT4 or (value + 16)).toByte())
                end += 1
            }

            in -128..127 -> {
                view.setInt8(offset, TYPE_INT8.toByte())
                view.setInt8(offset + 1, value.toByte())
                end += 2
            }

            in -32768..32767 -> {
                view.setInt8(offset, TYPE_INT16.toByte())
                view.setInt16(offset + 1, value.toShort())
                end += 3
            }

            else -> {
                view.setInt8(offset, TYPE_INT32.toByte())
                view.setInt32(offset + 1, value)
                end += 5
            }
        }
        return offset
    }

    /**
     * Write an 64-bit integer.
     * @param value The integer to write.
     * @return The offset of the value written.
     */
    fun writeInt64(value: BigInt64): Int {
        if (value gtei Int.MIN_VALUE && value ltei Int.MAX_VALUE) {
            return writeInt32(value.toInt())
        }
        val offset = end;
        view.setInt8(offset, TYPE_INT64.toByte())
        view.setBigInt64(offset + 1, value)
        end += 9
        return offset
    }

    /**
     * Write an 48-bit unsigned integer which represents a Unix-Epoch-Timestamp im milliseconds.
     * @param value The timestamp to write.
     * @return The offset of the value written.
     */
    fun writeTimestamp(value: BigInt64): Int {
        val offset = end;
        view.setInt8(offset, TYPE_TIMESTAMP.toByte())
        val hi = (value ushr 32).toShort()
        val lo = value.toInt()
        view.setInt16(offset + 1, hi)
        view.setInt32(offset + 3, lo)
        end += 7
        return offset
    }

    /**
     * Write a 32-bit floating point number.
     * @param value The value to write.
     * @return The offset of the value written.
     */
    fun writeFloat32(value: Float): Int {
        val pos = end
        if (value >= -8.0 && value <= 7.0 && value == floor(value)) {
            // Note: The integer value 0 represents -8!
            val i = value.toInt() + 8
            view.setInt8(end++, (TYPE_FLOAT4 or i).toByte())
            return pos
        }
        view.setInt8(end, TYPE_FLOAT32.toByte())
        view.setFloat32(end + 1, value)
        end += 5
        return pos
    }

    /**
     * Write a 64-bit floating point number.
     * @param value The value to write.
     * @return The offset of the value written.
     */
    fun writeFloat64(value: Double): Int {
        val pos = end
        if (value >= -8.0 && value <= 7.0 && value == floor(value)) {
            // Note: The integer value 0 represents -8!
            val i = value.toInt() + 8
            view.setInt8(end++, (TYPE_FLOAT4 or i).toByte())
            return pos
        }
        view.setInt8(end, TYPE_FLOAT64.toByte())
        view.setFloat64(end + 1, value)
        end += 9
        return pos
    }

    /**
     * Write a reference. Note that a negative value represents null.
     * @param index The index into the dictionary.
     * @param global If the reference is into global-dictionary (true) or local (false).
     * @return The offset of the value written.
     */
    fun writeRef(index: Int, global: Boolean): Int {
        val offset = end
        val indexBit = if (global) 0b0000_0100 else 0
        if (index < 0) {
            // null
            view.setInt8(end++, (TYPE_REFERENCE or indexBit).toByte())
            return offset
        }
        if (index < 16) {
            // tiny reference
            view.setInt8(end++, ((if (global) TYPE_TINY_GLOBAL_REF else TYPE_TINY_LOCAL_REF).toInt() or index).toByte())
            return offset
        }
        when (val value = index - 16) {
            in 0..255 -> {
                view.setInt8(end++, (TYPE_REFERENCE or indexBit or 1).toByte())
                view.setInt8(end++, value.toByte())
            }

            in 256..65535 -> {
                view.setInt8(end, (TYPE_REFERENCE or indexBit or 2).toByte())
                view.setInt16(end + 1, value.toShort())
                end += 3
            }

            in 65536..2147483647 -> {
                view.setInt8(end, (TYPE_REFERENCE or indexBit or 3).toByte())
                view.setInt32(end + 1, value)
                end += 5
            }

            else -> throw IllegalStateException()
        }
        return offset
    }

    /**
     * Add the given string to the local dictionary, if the same string is already in the dictionary, the existing will be returned.
     * @param string The string to add.
     * @return The index of the string.
     */
    fun writeToLocalDictionary(string: String): Int {
        val localDict = getLocalDictByString()
        var index = localDict[string]
        if (index != null) {
            return index
        }
        index = localNextIndex++
        localDict[string] = index
        getLocalDictByIndex().add(string)
        return index
    }

    /**
     * Encodes the given string into this binary. This method does not take references into account, it really only
     * encodes a string.
     * @param string The string to encode.
     * @return The offset of the value written.
     */
    fun writeString(string: String): Int {
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
            pos = writeUnicode(pos, unicode)
        }
        // Calculate the size of the string.
        val size = pos - start - 5
        var source = start + 5
        // If the header is smaller than 5 byte, we need copy the data backwards.
        var target = writeStringHeader(start, size)
        if (target < source) {
            while (source < pos) {
                // TODO: Optimized this by adding support for a native copy function into IDataView
                view.setInt8(target++, view.getInt8(source++))
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
    private fun writeStringRef(offset: Int, index: Int, isGlobal: Boolean, add: Int): Int {
        require(add in 0..3)
        require(index >= 0)
        var pos = offset
        var leadIn = 0b111_00_000 or (add shl 3)
        if (isGlobal) {
            leadIn = leadIn or 0b00000_100
        }
        when (index) {
            in 0..255 -> {
                view.setInt8(pos++, (leadIn or 1).toByte())
                view.setInt8(pos++, index.toByte())
            }

            in 256..65535 -> {
                view.setInt8(pos, (leadIn or 2).toByte())
                view.setInt16(pos + 1, index.toShort())
                pos += 3
            }

            else -> {
                view.setInt8(pos, (leadIn or 3).toByte())
                view.setInt32(pos + 1, index)
                pos += 5
            }
        }
        return pos
    }

    /**
     * Encodes the given string as text, that means using the local and global dictionary, if the latter is available.
     * @param string The string to encode.
     * @return The offset of the value written.
     */
    fun writeText(string: String): Int {
        val start = end
        val global = this.global
        // We reserve 5 byte for the header.
        var pos = end + 5
        var i = 0
        var wordStart = -1
        val stringReader = JbString()
        char_loop@while (i < string.length) {
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
                    pos = writeUnicode(pos, unicode)
                    // We know that the unicode is only one byte (is < 128)
                    size++
                }
                if (size >= 3) {
                    // Map without header.
                    stringReader.map(view, wordStart, wordStart, pos, null, null)
                    val subString = stringReader.toString()
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
                                val c = view.getInt8(reversePos).toInt() and 0xff
                                if (':'.code == c) {
                                    // We found a colon at reversePos
                                    // Try to look it up in the global dictionary (reversePos is excluded below).
                                    stringReader.map(view, wordStart, wordStart, reversePos, null, null)
                                    val prefix = stringReader.toString()
                                    index = global.indexOf(prefix)
                                    if (index >= 0) {
                                        // Found the prefix in the global dict, now we encode the prefix as reference.
                                        pos = writeStringRef(wordStart, index, true, ADD_COLON)
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
                        index = writeToLocalDictionary(subString)
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
                    pos = writeStringRef(wordStart, index, isGlobal, add)
                    wordStart = -1
                    // If we're currently on a space or underscore, we do not need to encode it, it is embedded.
                    // If we hit the end of the string, we have added the code already.
                    if (add > 0 || i == string.length) continue
                }
                // Word ends now.
                wordStart = -1
            }
            pos = writeUnicode(pos, unicode)
        }
        // Calculate the size of the string.
        val size = pos - start - 5
        var source = start + 5
        // If the header is smaller than 5 byte, we need copy the data backwards.
        var target = writeTextHeader(start, size)
        if (target < source) {
            while (source < pos) {
                // TODO: Optimized this by adding support for a native copy function into IDataView
                view.setInt8(target++, view.getInt8(source++))
            }
            pos = target
        }
        end = pos
        return start
    }

    private fun sizeOfUnicode(unicode: Int): Int {
        return when (unicode) {
            in 0..127 -> 1
            in 128..16511 -> 2
            else -> 3
        }
    }

    private fun writeUnicode(offset: Int, unicode: Int): Int {
        require(unicode in 0..2_097_151)
        require(offset >= 0 && offset <= (view.getSize() - sizeOfUnicode(unicode)))
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

    private fun writeStringHeader(offset: Int, size: Int): Int {
        var pos = offset
        when (size) {
            in 0..12 -> {
                view.setInt8(pos++, (TYPE_STRING or size).toByte())
            }

            in 13..255 -> {
                view.setInt8(pos++, (TYPE_STRING or 13).toByte())
                view.setInt8(pos++, size.toByte())
            }

            in 256..65535 -> {
                view.setInt8(pos++, (TYPE_STRING or 14).toByte())
                view.setInt16(pos, size.toShort())
                pos += 2
            }

            else -> {
                view.setInt8(pos++, (TYPE_STRING or 15).toByte())
                view.setInt32(pos, size)
                pos += 5
            }
        }
        return pos
    }

    private fun writeTextHeader(offset: Int, size: Int): Int {
        var pos = offset
        when (size) {
            0 -> {
                view.setInt8(pos++, (TYPE_CONTAINER or 0b1100).toByte())
            }

            in 1..255 -> {
                view.setInt8(pos++, (TYPE_CONTAINER or 0b1101).toByte())
                view.setInt8(pos++, size.toByte())
            }

            in 256..65535 -> {
                view.setInt8(pos++, (TYPE_CONTAINER or 0b1110).toByte())
                view.setInt16(pos, size.toShort())
                pos += 2
            }

            else -> {
                view.setInt8(pos++, (TYPE_CONTAINER or 0b1111).toByte())
                view.setInt32(pos, size)
                pos += 5
            }
        }
        return pos
    }

    /**
     * Starts an array and returns the offset where the array was started, needed to close the array.
     * The content of the array can be written simply after having started the array.
     * @return The offset where the array was started.
     */
    fun startArray(): Int {
        val start = end
        // We need 1-byte lead-in and a maximum of 4 byte for the size of the array.
        // We now write the lead-in for an empty array.
        view.setInt8(end++, (TYPE_CONTAINER or TYPE_CONTAINER_ARRAY).toByte())
        end += 4
        return start
    }

    /**
     * Ends an array.
     * @param start The start of the array as returned by [startArray].
     * @return The start of the array again.
     */
    fun endArray(start: Int): Int {
        val size = end - start - 5
        require(size >= 0)
        if (size == 0) {
            // Empty array, we already created the header, just fix end and we're done.
            end -= 4
            return start
        }
        val contentStart = start + 5
        val contentEnd = end
        var end = start
        when (size) {
            in 0..255 -> {
                view.setInt8(end++, (TYPE_CONTAINER or TYPE_CONTAINER_ARRAY or 1).toByte())
                view.setInt8(end++, size.toByte())
            }

            in 256..65536 -> {
                view.setInt8(end, (TYPE_CONTAINER or TYPE_CONTAINER_ARRAY or 2).toByte())
                view.setInt16(end + 1, size.toShort())
                end += 3
            }

            else -> {
                view.setInt8(end, (TYPE_CONTAINER or TYPE_CONTAINER_ARRAY or 3).toByte())
                view.setInt32(end + 1, size)
                end += 5
            }
        }
        // Copy from where the content backwards
        var source = contentStart
        if (end < source) {
            while (source < contentEnd) {
                view.setInt8(end++, view.getInt8(source++))
            }
        }
        this.end = end
        return start
    }

    /**
     * Starts a map and returns the offset where the map was started, needed to close the map.
     * The content of the map can be written simply after having started the map.
     * @return The offset where the map was started.
     */
    fun startMap(): Int {
        val start = end
        // We need 1-byte lead-in and a maximum of 4 byte for the size of the array.
        // We now write the lead-in for an empty array.
        view.setInt8(end++, (TYPE_CONTAINER or TYPE_CONTAINER_MAP).toByte())
        end += 4
        return start
    }

    /**
     * Writes the given key into a map, requires that [startMap] has been called before.
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
                writeRef(index, true)
                return start
            }
        }
        index = writeToLocalDictionary(key)
        writeRef(index, false)
        return start
    }

    /**
     * Ends a map.
     * @param start The start of the map as returned by [startMap].
     * @return The start of the map again.
     */
    fun endMap(start: Int): Int {
        val size = end - start - 5
        require(size >= 0)
        if (size == 0) {
            // Empty map, we already created the header, just fix end and we're done.
            end -= 4
            return start
        }
        val contentStart = start + 5
        val contentEnd = end
        var end = start
        when (size) {
            in 0..255 -> {
                view.setInt8(end++, (TYPE_CONTAINER or TYPE_CONTAINER_MAP or 1).toByte())
                view.setInt8(end++, size.toByte())
            }

            in 256..65536 -> {
                view.setInt8(end, (TYPE_CONTAINER or TYPE_CONTAINER_MAP or 2).toByte())
                view.setInt16(end + 1, size.toShort())
                end += 3
            }

            else -> {
                view.setInt8(end, (TYPE_CONTAINER or TYPE_CONTAINER_MAP or 3).toByte())
                view.setInt32(end + 1, size)
                end += 5
            }
        }
        // Copy from where the content backwards
        var source = contentStart
        if (end < source) {
            while (source < contentEnd) {
                view.setInt8(end++, view.getInt8(source++))
            }
        }
        this.end = end
        return start
    }

    /**
     * Creates a global dictionary out of this builder. Checks that nothing was yet written into the binary.
     * @param id The unique identifier of the dictionary.
     * @return The dictionary.
     */
    fun buildDictionary(id: String): ByteArray {
        check(end == 0)
        check(localDictByIndex != null)
        val localStringById = this.localDictByIndex!!

        // We add the lead-in with the size later, we anyway need to make a copy.
        // First, encode the unique identifier.
        writeString(id)
        for (string in localStringById) {
            writeString(string)
        }
        // Now end is the size, lets encode the size behind.
        val size = this.end
        writeInt32(size)
        // Now, end + 1 (lead-in) byte is what we need.
        val targetArray = ByteArray(end + 1)
        val targetView = JbSession.get().newDataView(targetArray)
        var target = 0
        targetView.setInt8(target++, TYPE_GLOBAL_DICTIONARY.toByte())
        // Copy size
        var source = size
        val end = this.end
        while (source < end) {
            targetView.setInt8(target++, view.getInt8(source++))
        }
        // Copy the rest (id and entities)
        source = 0
        while (source < size) {
            targetView.setInt8(target++, view.getInt8(source++))
        }
        return targetArray
    }

    /**
     * Expects a GeoJSON feature as input and convert it into JBON. The first being the JBON feature,
     * the second being the XYZ namespace, the third being the geometry.
     * @param map The GeoJSON feature to convert into JBON.
     * @return The JBON representation of the feature, the XYZ-namespace and the geometry.
     */
    fun buildFeatureFromMap(map: IMap): ByteArray {
        val raw = map["id"]
        val id: String? = if (raw is String) raw else null
        xyz = null
        val start = startMap()
        for (entry in map) {
            val key = entry.key
            val value = entry.value
            // TODO: Remember geometry to encode it later
            if ("id" == key || "geometry" == key) continue
            writeKey(entry.key)
            if ("properties" == key) {
                check(value is IMap)
                writeMap(value, true)
            } else {
                writeValue(value)
            }
        }
        endMap(start)
        return buildFeature(id)
    }

    /**
     * When invoking [buildFeatureFromMap] this is used to capture the XYZ namespace reference, if any is found.
     */
    var xyz: IMap? = null

    /**
     * Writes a map recursively.
     * @param map The map to write.
     * @param ignoreXyzNs If the key _@ns:com:here:xyz_ should be ignored (a reference is added to [xyz]).
     * @return The offset of the value written.
     */
    fun writeMap(map: IMap, ignoreXyzNs: Boolean = false): Int {
        val start = startMap()
        for (entry in map) {
            val key = entry.key
            val value = entry.value
            if (ignoreXyzNs && ("@ns:com:here:xyz" == key)) {
                if (value is IMap) xyz = value
                continue
            }
            writeKey(entry.key)
            writeValue(entry.value)
        }
        endMap(start)
        return start
    }

    /**
     * Writes an array recursively.
     * @param array The array to write.
     * @return The offset of the value written.
     */
    fun writeArray(array: Array<Any?>): Int {
        val start = startArray()
        for (value in array) writeValue(value)
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
    fun writeValue(value: Any?): Int {
        val start = end
        when (value) {
            is Char -> writeString(value.toString())
            is String -> writeString(value)
            is Boolean -> writeBool(value)
            is Byte -> writeInt32(value.toInt())
            is Short -> writeInt32(value.toInt())
            is Int -> writeInt32(value)
            is Long -> writeInt64(JbSession.int64.longToBigInt64(value))
            is BigInt64 -> writeInt64(value)
            is Float -> writeFloat32(value)
            is Double -> if (JbSession.env.canBeFloat32(value)) writeFloat32(value.toFloat()) else writeFloat64(value)
            is IMap -> writeMap(value)
            is Array<*> -> writeArray(value as Array<Any?>)
            null -> writeNull()
            else -> {
                throw IllegalArgumentException()
            }
        }
        return start
    }

    /**
     * Creates a feature out of this builder and the current local dictionary.
     * @param id The unique identifier of the feature, may be null.
     * @return The feature.
     */
    fun buildFeature(id: String?): ByteArray {
        check(end > 0)
        val contentEnd = end
        val startOfLocalDictContent = end
        val localDictByIndex = this.localDictByIndex
        if (localDictByIndex != null) {
            for (string in localDictByIndex) {
                writeString(string)
            }
        }
        val endOfLocalDictContent = end
        val startOfLocalDictSize = end
        writeInt32(endOfLocalDictContent - startOfLocalDictContent)
        val endOfLocalDictSize = end
        val startOfFeatureId = end
        // Write the id, we copy that into the target soon.
        if (id != null) {
            if (global != null) writeText(id) else writeString(id)
        } else {
            writeNull()
        }
        val endOfFeatureId = end
        val startOfGlobalDictId = end
        // Write the id of the global dictionary.
        val featureId: String? = global?.id()
        if (featureId != null) {
            writeString(featureId)
        } else {
            writeNull()
        }
        val endOfGlobalDictId = end
        val startOfTotalSize = end
        // Write the size of the feature.
        val totalSize = end
        writeInt32(totalSize)
        val endOfTotalSize = end

        // Now, end + 1 byte lead-in feature, + 1 byte lead-in of local dict.
        val targetArray = ByteArray(end + 2)
        val targetView = JbSession.get().newDataView(targetArray)
        var target = 0
        // Write lead-in.
        targetView.setInt8(target++, TYPE_FEATURE.toByte())
        // Copy size.
        var source = startOfTotalSize
        while (source < endOfTotalSize) {
            targetView.setInt8(target++, view.getInt8(source++))
        }
        // Copy the global dictionary id.
        source = startOfGlobalDictId
        while (source < endOfGlobalDictId) {
            targetView.setInt8(target++, view.getInt8(source++))
        }
        // Copy the feature id.
        source = startOfFeatureId
        while (source < endOfFeatureId) {
            targetView.setInt8(target++, view.getInt8(source++))
        }
        // Write lead-in local dict.
        targetView.setInt8(target++, TYPE_LOCAL_DICTIONARY.toByte())
        // Copy local dict size.
        source = startOfLocalDictSize
        while (source < endOfLocalDictSize) {
            targetView.setInt8(target++, view.getInt8(source++))
        }
        // Copy the local dict content.
        source = startOfLocalDictContent
        while (source < endOfLocalDictContent) {
            targetView.setInt8(target++, view.getInt8(source++))
        }
        // Copy the feature content.
        source = 0
        while (source < contentEnd) {
            targetView.setInt8(target++, view.getInt8(source++))
        }
        return targetArray
    }
}