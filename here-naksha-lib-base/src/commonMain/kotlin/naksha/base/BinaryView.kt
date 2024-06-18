package naksha.base

import kotlin.js.JsExport

/**
 * An interface that represents a view into a byte-array using a [PlatformDataView].
 */
@Suppress("OPT_IN_USAGE")
@JsExport
interface BinaryView {
    /**
     * The byte-array in which the binary is stored. If a new value is assigned, the [pos] and [end] are only truncated if necessary.
     * @throws IllegalStateException If modification is not supported, [resize] is _false_ or [readOnly] is _true_.
     */
    var byteArray: ByteArray

    /**
     * The platform view that maps the [byteArray]. If a new value is assigned, the [pos] and [end] are only truncated if necessary.
     * @throws IllegalStateException If modification is not supported, [resize] is _false_ or [readOnly] is _true_.
     */
    var view: PlatformDataView

    /**
     * The offset in the underlying byte-array where the view starts. The data before the [byteOffset] can't be accessed
     * through this view, a new view need to be created.
     */
    val byteOffset: Int

    /**
     * The size of the view in byte. The data behind the [byteLength] can't be accessed through the view, a new view need to
     * be created.
     */
    val byteLength: Int

    /**
     * The current read position in the view. Must not be less than zero or greater than [byteLength].
     *
     * This translates to the offset in the [byteArray] as `byteOffset + pos`.
     */
    var pos: Int

    /**
     * The current write position and the first byte that must **not** be read. Must not be less than zero or greater than [byteLength].
     *
     * This translates to the offset in the [byteArray] as `byteOffset + end`.
     */
    var end: Int

    /**
     * The number of byte available for appending new data in the view, behind the [end], specifically [byteLength] - [end].
     *
     * Setting the capacity to a value being smaller than the current capacity has no effect (no shrinking is done). Increasing
     * the capacity requires [resize] to be enabled (_true_), otherwise an [IllegalStateException] will be thrown. The
     * method may increase the capacity by more than requested.
     *
     * @throws IllegalStateException If [resize] if _false_.
     */
    var byteAvailable: Int

    /**
     * If the read-only mode is explicitly enabled, then all actions that modify the underlying byte-array will cause
     * a [IllegalStateException], this includes resizing.
     */
    var readOnly: Boolean

    /**
     * If the underlying byte-array may be replaced with a bigger, when more capacity is needed, or smaller one.
     * @throws IllegalStateException When the implementation does not support resizing.
     */
    var resize: Boolean

    /**
     * Sets the [pos] and [end] to `0`, so that the binary can be written again. Does not change [readOnly]
     * or [resize]. This does not actually modify the data in the buffer.
     * @return The previous value of [end].
     */
    fun reset(): Int

    /**
     * Changes the size of the underlying byte-array by creating a copy with the new size. The [pos] and [end] are
     * only truncated if necessary.
     *
     * @param newSize The new size in byte.
     * @param exact If _true_, then the resize is done to exactly the given [newSize]; otherwise optimizations are
     * applied that may result in a bigger view (or not modified one).
     * @throws IllegalStateException If the binary is [readOnly] or [resize] is _false_.
     */
    fun resizeTo(newSize: Int, exact:Boolean = false)

    /**
     * Internally called by all getters to ensure that reading the given number of byte from the given position is okay.
     * @param pos The position to read, must be greater than `0`.
     * @param bytes The number of bytes to read, must be greater than `0` and less than [byteLength].
     * @param size The size to test against.
     * @throws IndexOutOfBoundsException If reading that amount of byte from that position is out of bounds ([size]).
     */
    fun prepareRead(pos: Int, bytes: Int, size: Int)

    /**
     * Internally called by all setters to ensure that writing the given number of byte to the given position is okay.
     *
     * The method will set the [byteAvailable] and therefore may cause a [resize].
     * @param pos The position to write, must be less than [byteLength].
     * @param bytes The number of bytes to write.
     * @param resize If the underlying byte-array should be resized to allow to write.
     * @throws IndexOutOfBoundsException If resizing failed and otherwise that amount of bytes can't be written to that position.
     * @throws IllegalStateException If the binary is [readOnly].
     */
    fun prepareWrite(pos: Int, bytes: Int, resize: Boolean = this.resize)

    /**
     * Read from the [byteArray].
     * @param pos The position to read (between `0` and [byteLength]). The offset in the [byteArray] is calculated as [byteOffset] + [pos].
     * @param littleEndian If the data is stored in [little-endian](https://en.wikipedia.org/wiki/Endianness) in the [byteArray].
     * @return The read value.
     * @throws IndexOutOfBoundsException If the [pos] is at [byteLength] or reading would require to read [byteLength].
     */
    fun getFloat32(pos: Int, littleEndian: Boolean = false): Float

