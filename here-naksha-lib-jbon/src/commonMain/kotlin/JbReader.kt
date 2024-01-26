@file:OptIn(ExperimentalJsExport::class)

package com.here.naksha.lib.jbon

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * The JBON reader that can be used with any data view.
 */
@JsExport
class JbReader(val view: IDataView, val dictionary: JbDict? = null) {
    /**
     * The current position in the view, starts with zero.
     */
    var pos = 0

    /**
     * Moves the position to the given absolute index.
     * @param index The index to move the [pos] to, must be between 0 and view size (exclusive).
     * @return this.
     */
    fun seekTo(index: Int): JbReader {
        require(index > 0 && index < view.getSize())
        this.pos = index
        return this
    }

    /**
     * Moves the position relatively to the current position.
     * @param amount The amount of byte to move [pos].
     * @return this.
     */
    fun seekBy(amount: Int): JbReader {
        val newPos = pos + amount
        this.pos = newPos
        return this
    }

    /**
     * Tests if the current position is a valid position in the JBON.
     * @return true if the position is valid; false if out of bounds.
     */
    fun isValid(): Boolean {
        return pos >= 0 && pos < view.getSize()
    }

    private fun checkPos() {
        check(pos >= 0 && pos < view.getSize())
    }

    /**
     * Reads the type from the current position.
     * @return The type stored at the current position.
     */
    fun type(): Int {
        checkPos()
        var type = view.getInt8(pos).toInt() and 0xff
        if (type and 128 == 128) {
            type = type and 0xf0
        }
        return type
    }

    /**
     * Returns the size of the current value in byte including the lead-in byte, therefore the amount of byte to [seekBy], if
     * wanting to skip the value to the next one. The method return 0, when the position is invalid.
     * @return The size of the value in bytes including the lead-in (so between 1 and n).
     */
    fun size(): Int {
        if (pos < 0 || pos >= view.getSize()) return 0
        val raw = view.getInt8(pos).toInt() and 0xff
        val type = if (raw and 128 == 128) raw and 0xf0 else raw
        return when (type) {
            TYPE_NULL,
            TYPE_UNDEFINED,
            TYPE_BOOL_TRUE,
            TYPE_BOOL_FALSE,
            TYPE_UINT4,
            TYPE_SINT4,
            TYPE_TINY_LOCAL_REF,
            TYPE_TINY_GLOBAL_REF,
            TYPE_FLOAT4 -> {
                return 1
            }

            TYPE_INT8 -> 2
            TYPE_INT16 -> 3
            TYPE_INT32 -> 5
            TYPE_INT64 -> 9
            TYPE_FLOAT32 -> 5
            TYPE_FLOAT64 -> 9

            TYPE_REFERENCE -> {
                when (raw and 3) {
                    0 -> 1
                    1 -> 2 // 8-bit index
                    2 -> 3 // 16-bit index
                    3 -> 5 // 32-bit index
                    else -> throw IllegalStateException()
                }
            }

            TYPE_STRING -> {
                when (val size = raw and 0xf) {
                    in 0..12 -> 1 + size
                    13 -> 2 + view.getInt8(pos + 1).toInt() and 0xff
                    14 -> 3 + view.getInt16(pos + 1).toInt() and 0xffff
                    15 -> 5 + view.getInt32(pos + 1)
                    else -> throw IllegalStateException()
                }
            }

            else -> 0
        }
    }

    /**
     * Tests whether the type at the current position is a boolean.
     * @return true if the type at the current position is a boolean; false otherwise.
     */
    fun isBool(): Boolean {
        val type = type()
        return type == TYPE_BOOL_TRUE || type == TYPE_BOOL_FALSE
    }

    fun isNull(): Boolean {
        return type() == TYPE_NULL
    }

    fun isUndefined(): Boolean {
        return type() == TYPE_UNDEFINED
    }

    fun getBoolean(): Boolean? {
        val type = type()
        return when (type) {
            TYPE_BOOL_TRUE -> true
            TYPE_BOOL_FALSE -> false
            else -> null
        }
    }

    /**
     * Tests whether the value is any integer value.
     * @return true if the value is an integer; false otherwise.
     */
    fun isInt(): Boolean {
        val type = type()
        return type == TYPE_UINT4
                || type == TYPE_SINT4
                || type == TYPE_INT8
                || type == TYPE_INT16
                || type == TYPE_INT32
                || type == TYPE_INT64
    }

    /**
     * Tests whether the value is a 32-bit integer value, this excludes 64-bit integers.
     * @return true if the value is an integer; false otherwise.
     */
    fun isInt32(): Boolean {
        val type = type()
        return type == TYPE_UINT4
                || type == TYPE_SINT4
                || type == TYPE_INT8
                || type == TYPE_INT16
                || type == TYPE_INT32
    }

    fun getInt32(alternative: Int = -1): Int {
        val type = type()
        return when (type) {
            TYPE_UINT4 -> (view.getInt8(pos).toInt() and 0x0f)
            TYPE_SINT4 -> (view.getInt8(pos).toInt() and 0x0f) - 16
            TYPE_INT8 -> view.getInt8(pos + 1).toInt()
            TYPE_INT16 -> view.getInt16(pos + 1).toInt()
            TYPE_INT32 -> view.getInt32(pos + 1)
            else -> alternative
        }
    }

