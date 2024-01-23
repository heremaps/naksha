@file:OptIn(ExperimentalJsExport::class)

package com.here.naksha.lib.jbon;

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

const val TYPE_INT4 = 0;
const val TYPE_SIZE32 = 2 shl 4;
const val TYPE_INT8 = 8 shl 4;
const val TYPE_INT16 = 9 shl 4;
const val TYPE_INT24 = 10 shl 4;
const val TYPE_INT32 = 11 shl 4;
const val TYPE_INT40 = 12 shl 4;
const val TYPE_INT48 = 13 shl 4;
const val TYPE_INT56 = 14 shl 4;
const val TYPE_INT64 = 15 shl 4;

@JsExport
open class JbonBuilder(var view: IDataView, val dictionary: JbonDict? = null) {

    /**
     * The current end in the view.
     */
    var end: Int = 0;

    fun clear() : Int {
        val old = end
        end = 0
        return old
    }

    fun writeInt(value: Int): Int {
        val pos = end;
        if (value >= -8 && value <= 7) {
            // encode int4
            view.setInt8(pos, (value + 8).toByte())
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

    fun writeSize32(value: Int) : Int {
        val pos = end;
        if (value < 0) throw Exception("the value must not be negative")
        when (value) {
            in 0..11 -> view.setInt8(end++, (TYPE_SIZE32 xor value).toByte())
            in 12 .. 267 -> {
                view.setInt8(end++, (TYPE_SIZE32 xor 12).toByte())
                view.setInt8(end++, ((value-12) and 0xff).toByte())
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