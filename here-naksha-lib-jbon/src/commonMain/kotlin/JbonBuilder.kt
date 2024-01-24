@file:OptIn(ExperimentalJsExport::class)

package com.here.naksha.lib.jbon;

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@JsExport
class JbonBuilder(val view: IDataView, val dictionary: JbonDict? = null) {
    private val localStrings: HashMap<String, Int> = HashMap()

    /**
     * The current end in the view.
     */
    var end: Int = 0

    fun clear(): Int {
        val old = end
        end = 0
        localStrings.clear()
        return old
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
        writeSize32(view.getSize()-1)
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
            view.setInt8(pos, (TYPE_INT4 xor (value + 8)).toByte())
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
                view.setInt8(end++, (TYPE_FLOAT4 xor i).toByte())
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
                view.setInt8(end++, (TYPE_FLOAT4 xor i).toByte())
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
        view.setInt8(end++, (TYPE_SIZE32 xor value).toByte())
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
        view.setInt8(end++, (TYPE_SIZE32 xor 12).toByte())
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
        view.setInt8(end, (TYPE_SIZE32 xor 13).toByte())
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
        view.setInt8(end, (TYPE_SIZE32 xor 15).toByte())
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
}