    @Suppress("NON_EXPORTABLE_TYPE")
    fun getInt64(alternative: Long = -1): Long {
        val type = type()
        return when (type) {
            TYPE_UINT4, TYPE_SINT4, TYPE_INT8, TYPE_INT16, TYPE_INT32 -> getInt32().toLong()
            TYPE_INT64 -> {
                val hi = view.getInt32(pos + 1)
                val lo = view.getInt32(pos + 5)
                ((hi.toLong() and 0xffff_ffff) shl 32) or (lo.toLong() and 0xffff_ffff)
            }

            else -> alternative
        }
    }

    fun isFloat32(): Boolean {
        val type = type()
        return type == TYPE_FLOAT32 || type == TYPE_FLOAT4
    }

    fun isFloat64(): Boolean {
        val type = type()
        return type == TYPE_FLOAT64 || type == TYPE_FLOAT4
    }

    fun isNumber(): Boolean {
        return isInt() || isFloat32() || isFloat64()
    }

    /**
     * Read a float from the current position. Fails and returns the alternative, when the value is not convertible into a float.
     *
     * @param alternative The value to return, when the value can't be read as a float.
     * @param readStrict If set to true, the method does not lose precision, rather returns the alternative.
     */
    fun getFloat32(alternative: Float = Float.NaN, readStrict: Boolean = false): Float {
        return when (type()) {
            TYPE_UINT4, TYPE_SINT4, TYPE_INT8, TYPE_INT16 -> {
                getInt32().toFloat()
            }

            TYPE_INT32 -> {
                if (readStrict) return alternative
                getInt32().toFloat()
            }

            TYPE_INT64 -> {
                if (readStrict) return alternative
                getInt64().toFloat()
            }

            TYPE_FLOAT4 -> {
                val index = view.getInt8(pos).toInt() and 0xf
                TINY_FLOATS[index]
            }

            TYPE_FLOAT32 -> {
                view.getFloat32(pos + 1)
            }

            TYPE_FLOAT64 -> {
                if (readStrict) return alternative
                getDouble().toFloat()
            }

            else -> alternative
        }
    }

    /**
     * Read a double from the current position. Fails and returns the alternative, when the value is not convertible into a double.
     *
     * @param alternative The value to return, when the value can't be read as a double.
     * @param readStrict If set to true, the method does not lose precision, rather returns the alternative.
     */
    fun getDouble(alternative: Double = Double.NaN, readStrict: Boolean = false): Double {
        return when (type()) {
            TYPE_UINT4, TYPE_SINT4, TYPE_INT8, TYPE_INT16, TYPE_INT32 -> {
                getInt32().toDouble()
            }

            TYPE_INT64 -> {
                if (readStrict) return alternative
                getInt64().toDouble()
            }

            TYPE_FLOAT4 -> {
                val index = view.getInt8(pos).toInt() and 0xf
                TINY_DOUBLES[index]
            }

            TYPE_FLOAT32 -> {
                getFloat32().toDouble()
            }

            TYPE_FLOAT64 -> {
                view.getFloat64(pos + 1)
            }

            else -> alternative
        }
    }

    fun isString(): Boolean {
        return type() == TYPE_STRING
    }

    private var stringReader: JbString? = null

    /**
     * Return a string-reader for the current string, requires that the current position is at a string.
     * @param reader The reader to use, if null, an internal cached reader is used and returned.
     * @return The given reader mapped to this string or the internal cached reader mapped to this string.
     */
    fun getString(reader: JbString? = null): JbString {
        if (reader != null) {
            reader.map(this)
            return reader
        }
        if (stringReader == null) {
            stringReader = JbString(this)
        } else {
            stringReader!!.map(this)
        }
        return stringReader!!
    }

    fun isRef(): Boolean {
        val type = type()
        return type == TYPE_REFERENCE || type == TYPE_TINY_GLOBAL_REF || type == TYPE_TINY_LOCAL_REF
    }

    fun isLocalRef(): Boolean {
        checkPos()
        val raw = view.getInt8(pos).toInt() and 0xff
        val type = if (raw and 128 == 128) raw and 0xf0 else raw
        if (type == TYPE_REFERENCE) {
            return raw and 0b0000_0100 == 0
        }
        return type == TYPE_TINY_LOCAL_REF
    }

    fun isGlobalRef(): Boolean {
        checkPos()
        val raw = view.getInt8(pos).toInt() and 0xff
        val type = if (raw and 128 == 128) raw and 0xf0 else raw
        if (type == TYPE_REFERENCE) {
            return raw and 0b0000_0100 != 0
        }
        return type == TYPE_TINY_GLOBAL_REF
    }

    fun getRef(): Int {
        val raw = view.getInt8(pos).toInt() and 0xff
        val type = if (raw and 128 == 128) raw and 0xf0 else raw
        val value = raw and 0xf
        return when (type) {
            TYPE_TINY_LOCAL_REF, TYPE_TINY_GLOBAL_REF -> value
            TYPE_REFERENCE -> {
                return when (value and 3) {
                    0 -> -1
                    1 -> (view.getInt8(pos + 1).toInt() and 0xff) + 16
                    2 -> (view.getInt16(pos + 1).toInt() and 0xffff) + 16
                    3 -> view.getInt32(pos + 1) + 16
                    else -> throw IllegalStateException()
                }
            }
            else -> -1
        }
    }
}