package naksha.base

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual class PlatformDataViewApi {
    // https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/DataView
    @OptIn(ExperimentalJsStatic::class)
    actual companion object {
        @JsStatic
        actual fun dataview_get_byte_array(view: PlatformDataView): ByteArray = js("new Int8Array(view.buffer)").unsafeCast<ByteArray>()

        @JsStatic
        actual fun dataview_get_start(view: PlatformDataView): Int = view.asDynamic().byteOffset.unsafeCast<Int>()

        @JsStatic
        actual fun dataview_get_size(view: PlatformDataView): Int = view.asDynamic().byteLength.unsafeCast<Int>()

        @JsStatic
        actual fun dataview_get_float32(
            view: PlatformDataView,
            pos: Int,
            littleEndian: Boolean
        ): Float = view.asDynamic().getFloat32(pos, littleEndian).unsafeCast<Float>()

        @JsStatic
        actual fun dataview_set_float32(
            view: PlatformDataView,
            pos: Int,
            value: Float,
            littleEndian: Boolean
        ) {
            view.asDynamic().setFloat32(pos, value, littleEndian)
        }

        @JsStatic
        actual fun dataview_get_float64(
            view: PlatformDataView,
            pos: Int,
            littleEndian: Boolean
        ): Double = view.asDynamic().getFloat64(pos, littleEndian).unsafeCast<Double>()

        @JsStatic
        actual fun dataview_set_float64(
            view: PlatformDataView,
            pos: Int,
            value: Double,
            littleEndian: Boolean
        ) {
            view.asDynamic().setFloat64(pos, value, littleEndian)
        }

        @JsStatic
        actual fun dataview_get_int8(view: PlatformDataView, pos: Int): Byte = view.asDynamic().getInt8(pos).unsafeCast<Byte>()

        @JsStatic
        actual fun dataview_set_int8(view: PlatformDataView, pos: Int, value: Byte) {
            view.asDynamic().setInt8(pos, value)
        }

        @JsStatic
        actual fun dataview_get_int16(
            view: PlatformDataView,
            pos: Int,
            littleEndian: Boolean
        ): Short = view.asDynamic().getInt16(pos, littleEndian).unsafeCast<Short>()

        @JsStatic
        actual fun dataview_set_int16(
            view: PlatformDataView,
            pos: Int,
            value: Short,
            littleEndian: Boolean
        ) {
            view.asDynamic().setInt16(pos, value, littleEndian)
        }

        @JsStatic
        actual fun dataview_get_int32(
            view: PlatformDataView,
            pos: Int,
            littleEndian: Boolean
        ): Int = view.asDynamic().getInt32(pos, littleEndian).unsafeCast<Int>()

        @JsStatic
        actual fun dataview_set_int32(
            view: PlatformDataView,
            pos: Int,
            value: Int,
            littleEndian: Boolean
        ) {
            view.asDynamic().setInt32(pos, value, littleEndian)
        }

        @JsStatic
        actual fun dataview_get_int64(
            view: PlatformDataView,
            pos: Int,
            littleEndian: Boolean
        ): Int64 = view.asDynamic().getBigInt64(pos, littleEndian).unsafeCast<Int64>()

        @JsStatic
        actual fun dataview_set_int64(
            view: PlatformDataView,
            pos: Int,
            value: Int64,
            littleEndian: Boolean
        ) {
            view.asDynamic().setBigInt64(pos, value, littleEndian)
        }

    }
}