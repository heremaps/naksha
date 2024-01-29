import com.here.naksha.lib.jbon.IDataView
import com.here.naksha.lib.jbon.JbPlatform
import java.nio.ByteOrder

class JvmDataView(val buffer: ByteArray, val startOffset: Int, val endOffset: Int) : IDataView {
    override fun getPlatform(): JbPlatform {
        return JvmPlatform
    }

    override fun getByteArray(): ByteArray {
        return buffer
    }

    override fun getStart(): Int {
        return startOffset - JvmPlatform.baseOffset
    }

    override fun getEnd(): Int {
        return endOffset - JvmPlatform.baseOffset
    }

    override fun getSize(): Int {
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

    override fun getFloat32(pos: Int, littleEndian: Boolean): Float {
        val value = JvmPlatform.unsafe.getFloat(buffer, offset(pos,4))
        return ordered(value, littleEndian)
    }

    override fun setFloat32(pos: Int, value: Float, littleEndian: Boolean) {
        JvmPlatform.unsafe.putFloat(buffer, offset(pos, 4), ordered(value, littleEndian))
    }

    override fun getFloat64(pos: Int, littleEndian: Boolean): Double {
        val value = JvmPlatform.unsafe.getDouble(buffer, offset(pos, 8))
        return ordered(value, littleEndian)
    }

    override fun setFloat64(pos: Int, value: Double, littleEndian: Boolean) {
        JvmPlatform.unsafe.putDouble(buffer, offset(pos, 8), ordered(value, littleEndian))
    }

    override fun getInt8(pos: Int): Byte {
        return JvmPlatform.unsafe.getByte(buffer, offset(pos, 1))
    }

    override fun setInt8(pos: Int, value: Byte) {
        JvmPlatform.unsafe.putByte(buffer, offset(pos, 1), value)
    }

    override fun getInt16(pos: Int, littleEndian: Boolean): Short {
        val value = JvmPlatform.unsafe.getShort(buffer, offset(pos, 2))
        return ordered(value, littleEndian)
    }

    override fun setInt16(pos: Int, value: Short, littleEndian: Boolean) {
        JvmPlatform.unsafe.putShort(buffer, offset(pos, 2), ordered(value, littleEndian))
    }

    override fun getInt32(pos: Int, littleEndian: Boolean): Int {
        val value = JvmPlatform.unsafe.getInt(buffer, offset(pos, 4))
        return ordered(value, littleEndian)
    }

    override fun setInt32(pos: Int, value: Int, littleEndian: Boolean) {
        JvmPlatform.unsafe.putInt(buffer, offset(pos, 4), ordered(value, littleEndian))
    }
}