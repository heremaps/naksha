package com.here.naksha.lib.base

import java.nio.ByteOrder

/**
 * The JVM implementation of [PlatformDataViewApi].
 * @param byteArray The byte-array to map.
 * @param offset The offset of the first byte
 * @param length The amount of byte to map.
 */
open class JvmNativeDataView(byteArray: ByteArray, offset: Int = 0, length: Int = byteArray.size - offset) : JvmObject(), PlatformDataViewApi {
    private val buffer: ByteArray = byteArray
    private val startOffset: Int = Platform.baseOffset + offset
    private val endOffset: Int = Platform.baseOffset + startOffset + length

    override fun getByteArray(): ByteArray {
        return buffer
    }

    override fun getStart(): Int {
        return startOffset - Platform.baseOffset
    }

    override fun getEnd(): Int {
        return endOffset - Platform.baseOffset
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
        val value = Platform.unsafe.getFloat(buffer, offset(pos,4))
        return ordered(value, littleEndian)
    }

    override fun setFloat32(pos: Int, value: Float, littleEndian: Boolean) {
        Platform.unsafe.putFloat(buffer, offset(pos, 4), ordered(value, littleEndian))
    }

    override fun getFloat64(pos: Int, littleEndian: Boolean): Double {
        val value = Platform.unsafe.getDouble(buffer, offset(pos, 8))
        return ordered(value, littleEndian)
    }

    override fun setFloat64(pos: Int, value: Double, littleEndian: Boolean) {
        Platform.unsafe.putDouble(buffer, offset(pos, 8), ordered(value, littleEndian))
    }

    override fun getInt8(pos: Int): Byte {
        return Platform.unsafe.getByte(buffer, offset(pos, 1))
    }

    override fun setInt8(pos: Int, value: Byte) {
        Platform.unsafe.putByte(buffer, offset(pos, 1), value)
    }

    override fun getInt16(pos: Int, littleEndian: Boolean): Short {
        val value = Platform.unsafe.getShort(buffer, offset(pos, 2))
        return ordered(value, littleEndian)
    }

    override fun setInt16(pos: Int, value: Short, littleEndian: Boolean) {
        Platform.unsafe.putShort(buffer, offset(pos, 2), ordered(value, littleEndian))
    }

    override fun getInt32(pos: Int, littleEndian: Boolean): Int {
        val value = Platform.unsafe.getInt(buffer, offset(pos, 4))
        return ordered(value, littleEndian)
    }

    override fun setInt32(pos: Int, value: Int, littleEndian: Boolean) {
        Platform.unsafe.putInt(buffer, offset(pos, 4), ordered(value, littleEndian))
    }

    override fun getInt64(pos: Int, littleEndian: Boolean): Int64 {
        val value = Platform.unsafe.getLong(buffer, offset(pos, 8))
        return JvmInt64(ordered(value, littleEndian))
    }

    override fun setInt64(pos: Int, value: Int64, littleEndian: Boolean) {
        check(value is JvmInt64)
        Platform.unsafe.putLong(buffer, offset(pos, 8), ordered(value.toLong(), littleEndian))
    }
}