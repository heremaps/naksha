@file:OptIn(ExperimentalJsExport::class)

package com.here.naksha.lib.jbon;

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@JsExport
class JbonBuilder(val view: IDataView, val dictionary: JbonDict? = null) {
    private var localStrings: HashMap<String, Int>? = null

    /**
     * The current end in the view.
     */
    var end: Int = 0

    fun clear(): Int {
        val old = end
        end = 0
        localStrings?.clear()
        return old
    }

    fun writeNull(): Int {
        val pos = end;
        view.setInt8(end++, TYPE_NULL.toByte())
        return pos;
    }

    fun writeUndefined(): Int {
        val pos = end;
        view.setInt8(end++, TYPE_UNDEFINED.toByte())
        return pos;
    }

    fun writeBool(value: Boolean): Int {
        val pos = end;
        if (value) {
            view.setInt8(end++, TYPE_BOOL_TRUE.toByte())
        } else {
            view.setInt8(end++, TYPE_BOOL_FALSE.toByte())
        }
        return pos;
    }

    fun writeInt(value: Int): Int {
        val pos = end;
        if (value >= -8 && value <= 7) {
            // encode int4
            view.setInt8(pos, (TYPE_INT4 or (value + 8)).toByte())
            end += 1
        } else if (value >= -128 && value <= 127) {
            view.setInt8(pos, TYPE_INT8.toByte())
            view.setInt8(pos + 1, value.toByte())
            end += 2
        } else if (value >= -32768 && value <= 32767) {
            view.setInt8(pos, TYPE_INT16.toByte())
            view.setInt16(pos + 1, value.toShort())
            end += 3
        } else {
            view.setInt8(pos, TYPE_INT32.toByte())
            view.setInt32(pos + 1, value)
            end += 5
        }
        return pos
    }

    fun writeFloat(value: Float): Int {
        val pos = end
        for (i in 0..15) {
            val tiny = TINY_FLOATS[i]
            if (tiny == value) {
                view.setInt8(end++, (TYPE_FLOAT4 or i).toByte())
                return pos
            }
        }
        view.setInt8(end, TYPE_FLOAT32.toByte())
        view.setFloat32(end + 1, value)
        end += 5
        return pos
    }

    fun writeDouble(value: Double): Int {
        val pos = end
        for (i in 0..15) {
            val tiny = TINY_DOUBLES[i]
            if (tiny == value) {
                view.setInt8(end++, (TYPE_FLOAT4 or i).toByte())
                return pos
            }
        }
        view.setInt8(end, TYPE_FLOAT64.toByte())
        view.setFloat64(end + 1, value)
        end += 9
        return pos
    }

    /**
     * Writes an 8-bit encoded size.
     * @param value The size to write, between 0 and 11 (inclusive).
     * @return The index where the value was written.
     */
    fun writeSize32_8(value: Int): Int {
        require(value in 0..11)
        val pos = end
        view.setInt8(end++, (TYPE_SIZE32 or value).toByte())
        return pos
    }

    /**
     * Writes a 16-bit encoded size.
     * @param value The value to write, between 12 and 267 (inclusive)
     * @return The index where the value was written.
     */
    fun writeSize32_16(value: Int): Int {
        require(value in 0..255)
        val pos = end
        view.setInt8(end++, (TYPE_SIZE32 or 12).toByte())
        view.setInt8(end++, (value and 0xff).toByte())
        return pos
    }

    /**
     * Writes a 24-bit encoded size.
     * @param value The value to write, between 0 and 65535 (inclusive)
     * @return The index where the value was written.
     */
    fun writeSize32_24(value: Int): Int {
        require(value in 0..65535)
        val pos = end
        view.setInt8(end, (TYPE_SIZE32 or 13).toByte())
        view.setInt16(end + 1, (value and 0xffff).toShort())
        end += 3
        return pos
    }

    /**
     * Writes a 40-bit encoded size.
     * @param value The value to write, between 0 and [Int.MAX_VALUE] (inclusive)
     * @return The index where the value was written.
     */
    fun writeSize32_40(value: Int): Int {
        require(value >= 0)
        val pos = end
        view.setInt8(end, (TYPE_SIZE32 or 15).toByte())
        view.setInt32(end + 1, value)
        end += 5
        return pos
    }

