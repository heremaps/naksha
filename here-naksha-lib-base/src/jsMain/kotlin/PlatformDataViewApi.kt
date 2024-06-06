package com.here.naksha.lib.base

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual class PlatformDataViewApi {
    // https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/DataView
    @OptIn(ExperimentalJsStatic::class)
    actual companion object {
        @JsStatic
        actual fun dataview_get_byte_array(view: PlatformDataView): ByteArray {
            TODO("Not yet implemented")
        }

        @JsStatic
        actual fun dataview_get_start(view: PlatformDataView): Int {
            TODO("Not yet implemented")
        }

        @JsStatic
        actual fun dataview_get_end(view: PlatformDataView): Int {
            TODO("Not yet implemented")
        }

        @JsStatic
        actual fun dataview_get_size(view: PlatformDataView): Int {
            TODO("Not yet implemented")
        }

        @JsStatic
        actual fun dataview_get_float32(
            view: PlatformDataView,
            pos: Int,
            littleEndian: Boolean
        ): Float {
            TODO("Not yet implemented")
        }

        @JsStatic
        actual fun dataview_set_float32(
            view: PlatformDataView,
            pos: Int,
            value: Float,
            littleEndian: Boolean
        ) {
        }

        @JsStatic
        actual fun dataview_get_float64(
            view: PlatformDataView,
            pos: Int,
            littleEndian: Boolean
        ): Double {
            TODO("Not yet implemented")
        }

        @JsStatic
        actual fun dataview_set_float64(
            view: PlatformDataView,
            pos: Int,
            value: Double,
            littleEndian: Boolean
        ) {
        }

        @JsStatic
        actual fun dataview_get_int8(view: PlatformDataView, pos: Int): Byte {
            TODO("Not yet implemented")
        }

        @JsStatic
        actual fun dataview_set_int8(view: PlatformDataView, pos: Int, value: Byte) {
        }

        @JsStatic
        actual fun dataview_get_int16(
            view: PlatformDataView,
            pos: Int,
            littleEndian: Boolean
        ): Short {
            TODO("Not yet implemented")
        }

        @JsStatic
        actual fun dataview_set_int16(
            view: PlatformDataView,
            pos: Int,
            value: Short,
            littleEndian: Boolean
        ) {
        }

        @JsStatic
        actual fun dataview_get_int32(
            view: PlatformDataView,
            pos: Int,
            littleEndian: Boolean
        ): Int {
            TODO("Not yet implemented")
        }

        @JsStatic
        actual fun dataview_set_int32(
            view: PlatformDataView,
            pos: Int,
            value: Int,
            littleEndian: Boolean
        ) {
        }

        @JsStatic
        actual fun dataview_get_int64(
            view: PlatformDataView,
            pos: Int,
            littleEndian: Boolean
        ): Int64 {
            TODO("Not yet implemented")
        }

        @JsStatic
        actual fun dataview_set_int64(
            view: PlatformDataView,
            pos: Int,
            value: Int64,
            littleEndian: Boolean
        ) {
        }

    }
}