    /**
     * Writes into the [byteArray].
     * @param pos The position to write (between `0` and [byteLength]). The offset in the [byteArray] is calculated as [byteOffset] + [pos].
     * @param littleEndian If the data should be stored in [little-endian](https://en.wikipedia.org/wiki/Endianness) in the [byteArray].
     * @throws IndexOutOfBoundsException If [resize] is _false_ and the [end] is at [byteLength] or writing would require to write at [byteLength].
     */
    fun setFloat32(pos: Int, value: Float, littleEndian: Boolean = false)

    /**
     * Read from the [byteArray] at [pos] and increment the position.
     * @param littleEndian If the data is stored in [little-endian](https://en.wikipedia.org/wiki/Endianness) in the [byteArray].
     * @return The read value.
     * @throws IndexOutOfBoundsException If the [pos] is at [end] or reading would require to read [end].
     */
    fun readFloat32(littleEndian: Boolean = false): Float

    /**
     * Writes into the [byteArray] at [end] and increment the end.
     * @param littleEndian If the data should be stored in [little-endian](https://en.wikipedia.org/wiki/Endianness) in the [byteArray].
     * @throws IndexOutOfBoundsException If [resize] is _false_ and the [end] is at [byteLength] or writing would require to write at [byteLength].
     */
    fun writeFloat32(value: Float, littleEndian: Boolean = false)

    /**
     * Read from the [byteArray].
     * @param pos The position to read (between `0` and [byteLength]). The offset in the [byteArray] is calculated as [byteOffset] + [pos].
     * @param littleEndian If the data is stored in [little-endian](https://en.wikipedia.org/wiki/Endianness) in the [byteArray].
     * @return The read value.
     * @throws IndexOutOfBoundsException If the [pos] is at [byteLength] or reading would require to read [byteLength].
     */
    fun getFloat64(pos: Int, littleEndian: Boolean = false): Double

    /**
     * Writes into the [byteArray].
     * @param pos The position to write (between `0` and [byteLength]). The offset in the [byteArray] is calculated as [byteOffset] + [pos].
     * @param littleEndian If the data should be stored in [little-endian](https://en.wikipedia.org/wiki/Endianness) in the [byteArray].
     * @throws IndexOutOfBoundsException If [resize] is _false_ and the [end] is at [byteLength] or writing would require to write at [byteLength].
     */
    fun setFloat64(pos: Int, value: Double, littleEndian: Boolean = false)

    /**
     * Read from the [byteArray] at [pos] and increment the position.
     * @param littleEndian If the data is stored in [little-endian](https://en.wikipedia.org/wiki/Endianness) in the [byteArray].
     * @return The read value.
     * @throws IndexOutOfBoundsException If the [pos] is at [end] or reading would require to read [end].
     */
    fun readFloat64(littleEndian: Boolean = false): Double

    /**
     * Writes into the [byteArray] at [end] and increment the end.
     * @param littleEndian If the data should be stored in [little-endian](https://en.wikipedia.org/wiki/Endianness) in the [byteArray].
     * @throws IndexOutOfBoundsException If [resize] is _false_ and the [end] is at [byteLength] or writing would require to write at [byteLength].
     */
    fun writeFloat64(value: Double, littleEndian: Boolean = false)

    /**
     * Read from the [byteArray].
     * @param pos The position to read (between `0` and [byteLength]). The offset in the [byteArray] is calculated as [byteOffset] + [pos].
     * @return The read value.
     * @throws IndexOutOfBoundsException If the [pos] is at [byteLength] or reading would require to read [byteLength].
     */
    fun getInt8(pos: Int): Byte

    /**
     * Writes into the [byteArray].
     * @param pos The position to write (between `0` and [byteLength]). The offset in the [byteArray] is calculated as [byteOffset] + [pos].
     * @throws IndexOutOfBoundsException If [resize] is _false_ and the [end] is at [byteLength] or writing would require to write at [byteLength].
     */
    fun setInt8(pos: Int, value: Byte)

    /**
     * Read from the [byteArray] at [pos] and increment the position.
     * @return The read value.
     * @throws IndexOutOfBoundsException If the [pos] is at [end] or reading would require to read [end].
     */
    fun readInt8(): Byte

    /**
     * Writes into the [byteArray] at [end] and increment the end.
     * @throws IndexOutOfBoundsException If [resize] is _false_ and the [end] is at [byteLength] or writing would require to write at [byteLength].
     */
    fun writeInt8(value: Byte)

    /**
     * Read from the [byteArray].
     * @param pos The position to read (between `0` and [byteLength]). The offset in the [byteArray] is calculated as [byteOffset] + [pos].
     * @param littleEndian If the data is stored in [little-endian](https://en.wikipedia.org/wiki/Endianness) in the [byteArray].
     * @return The read value.
     * @throws IndexOutOfBoundsException If the [pos] is at [byteLength] or reading would require to read [byteLength].
     */
    fun getInt16(pos: Int, littleEndian: Boolean = false): Short

