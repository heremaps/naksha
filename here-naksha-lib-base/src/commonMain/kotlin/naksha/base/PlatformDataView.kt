package naksha.base

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * A data-view is a logical wrapper on top of a [ByteArray] to read and manipulate the bytes.
 *
 * ### Java
 * In _Java_ the [ByteArray] is represented by a simple byte-array _(`byte[]`)_, so there is no intermediate layer.
 *
 * The [PlatformDataView] is implemented as `JvmDataView`, which actually wraps a [ByteArray], and allows to read and mutate the underlying bytes in various ways.
 *
 * ### JavaScript
 * In _JavaScript_ the [ByteArray] is represented as [Uint8Array](https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Uint8Array), which itself is just a view on an [ArrayBuffer](https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/ArrayBuffer) or [SharedArrayBuffer](https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/SharedArrayBuffer), which represents some memory. Therefore, in _JavaScript_ there is an intermediate layer for [ByteArray].
 *
 * The [PlatformDataView] is represented as [DataView](https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/DataView), a native _JavaScript_ object.
 * @since 3.0.0
 */
@OptIn(ExperimentalJsExport::class)
@JsExport
@JsName("DataView")
interface PlatformDataView : PlatformObject {
    /**
     * The underlying byte-array mapped by the view.
     *
     * ### Note
     * In _JavaScript_ there is a `buffer` property that refers to the underlying low level [ArrayBuffer](https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/ArrayBuffer) or [SharedArrayBuffer](https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/SharedArrayBuffer). This property is added by the Naksha framework to all [DataView](https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/DataView) instances, and wraps this underlying `buffer` into an [Uint8Array](https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Uint8Array).
     * @since 3.0
     */
    val byteArray: ByteArray

    /**
     * The length (in bytes) of this view, so basically `byteArray.size - byteOffset`.
     * @since 3.0
     */
    val byteLength: Int

    /**
     * The offset (in bytes) of this view from the start of its [byteArray].
     * @since 3.0
     */
    val byteOffset: Int

    // TODO: Add 16-bit float support, JavaScript has it!

    /**
     * Read a 32-bit IEEE-floating point number from the view.
     * @param byteOffset the offset in the view to read the first byte from.
     * @param littleEndian _true_ if little-endian encoding should be used.
     * @return the read value.
     * @since 3.0.0
     */
    fun getFloat32(byteOffset: Int, littleEndian: Boolean = false): Float

    /**
     * Write a 32-bit IEEE-floating point number into the view.
     * @param byteOffset the offset in the view to write the first byte to.
     * @param value the value to write.
     * @param littleEndian _true_ if little-endian encoding should be used.
     * @since 3.0.0
     */
    fun setFloat32(byteOffset: Int, value: Float, littleEndian: Boolean = false)

    /**
     * Read a 64-bit IEEE-floating point number from the view.
     * @param byteOffset the offset in the view to read the first byte from.
     * @param littleEndian _true_ if little-endian encoding should be used.
     * @return the read value.
     */
    fun getFloat64(byteOffset: Int, littleEndian: Boolean = false): Double

    /**
     * Write a 64-bit IEEE-floating point number into the view.
     * @param byteOffset the offset in the view to write the first byte to.
     * @param value the value to write.
     * @param littleEndian _true_ if little-endian encoding should be used.
     * @since 3.0.0
     */
    fun setFloat64(byteOffset: Int, value: Double, littleEndian: Boolean = false)

    /**
     * Read a single byte from the view.
     * @param byteOffset the offset in the view to read the byte from.
     * @return the read value.
     * @since 3.0.0
     */
    fun getInt8(byteOffset: Int): Byte

    /**
     * Write a single byte into the view.
     * @param byteOffset the offset in the view to write the byte to.
     * @param value the value to write.
     * @since 3.0.0
     */
    fun setInt8(byteOffset: Int, value: Byte)

    /**
     * Read a 16-bit integer from the view.
     * @param byteOffset the offset in the view to read the first byte from.
     * @param littleEndian _true_ if little-endian encoding should be used.
     * @return the read value.
     * @since 3.0.0
     */
    fun getInt16(byteOffset: Int, littleEndian: Boolean = false): Short

    /**
     * Write a 16-bit integer into the view.
     * @param byteOffset the offset in the view to write the first byte to.
     * @param value the value to write.
     * @param littleEndian _true_ if little-endian encoding should be used.
     * @since 3.0.0
     */
    fun setInt16(byteOffset: Int, value: Short, littleEndian: Boolean = false)

    /**
     * Read a 32-bit integer from the view.
     * @param byteOffset the offset in the view to read the first byte from.
     * @param littleEndian _true_ if little-endian encoding should be used.
     * @return the read value.
     * @since 3.0.0
     */
    fun getInt32(byteOffset: Int, littleEndian: Boolean = false): Int

    /**
     * Write a 32-bit integer into the view.
     * @param byteOffset the offset in the view to write the first byte to.
     * @param value the value to write.
     * @param littleEndian _true_ if little-endian encoding should be used.
     * @since 3.0.0
     */
    fun setInt32(byteOffset: Int, value: Int, littleEndian: Boolean = false)

    /**
     * Read a 64-bit integer from the view.
     * @param byteOffset the offset in the view to read the first byte from.
     * @param littleEndian _true_ if little-endian encoding should be used.
     * @return the read value.
     * @since 3.0.0
     */
    fun getInt64(byteOffset: Int, littleEndian: Boolean = false): Int64

    /**
     * Write a 64-bit integer into the view.
     * @param byteOffset the offset in the view to write the first byte to.
     * @param value the value to write.
     * @param littleEndian _true_ if little-endian encoding should be used.
     * @since 3.0.0
     */
    fun setInt64(byteOffset: Int, value: Int64, littleEndian: Boolean = false)
}