package com.here.naksha.lib.base

import java.nio.ByteOrder

/**
 * The JVM implementation of [PDataView].
 * @param byteArray The byte-array to map.
 * @param offset The offset of the first byte
 * @param length The amount of byte to map.
 */
open class JvmPDataView(byteArray: ByteArray, offset: Int = 0, length: Int = byteArray.size - offset) : JvmObject(), PDataView {
    private val buffer: ByteArray = byteArray
    private val startOffset: Int = Base.baseOffset + offset
    private val endOffset: Int = Base.baseOffset + startOffset + length

    override fun getByteArray(): ByteArray {
        return buffer
    }

    override fun getStart(): Int {
        return startOffset - Base.baseOffset
    }

    override fun getEnd(): Int {
        return endOffset - Base.baseOffset
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
        val value = Base.unsafe.getFloat(buffer, offset(pos,4))
        return ordered(value, littleEndian)
    }

    override fun setFloat32(pos: Int, value: Float, littleEndian: Boolean) {
        Base.unsafe.putFloat(buffer, offset(pos, 4), ordered(value, littleEndian))
    }

    override fun getFloat64(pos: Int, littleEndian: Boolean): Double {
        val value = Base.unsafe.getDouble(buffer, offset(pos, 8))
        return ordered(value, littleEndian)
    }

    override fun setFloat64(pos: Int, value: Double, littleEndian: Boolean) {
        Base.unsafe.putDouble(buffer, offset(pos, 8), ordered(value, littleEndian))
    }

    override fun getInt8(pos: Int): Byte {
        return Base.unsafe.getByte(buffer, offset(pos, 1))
    }

    override fun setInt8(pos: Int, value: Byte) {
        Base.unsafe.putByte(buffer, offset(pos, 1), value)
    }

    override fun getInt16(pos: Int, littleEndian: Boolean): Short {
        val value = Base.unsafe.getShort(buffer, offset(pos, 2))
        return ordered(value, littleEndian)
    }

    override fun setInt16(pos: Int, value: Short, littleEndian: Boolean) {
        Base.unsafe.putShort(buffer, offset(pos, 2), ordered(value, littleEndian))
    }

    override fun getInt32(pos: Int, littleEndian: Boolean): Int {
        val value = Base.unsafe.getInt(buffer, offset(pos, 4))
        return ordered(value, littleEndian)
    }

    override fun setInt32(pos: Int, value: Int, littleEndian: Boolean) {
        Base.unsafe.putInt(buffer, offset(pos, 4), ordered(value, littleEndian))
    }

    override fun getInt64(pos: Int, littleEndian: Boolean): Int64 {
        val value = Base.unsafe.getLong(buffer, offset(pos, 8))
        return JvmInt64(ordered(value, littleEndian))
    }

    override fun setInt64(pos: Int, value: Int64, littleEndian: Boolean) {
        check(value is JvmInt64)
        Base.unsafe.putLong(buffer, offset(pos, 8), ordered(value.toLong(), littleEndian))
    }
}