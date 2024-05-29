@file:Suppress("OPT_IN_USAGE")

package com.here.naksha.lib.base

import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * A view that allows to read and mutate the underlying byte-array.
 */
@JsExport
@JsName("DataView")
interface N_DataView : N_Object {
    /**
     * Returns the byte-array below the view.
     */
    fun getByteArray(): ByteArray

    /**
     * Returns the offset in the underlying byte-array where the view starts.
     */
    fun getStart(): Int

    /**
     * Returns the offset in the underlying byte-array where the view end, so the offset that must **not** be read.
     */
    fun getEnd(): Int

    /**
     * Returns the amount of byte being available in the view.
     */
    fun getSize(): Int

    fun getFloat32(pos: Int, littleEndian: Boolean = false): Float
    fun setFloat32(pos: Int, value: Float, littleEndian: Boolean = false)
    fun getFloat64(pos: Int, littleEndian: Boolean = false): Double
    fun setFloat64(pos: Int, value: Double, littleEndian: Boolean = false)

    fun getInt8(pos: Int): Byte
    fun setInt8(pos: Int, value: Byte)
    fun getInt16(pos: Int, littleEndian: Boolean = false): Short
    fun setInt16(pos: Int, value: Short, littleEndian: Boolean = false)
    fun getInt32(pos: Int, littleEndian: Boolean = false): Int
    fun setInt32(pos: Int, value: Int, littleEndian: Boolean = false)
    fun getInt64(pos: Int, littleEndian: Boolean = false): Int64
    fun setInt64(pos: Int, value: Int64, littleEndian: Boolean = false)
}