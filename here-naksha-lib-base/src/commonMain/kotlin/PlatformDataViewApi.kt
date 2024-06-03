@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package com.here.naksha.lib.base


internal expect class PlatformDataViewApi {
    // https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/DataView
    companion object {
        fun dataview_get_byte_array(view: PlatformDataView): ByteArray
        fun dataview_get_start(view: PlatformDataView): Int
        fun dataview_get_end(view: PlatformDataView): Int
        fun dataview_get_size(view: PlatformDataView): Int

        fun dataview_get_float32(view: PlatformDataView, pos: Int, littleEndian: Boolean = false): Float
        fun dataview_set_float32(view: PlatformDataView, pos: Int, value: Float, littleEndian: Boolean = false)
        fun dataview_get_float64(view: PlatformDataView, pos: Int, littleEndian: Boolean = false): Double
        fun dataview_set_float64(view: PlatformDataView, pos: Int, value: Double, littleEndian: Boolean = false)

        fun dataview_get_int8(view: PlatformDataView, pos: Int): Byte
        fun dataview_set_int8(view: PlatformDataView, pos: Int, value: Byte)
        fun dataview_get_int16(view: PlatformDataView, pos: Int, littleEndian: Boolean = false): Short
        fun dataview_set_int16(view: PlatformDataView, pos: Int, value: Short, littleEndian: Boolean = false)
        fun dataview_get_int32(view: PlatformDataView, pos: Int, littleEndian: Boolean = false): Int
        fun dataview_set_int32(view: PlatformDataView, pos: Int, value: Int, littleEndian: Boolean = false)
        fun dataview_get_int64(view: PlatformDataView, pos: Int, littleEndian: Boolean = false): Int64
        fun dataview_set_int64(view: PlatformDataView, pos: Int, value: Int64, littleEndian: Boolean = false)
    }
}