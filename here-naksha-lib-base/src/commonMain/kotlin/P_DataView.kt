@file:Suppress("OPT_IN_USAGE")

package com.here.naksha.lib.base

import kotlin.js.JsExport

/**
 * The Naksha type for a data view.
 */
@Suppress("LeakingThis")
@JsExport
open class P_DataView(byteArray: ByteArray? = null, offset: Int? = null, length: Int? = null) : Proxy() {
    init {
        if (!Platform.isNil(byteArray)) {
            val off = offset ?: 0
            val len = length ?: (byteArray!!.size - off)
            bind(Platform.newDataView(byteArray!!, off, len), Platform.symbolOf(this::class))
        }
    }

    override fun data(): PlatformDataViewApi = super.data() as PlatformDataViewApi
    override fun createData(): PlatformDataViewApi = Platform.newDataView(ByteArray(1024))

    /**
     * Returns the byte-array below the view.
     */
    fun getByteArray(): ByteArray = data().getByteArray()

    /**
     * Returns the offset in the underlying byte-array where the view starts.
     */
    fun getStart(): Int = data().getStart()

    /**
     * Returns the offset in the underlying byte-array where the view end, so the offset that must **not** be read.
     */
    fun getEnd(): Int = data().getEnd()

    /**
     * Returns the amount of byte being available in the view.
     */
    fun getSize(): Int = data().getSize()

    fun getFloat32(pos: Int, littleEndian: Boolean = false): Float = data().getFloat32(pos, littleEndian)
    fun setFloat32(pos: Int, value: Float, littleEndian: Boolean = false) = data().setFloat32(pos, value, littleEndian)
    fun getFloat64(pos: Int, littleEndian: Boolean = false): Double = data().getFloat64(pos, littleEndian)
    fun setFloat64(pos: Int, value: Double, littleEndian: Boolean = false) = data().setFloat64(pos, value, littleEndian)

    fun getInt8(pos: Int): Byte = data().getInt8(pos)
    fun setInt8(pos: Int, value: Byte) = data().setInt8(pos, value)
    fun getInt16(pos: Int, littleEndian: Boolean = false): Short = data().getInt16(pos, littleEndian)
    fun setInt16(pos: Int, value: Short, littleEndian: Boolean = false) = data().setInt16(pos, value, littleEndian)
    fun getInt32(pos: Int, littleEndian: Boolean = false): Int = data().getInt32(pos, littleEndian)
    fun setInt32(pos: Int, value: Int, littleEndian: Boolean = false) = data().setInt32(pos, value, littleEndian)
    fun getInt64(pos: Int, littleEndian: Boolean = false): Int64 = data().getInt64(pos, littleEndian)
    fun setInt64(pos: Int, value: Int64, littleEndian: Boolean = false) = data().setInt64(pos, value, littleEndian)
}