    /**
     * Writes a compacted size. This method does use the smallest possible encoding.
     * @param value The size to write, must be a value between 0 and [Int.MAX_VALUE].
     * @return The index where the value was written.
     */
    fun writeSize32(value: Int): Int {
        require(value >= 0)
        return when (value) {
            in 0..11 -> writeSize32_8(value)
            in 12..255 -> writeSize32_16(value)
            in 256..65535 -> writeSize32_24(value)
            else -> writeSize32_40(value)
        }
    }

    /**
     * Encodes the given string into the JBON.
     */
    fun writeString(string: String): Int {
        if (localStrings == null) {
            localStrings = HashMap()
        }
        val start = end
        // We reserve 5 byte for the lead-in, so we encode a string at max length.
        // Later we will copy around smaller strings, so compacting them, but we do not want to copy big strings!
        var pos = end + 5
        var i = 0
        // We use a mark to match substrings for compression
        var mark = pos
        var markIndex = i
        while (i < string.length) {
            var unicode: Int
            val hi = string[i++]
            if (i < string.length && hi.isHighSurrogate()) {
                val lo = string[i++]
                require(lo.isLowSurrogate())
                unicode = CodePoints.toCodePoint(hi, lo)
            } else {
                unicode = hi.code
            }
            check(unicode in 0..2_097_151)
            // When we hit a space, compression kicks in
            if (unicode == ' '.code) {
                // TODO: Implement local dictionary lookup
                // TODO: Implement global dictionary lookup
            }
            when (unicode) {
                in 0..127 -> view.setInt8(pos++, unicode.toByte())
                in 128..16511 -> { // 0 -> 2^14-1 biased by 128
                    // BIAS the unicode value
                    unicode -= 128
                    // Encode the higher 6 bit
                    view.setInt8(pos++, ((unicode ushr 8) or 0b10_000000).toByte())
                    // Encode the lower 8 bit
                    view.setInt8(pos++, (unicode and 0xff).toByte())
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
        }
        // Calculate the bytes we encoded (5 bytes where reserved for the lead-in)
        val size = pos - start - 5
        // Truncate the lead-in and copy the data backwards.
        // TODO: Optimize by using words (32-bit) instead of copy bytes, add support to IDataView for this copy op,
        //       because in the JVM we can use the low level array-copy method of System, maybe there is something
        //       like this as well in NodeJS and/or the Browser?
        var source = start + 5
        var target = start
        when (size) {
            in 0..10 -> {
                view.setInt8(target++, (TYPE_STRING or size).toByte())
                check(target + 4 == source)
            }

            in 11..255 -> {
                view.setInt8(target++, (TYPE_STRING or 11).toByte())
                view.setInt8(target++, size.toByte())
                check(target + 3 == source)
            }

            in 256..65535 -> {
                view.setInt8(target++, (TYPE_STRING or 12).toByte())
                view.setInt16(target, size.toShort())
                target += 2
                check(target + 2 == source)
            }

            // TODO: Implement 13, which encodes size as 24-bit value, so 32-bit total

            else -> {
                view.setInt8(target++, (TYPE_STRING or 14).toByte())
                view.setInt32(target, size)
                target += 5
                check(target == source)
            }
        }
        if (target < source) {
            while (source < pos) {
                view.setInt8(target++, view.getInt8(source++))
            }
            pos = target
        }
        end = pos
        return start
    }

    /**
     * Starts a document, can only be called for a blank builder, so when [end] is null.
     */
    fun startDocument() {
        // TODO: Finish this
        check(end == 0)
        // Lead-In
        // size of document in bytes
        // reference to root
        view.setInt8(end++, TYPE_DOCUMENT.toByte())
        // We write the maximal value and then, when closing the document, fix the value
        // This may waste a few bytes, if the document is smaller than expected.
        writeSize32(view.getSize() - 1)
    }

}