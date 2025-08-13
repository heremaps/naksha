package naksha.base

import naksha.base.Platform.Platform_C.longToInt64
import java.nio.ByteOrder

/**
 * The JVM implementation of [PlatformDataViewApi].
 */
@Suppress("LeakingThis", "DEPRECATION")
internal open class JvmDataView(
    override val byteArray: ByteArray,
    override val byteOffset: Int = 0,
    override val byteLength: Int = byteArray.size - byteOffset
) : JvmObject(), PlatformDataView {
    init {
        require(byteOffset >= 0 && byteOffset <= byteArray.size)
        require(byteLength >= byteOffset && (byteOffset + byteLength) <= byteArray.size)
    }

    private val startOffset: Int = Platform.baseOffset + byteOffset
    private val endOffset: Int = startOffset + byteLength

    fun getStart(): Int {
        return startOffset - Platform.baseOffset
    }

    fun getEnd(): Int {
        return endOffset - Platform.baseOffset
    }

    fun getSize(): Int {
        return endOffset - startOffset
    }

    private fun byteOrder(littleEndian: Boolean): ByteOrder {
        return if (littleEndian) ByteOrder.LITTLE_ENDIAN else ByteOrder.BIG_ENDIAN
    }

    private fun offset(pos: Int, size: Int): Long {
        val offset = startOffset + pos
        assert(offset + size <= endOffset)
        return offset.toLong()
    }

    private fun ordered(value: Short, littleEndian: Boolean): Short {
        if (ByteOrder.nativeOrder() !== byteOrder(littleEndian)) {
            return java.lang.Short.reverseBytes(value)
        }
        return value
    }

    private fun ordered(value: Int, littleEndian: Boolean): Int {
        if (ByteOrder.nativeOrder() !== byteOrder(littleEndian)) {
            return Integer.reverseBytes(value)
        }
        return value
    }

    private fun ordered(value: Long, littleEndian: Boolean): Long {
        if (ByteOrder.nativeOrder() !== byteOrder(littleEndian)) {
            return java.lang.Long.reverseBytes(value)
        }
        return value
    }

    private fun ordered(value: Float, littleEndian: Boolean): Float {
        if (ByteOrder.nativeOrder() !== byteOrder(littleEndian)) {
            return Float.fromBits(Integer.reverseBytes(value.toRawBits()))
        }
        return value
    }

    private fun ordered(value: Double, littleEndian: Boolean): Double {
        if (ByteOrder.nativeOrder() !== byteOrder(littleEndian)) {
            return Double.fromBits(java.lang.Long.reverseBytes(value.toRawBits()))
        }
        return value
    }

    override fun getFloat32(byteOffset: Int, littleEndian: Boolean): Float {
        val value = Platform.unsafe.getFloat(byteArray, offset(byteOffset, 4))
        return ordered(value, littleEndian)
    }

    override fun setFloat32(byteOffset: Int, value: Float, littleEndian: Boolean) {
        Platform.unsafe.putFloat(byteArray, offset(byteOffset, 4), ordered(value, littleEndian))
    }

    override fun getFloat64(byteOffset: Int, littleEndian: Boolean): Double {
        val value = Platform.unsafe.getDouble(byteArray, offset(byteOffset, 8))
        return ordered(value, littleEndian)
    }

    override fun setFloat64(byteOffset: Int, value: Double, littleEndian: Boolean) {
        Platform.unsafe.putDouble(byteArray, offset(byteOffset, 8), ordered(value, littleEndian))
    }

    override fun getInt8(byteOffset: Int): Byte {
        return Platform.unsafe.getByte(byteArray, offset(byteOffset, 1))
    }

    override fun setInt8(byteOffset: Int, value: Byte) {
        Platform.unsafe.putByte(byteArray, offset(byteOffset, 1), value)
    }

    override fun getInt16(byteOffset: Int, littleEndian: Boolean): Short {
        val value = Platform.unsafe.getShort(byteArray, offset(byteOffset, 2))
        return ordered(value, littleEndian)
    }

    override fun setInt16(byteOffset: Int, value: Short, littleEndian: Boolean) {
        Platform.unsafe.putShort(byteArray, offset(byteOffset, 2), ordered(value, littleEndian))
    }

    override fun getInt32(byteOffset: Int, littleEndian: Boolean): Int {
        val value = Platform.unsafe.getInt(byteArray, offset(byteOffset, 4))
        return ordered(value, littleEndian)
    }

    override fun setInt32(byteOffset: Int, value: Int, littleEndian: Boolean) {
        Platform.unsafe.putInt(byteArray, offset(byteOffset, 4), ordered(value, littleEndian))
    }

    override fun getInt64(byteOffset: Int, littleEndian: Boolean): Int64 {
        val value = Platform.unsafe.getLong(byteArray, offset(byteOffset, 8))
        return longToInt64(ordered(value, littleEndian))
    }

    override fun setInt64(byteOffset: Int, value: Int64, littleEndian: Boolean) {
        check(value is JvmInt64)
        Platform.unsafe.putLong(byteArray, offset(byteOffset, 8), ordered(value.toLong(), littleEndian))
    }
}