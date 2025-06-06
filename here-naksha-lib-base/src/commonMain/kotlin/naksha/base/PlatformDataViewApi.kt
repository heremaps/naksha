package naksha.base

/**
 * Static singleton that allows to access data-views.
 *
 * A data-view is a wrapper on top of a byte-buffer (some memory), that allows accessing the underlying bytes more flexible than [ByteArray] does. Note that in JavaScript the [ByteArray] is as well a view (`Uint8Array`) that is backed by a byte-buffer, while in Java the [ByteArray] is the same as the byte-buffer. For this reason, the byte-buffer is never exposed or visible somewhere, and everything operates only on the [ByteArray] or the data-view.
 * @since 3.0.0
 */
@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
expect class PlatformDataViewApi private constructor() {
    // https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/DataView
    companion object PlatformDataViewApiCompanion {
        /**
         * Return a byte-array sharing the byte-buffer with view.
         * @param view the view to query.
         * @return the [ByteArray] that shares the byte-buffer with the view.
         * @since 3.0.0
         */
        fun dataview_get_byte_array(view: PlatformDataView): ByteArray

        /**
         * Return the offset to the underlying byte-buffer.
         * @param view the view to query.
         * @return the byte-buffer offset, so where the offset 0 in the view starts in the underlying byte-buffer.
         * @since 3.0.0
         */
        fun dataview_get_start(view: PlatformDataView): Int

        /**
         * Return the size of the view, so the amount of byte the view covers.
         * @param view the view to query.
         * @return the amount of byte that are covered by the view.
         * @since 3.0.0
         */
        fun dataview_get_size(view: PlatformDataView): Int

        /**
         * Read a 32-bit IEEE-floating point number from the view.
         * @param view the view to query.
         * @param pos the offset in the view to read the first byte from.
         * @param littleEndian _true_ if little-endian encoding should be used.
         * @return the read value.
         * @since 3.0.0
         */
        fun dataview_get_float32(view: PlatformDataView, pos: Int, littleEndian: Boolean = false): Float

        /**
         * Write a 32-bit IEEE-floating point number into the view.
         * @param view the view to query.
         * @param pos the offset in the view to write the first byte to.
         * @param value the value to write.
         * @param littleEndian _true_ if little-endian encoding should be used.
         * @since 3.0.0
         */
        fun dataview_set_float32(view: PlatformDataView, pos: Int, value: Float, littleEndian: Boolean = false)

        /**
         * Read a 64-bit IEEE-floating point number from the view.
         * @param view the view to query.
         * @param pos the offset in the view to read the first byte from.
         * @param littleEndian _true_ if little-endian encoding should be used.
         * @return the read value.
         */
        fun dataview_get_float64(view: PlatformDataView, pos: Int, littleEndian: Boolean = false): Double

        /**
         * Write a 64-bit IEEE-floating point number into the view.
         * @param view the view to query.
         * @param pos the offset in the view to write the first byte to.
         * @param value the value to write.
         * @param littleEndian _true_ if little-endian encoding should be used.
         * @since 3.0.0
         */
        fun dataview_set_float64(view: PlatformDataView, pos: Int, value: Double, littleEndian: Boolean = false)

        /**
         * Read a single byte from the view.
         * @param view the view to query.
         * @param pos the offset in the view to read the byte from.
         * @return the read value.
         * @since 3.0.0
         */
        fun dataview_get_int8(view: PlatformDataView, pos: Int): Byte

        /**
         * Write a single byte into the view.
         * @param view the view to query.
         * @param pos the offset in the view to write the byte to.
         * @param value the value to write.
         * @since 3.0.0
         */
        fun dataview_set_int8(view: PlatformDataView, pos: Int, value: Byte)

        /**
         * Read a 16-bit integer from the view.
         * @param view the view to query.
         * @param pos the offset in the view to read the first byte from.
         * @param littleEndian _true_ if little-endian encoding should be used.
         * @return the read value.
         * @since 3.0.0
         */
        fun dataview_get_int16(view: PlatformDataView, pos: Int, littleEndian: Boolean = false): Short

        /**
         * Write a 16-bit integer into the view.
         * @param view the view to query.
         * @param pos the offset in the view to write the first byte to.
         * @param value the value to write.
         * @param littleEndian _true_ if little-endian encoding should be used.
         * @since 3.0.0
         */
        fun dataview_set_int16(view: PlatformDataView, pos: Int, value: Short, littleEndian: Boolean = false)

        /**
         * Read a 32-bit integer from the view.
         * @param view the view to query.
         * @param pos the offset in the view to read the first byte from.
         * @param littleEndian _true_ if little-endian encoding should be used.
         * @return the read value.
         * @since 3.0.0
         */
        fun dataview_get_int32(view: PlatformDataView, pos: Int, littleEndian: Boolean = false): Int

        /**
         * Write a 32-bit integer into the view.
         * @param view the view to query.
         * @param pos the offset in the view to write the first byte to.
         * @param value the value to write.
         * @param littleEndian _true_ if little-endian encoding should be used.
         * @since 3.0.0
         */
        fun dataview_set_int32(view: PlatformDataView, pos: Int, value: Int, littleEndian: Boolean = false)

        /**
         * Read a 64-bit integer from the view.
         * @param view the view to query.
         * @param pos the offset in the view to read the first byte from.
         * @param littleEndian _true_ if little-endian encoding should be used.
         * @return the read value.
         * @since 3.0.0
         */
        fun dataview_get_int64(view: PlatformDataView, pos: Int, littleEndian: Boolean = false): Int64

        /**
         * Write a 64-bit integer into the view.
         * @param view the view to query.
         * @param pos the offset in the view to write the first byte to.
         * @param value the value to write.
         * @param littleEndian _true_ if little-endian encoding should be used.
         * @since 3.0.0
         */
        fun dataview_set_int64(view: PlatformDataView, pos: Int, value: Int64, littleEndian: Boolean = false)
    }
}

// TODO: Allow usage of SharedArrayBuffer for backing the Uint8Array (fun Platform.newSharedByteArray(size: Int): ByteArray)
//       Allow detection if a ByteArray (which is an Uint8Array) is based upon a shared-buffer.
//       Add atomic operations, especially compareAndGet, based upon JavaScript `Atomics.compareExchange` or use Java unsafe code.
//       See: https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/SharedArrayBuffer
//       See: https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Atomics/compareExchange
