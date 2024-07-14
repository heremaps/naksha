package naksha.base

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual class PlatformDataViewApi {
    // https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/DataView
    actual companion object PlatformDataViewApiCompanion {
        @JvmStatic
        actual fun dataview_get_byte_array(view: PlatformDataView): ByteArray =
            (view as JvmDataView).getByteArray()

        @JvmStatic
        actual fun dataview_get_start(view: PlatformDataView): Int =
            (view as JvmDataView).getStart()

        @JvmStatic
        actual fun dataview_get_size(view: PlatformDataView): Int =
            (view as JvmDataView).getSize()

        @JvmStatic
        actual fun dataview_get_float32(view: PlatformDataView, pos: Int, littleEndian: Boolean): Float =
            (view as JvmDataView).getFloat32(pos, littleEndian)

        @JvmStatic
        actual fun dataview_set_float32(view: PlatformDataView, pos: Int, value: Float, littleEndian: Boolean) =
            (view as JvmDataView).setFloat32(pos, value, littleEndian)

        @JvmStatic
        actual fun dataview_get_float64(view: PlatformDataView, pos: Int, littleEndian: Boolean): Double =
            (view as JvmDataView).getFloat64(pos, littleEndian)

        @JvmStatic
        actual fun dataview_set_float64(view: PlatformDataView, pos: Int, value: Double, littleEndian: Boolean) =
            (view as JvmDataView).setFloat64(pos, value, littleEndian)

        @JvmStatic
        actual fun dataview_get_int8(view: PlatformDataView, pos: Int): Byte =
            (view as JvmDataView).getInt8(pos)

        @JvmStatic
        actual fun dataview_set_int8(view: PlatformDataView, pos: Int, value: Byte) =
            (view as JvmDataView).setInt8(pos, value)

        @JvmStatic
        actual fun dataview_get_int16(view: PlatformDataView, pos: Int, littleEndian: Boolean): Short =
            (view as JvmDataView).getInt16(pos, littleEndian)

        @JvmStatic
        actual fun dataview_set_int16(view: PlatformDataView, pos: Int, value: Short, littleEndian: Boolean) =
            (view as JvmDataView).setInt16(pos, value, littleEndian)

        @JvmStatic
        actual fun dataview_get_int32(view: PlatformDataView, pos: Int, littleEndian: Boolean): Int =
            (view as JvmDataView).getInt32(pos, littleEndian)

        @JvmStatic
        actual fun dataview_set_int32(view: PlatformDataView, pos: Int, value: Int, littleEndian: Boolean) =
            (view as JvmDataView).setInt32(pos, value, littleEndian)

        @JvmStatic
        actual fun dataview_get_int64(view: PlatformDataView, pos: Int, littleEndian: Boolean): Int64 =
            (view as JvmDataView).getInt64(pos, littleEndian)

        @JvmStatic
        actual fun dataview_set_int64(view: PlatformDataView, pos: Int, value: Int64, littleEndian: Boolean) =
            (view as JvmDataView).setInt64(pos, value, littleEndian)
    }
}