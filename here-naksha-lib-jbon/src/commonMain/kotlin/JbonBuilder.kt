@file:OptIn(ExperimentalJsExport::class)

package com.here.naksha.lib.jbon;

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

const val TYPE_INT4 = 0b1000_0000
const val TYPE_FLOAT4 = 0b1001_0000
const val TYPE_SIZE32 = 0b1010_0000
const val TYPE_NULL = 0
const val TYPE_UNDEFINED = 1
const val TYPE_BOOL_TRUE = 2
const val TYPE_BOOL_FALSE = 3
const val TYPE_FLOAT32 = 4
const val TYPE_FLOAT64 = 5
const val TYPE_INT8 = 8
const val TYPE_INT16 = 9
const val TYPE_INT24 = 10
const val TYPE_INT32 = 11
const val TYPE_INT40 = 12
const val TYPE_INT48 = 13
const val TYPE_INT56 = 14
const val TYPE_INT64 = 15
val TINY_FLOATS = floatArrayOf(-8f, -7f, -6f, -5f, -4f, -3f, -2f, -1f, 0f, 1f, 2f, 3f, 4f, 5f, 6f, 7f)
val TINY_DOUBLES = doubleArrayOf(-8.0, -7.0, -6.0, -5.0, -4.0, -3.0, -2.0, -1.0, 0.0, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0)

@JsExport
open class JbonBuilder(var view: IDataView, val dictionary: JbonDict? = null) {

    /**
     * The current end in the view.
     */
    var end: Int = 0;

    fun clear(): Int {
        val old = end
        end = 0
        return old
    }

    fun writeNull() : Int {
        val pos = end;
        view.setInt8(end++, TYPE_NULL.toByte())
        return pos;
    }

    fun writeUndefined() : Int {
        val pos = end;
        view.setInt8(end++, TYPE_UNDEFINED.toByte())
        return pos;
    }

    fun writeBool(value : Boolean) : Int {
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

    fun writeSize32(value: Int): Int {
        val pos = end
        if (value < 0) throw Exception("the value must not be negative")
        when (value) {
            in 0..11 -> view.setInt8(end++, (TYPE_SIZE32 xor value).toByte())
            in 12..267 -> {
                view.setInt8(end++, (TYPE_SIZE32 xor 12).toByte())
                view.setInt8(end++, ((value - 12) and 0xff).toByte())
            }

            in 268..65535 -> {
                view.setInt8(end++, (TYPE_SIZE32 xor 13).toByte())
                view.setInt16(end, (value and 0xffff).toShort())
                end += 2
            } // TODO: Add 3 byte encoding 14
            else -> {
                view.setInt8(end++, (TYPE_SIZE32 xor 15).toByte())
                view.setInt32(end, value)
                end += 4
            }
        }
        return pos
    }
}