@file:OptIn(ExperimentalJsExport::class)

package com.here.naksha.lib.jbon

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@JsExport
class Jbon(val view: IDataView, val dictionary: JbonDict? = null) {
    /**
     * The current position in the view, starts with zero.
     */
    var pos = 0

    /**
     * Moves the position to the given absolute index.
     * @param index The index to move the [pos] to, must be between 0 and view size (exclusive).
     * @return this.
     */
    fun seekTo(index: Int): Jbon {
        require(index > 0 && index < view.getSize())
        this.pos = index
        return this
    }

    /**
     * Moves the position relatively to the current position.
     * @param amount The amount of byte to move [pos].
     * @return this.
     */
    fun seekBy(amount: Int): Jbon {
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
            TYPE_NULL, TYPE_UNDEFINED, TYPE_BOOL_TRUE, TYPE_BOOL_FALSE, TYPE_INT4, TYPE_FLOAT4 -> {
                return 1
            }

            TYPE_INT8 -> 2
            TYPE_INT16 -> 3
            TYPE_INT32 -> 5
            TYPE_FLOAT32 -> 5
            TYPE_FLOAT64 -> 9
            TYPE_SIZE32 -> {
                when (raw and 0xf) {
                    in 0..11 -> 1
                    12 -> 2
                    13 -> 3
                    14 -> 4
                    else -> 5 // 15
                }
            }

            TYPE_STRING -> {
                when (val size = raw and 0xf) {
                    in 0..10 -> 1 + size
                    11 -> 2 + view.getInt8(pos + 1).toInt() and 0xff
                    12 -> 3 + view.getInt16(pos + 1).toInt() and 0xffff
                    // TODO: 13 -> Support 24-bit encoding
                    14 -> 5 + view.getInt32(pos + 1).toInt()
                    else -> 0
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

    fun isInt(): Boolean {
        val type = type()
        return type == TYPE_INT4 || type == TYPE_INT8 || type == TYPE_INT16
                //|| type == TYPE_INT24
                || type == TYPE_INT32
        //|| type == TYPE_INT40
        //|| type == TYPE_INT48
        //|| type == TYPE_INT56
        //|| type == TYPE_INT64
    }

    fun isSize32(): Boolean {
        return type() == TYPE_SIZE32
    }

    fun getSize32(alternative: Int = -1): Int {
        checkPos()
        val raw = view.getInt8(pos).toInt() and 0xff
        val type = if (raw and 128 == 128) raw and 0xf0 else raw
        if (type == TYPE_SIZE32) {
            return when (val value = raw and 0xf) {
                in 0..11 -> value
                12 -> view.getInt8(pos + 1).toInt() and 0xff
                13 -> view.getInt16(pos + 1).toInt() and 0xffff
                // 14 -> 24-bit encoding not yet supported
                15 -> view.getInt32(pos + 1)
                else -> alternative
            }
        }
        return when (type) {
            TYPE_INT4, TYPE_INT8, TYPE_INT16, TYPE_INT32 -> getInt(alternative)
            else -> alternative
        }
    }

    fun getInt(alternative: Int = -1): Int {
        val type = type()
        return when (type) {
            TYPE_INT4 -> (view.getInt8(pos).toInt() and 0x0f) - 8
            TYPE_INT8 -> view.getInt8(pos + 1).toInt()
            TYPE_INT16 -> view.getInt16(pos + 1).toInt()
            TYPE_INT32 -> view.getInt32(pos + 1)
            TYPE_SIZE32 -> getSize32(alternative)
            else -> alternative
        }
    }

    fun isFloat(): Boolean {
        val type = type()
        return type == TYPE_FLOAT32 || type == TYPE_FLOAT4
    }

    fun isDouble(): Boolean {
        val type = type()
        return type == TYPE_FLOAT64 || type == TYPE_FLOAT4
    }

    fun isNumber(): Boolean {
        return isInt() || isFloat() || isDouble()
    }

    /**
     * Read a float from the current position. Fails and returns the alternative, when the value is not convertible into a float.
     *
     * @param alternative The value to return, when the value can't be read as a float.
     * @param readStrict If set to true, the method does not lose precision, rather returns the alternative.
     */
    fun getFloat(alternative: Float = Float.NaN, readStrict: Boolean = false): Float {
        return when (type()) {
            TYPE_INT4, TYPE_INT8, TYPE_INT16 -> {
                getInt().toFloat()
            }

            TYPE_INT32 -> {
                if (readStrict) return alternative
                getInt().toFloat()
            }

            TYPE_SIZE32 -> {
                if (readStrict) return alternative
                getSize32().toFloat()
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
    @Suppress("UNUSED_PARAMETER")
    fun getDouble(alternative: Double = Double.NaN, readStrict: Boolean = false): Double {
        return when (type()) {
            TYPE_INT4, TYPE_INT8, TYPE_INT16, TYPE_INT32 -> {
                getInt(0).toDouble()
            }

            TYPE_SIZE32 -> {
                getSize32().toDouble()
            }

            TYPE_FLOAT4 -> {
                val index = view.getInt8(pos).toInt() and 0xf
                TINY_DOUBLES[index]
            }

            TYPE_FLOAT32 -> {
                getFloat().toDouble()
            }

            TYPE_FLOAT64 -> {
                view.getFloat64(pos + 1)
            }

            else -> alternative
        }
    }

    fun isString() : Boolean {
        return type() == TYPE_STRING
    }
}