    /**
     * Writes into the [byteArray].
     * @param pos The position to write (between `0` and [byteLength]). The offset in the [byteArray] is calculated as [byteOffset] + [pos].
     * @param littleEndian If the data should be stored in [little-endian](https://en.wikipedia.org/wiki/Endianness) in the [byteArray].
     * @throws IndexOutOfBoundsException If [resize] is _false_ and the [end] is at [byteLength] or writing would require to write at [byteLength].
     */
    fun setInt16(pos: Int, value: Short, littleEndian: Boolean = false)

    /**
     * Read from the [byteArray] at [pos] and increment the position.
     * @param littleEndian If the data is stored in [little-endian](https://en.wikipedia.org/wiki/Endianness) in the [byteArray].
     * @return The read value.
     * @throws IndexOutOfBoundsException If the [pos] is at [end] or reading would require to read [end].
     */
    fun readInt16(littleEndian: Boolean = false): Short

    /**
     * Writes into the [byteArray] at [end] and increment the end.
     * @param littleEndian If the data should be stored in [little-endian](https://en.wikipedia.org/wiki/Endianness) in the [byteArray].
     * @throws IndexOutOfBoundsException If [resize] is _false_ and the [end] is at [byteLength] or writing would require to write at [byteLength].
     */
    fun writeInt16(value: Short, littleEndian: Boolean = false)

    /**
     * Read from the [byteArray].
     * @param pos The position to read (between `0` and [byteLength]). The offset in the [byteArray] is calculated as [byteOffset] + [pos].
     * @param littleEndian If the data is stored in [little-endian](https://en.wikipedia.org/wiki/Endianness) in the [byteArray].
     * @return The read value.
     * @throws IndexOutOfBoundsException If the [pos] is at [byteLength] or reading would require to read [byteLength].
     */
    fun getInt32(pos: Int, littleEndian: Boolean = false): Int

    /**
     * Writes into the [byteArray].
     * @param pos The position to write (between `0` and [byteLength]). The offset in the [byteArray] is calculated as [byteOffset] + [pos].
     * @param littleEndian If the data should be stored in [little-endian](https://en.wikipedia.org/wiki/Endianness) in the [byteArray].
     * @throws IndexOutOfBoundsException If [resize] is _false_ and the [end] is at [byteLength] or writing would require to write at [byteLength].
     */
    fun setInt32(pos: Int, value: Int, littleEndian: Boolean = false)

    /**
     * Read from the [byteArray] at [pos] and increment the position.
     * @param littleEndian If the data is stored in [little-endian](https://en.wikipedia.org/wiki/Endianness) in the [byteArray].
     * @return The read value.
     * @throws IndexOutOfBoundsException If the [pos] is at [end] or reading would require to read [end].
     */
    fun readInt32(littleEndian: Boolean = false): Int

    /**
     * Writes into the [byteArray] at [end] and increment the end.
     * @param littleEndian If the data should be stored in [little-endian](https://en.wikipedia.org/wiki/Endianness) in the [byteArray].
     * @throws IndexOutOfBoundsException If [resize] is _false_ and the [end] is at [byteLength] or writing would require to write at [byteLength].
     */
    fun writeInt32(value: Int, littleEndian: Boolean = false)

    /**
     * Read from the [byteArray].
     * @param pos The position to read (between `0` and [byteLength]). The offset in the [byteArray] is calculated as [byteOffset] + [pos].
     * @param littleEndian If the data is stored in [little-endian](https://en.wikipedia.org/wiki/Endianness) in the [byteArray].
     * @return The read value.
     * @throws IndexOutOfBoundsException If the [pos] is at [byteLength] or reading would require to read [byteLength].
     */
    fun getInt64(pos: Int, littleEndian: Boolean = false): Int64

    /**
     * Writes into the [byteArray].
     * @param pos The position to write (between `0` and [byteLength]). The offset in the [byteArray] is calculated as [byteOffset] + [pos].
     * @param littleEndian If the data should be stored in [little-endian](https://en.wikipedia.org/wiki/Endianness) in the [byteArray].
     * @throws IndexOutOfBoundsException If [resize] is _false_ and the [end] is at [byteLength] or writing would require to write at [byteLength].
     */
    fun setInt64(pos: Int, value: Int64, littleEndian: Boolean = false)

    /**
     * Read from the [byteArray] at [pos] and increment the position.
     * @param littleEndian If the data is stored in [little-endian](https://en.wikipedia.org/wiki/Endianness) in the [byteArray].
     * @return The read value.
     * @throws IndexOutOfBoundsException If the [pos] is at [end] or reading would require to read [end].
     */
    fun readInt64(littleEndian: Boolean = false): Int64

    /**
     * Writes into the [byteArray] at [end] and increment the end.
     * @param littleEndian If the data should be stored in [little-endian](https://en.wikipedia.org/wiki/Endianness) in the [byteArray].
     * @throws IndexOutOfBoundsException If [resize] is _false_ and the [end] is at [byteLength] or writing would require to write at [byteLength].
     */
    fun writeInt64(value: Int64, littleEndian: Boolean = false)

}