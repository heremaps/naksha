package naksha.base

import naksha.base.Base.BaseCompanion.logger
import naksha.base.PlatformDataViewApi.PlatformDataViewApiCompanion.dataview_get_byte_array
import naksha.base.PlatformDataViewApi.PlatformDataViewApiCompanion.dataview_get_size
import naksha.base.PlatformDataViewApi.PlatformDataViewApiCompanion.dataview_get_start
import naksha.base.BaseUtil.BaseUtil_C.defaultDataViewSize
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmField
import kotlin.math.max

/**
 * A class to read or modify binary data stored in a byte-array.
 *
 * @constructor The default constructor creates an empty, mutable, resizable editor.
 */
@Suppress("OPT_IN_USAGE", "MemberVisibilityCanBePrivate", "unused")
@JsExport
open class Binary() : BinaryView {
    /**
     * Create a new resizable editor with a new byte-array of the given size backing it.
     * @param size The amount of byte to allocate initially.
     */
    @Suppress("LeakingThis")
    @JsName("newBinary")
    constructor(size: Int) : this() {
        byteArray = ByteArray(size)
        viewStart = 0
        viewEnd = byteArray.size
        readOnly = false
        resize = true
    }

    /**
     * Creates a new read-only about the given data-view.
     * @param view The view for which to create a proxy.
     * @param pos The position in the view to start reading; defaults to `0`.
     * @param end The position in the view to stop reading at (first position to **not** read); defaults to `view.byteLength`.
     */
    @Suppress("LeakingThis")
    @JsName("newBinaryFromDataView")
    @Deprecated("Please directly use ByteArray")
    constructor(view: PlatformDataView, pos: Int = 0, end: Int = dataview_get_size(view)) : this() {
        byteArray = dataview_get_byte_array(view)
        viewStart = dataview_get_start(view)
        viewEnd = viewStart + dataview_get_size(view)
        this.pos = pos
        this.end = end
        this.readOnly = true
        this.resize = false
    }

    /**
     * Creates a new read-only with a new data-view about the given byte-array.
     * @param byteArray The byte-array to view.
     * @param offset The first byte to view.
     * @param length The amount of byte to view; defaults to everything from [offset] to `byteArray.size`.
     */
    @Suppress("LeakingThis")
    @JsName("newBinaryFromUint8Array")
    constructor(byteArray: ByteArray, offset: Int = 0, length: Int = byteArray.size - offset) : this() {
        this.byteArray = byteArray
        this.viewStart = offset
        this.viewEnd = offset + length
        this.pos = 0
        this.end = 0
        this.readOnly = true
        this.resize = false
    }

    @Suppress("UNUSED_PARAMETER")
    companion object BinaryCompanion {
        /**
         * The default empty byte-array.
         */
        @JvmField
        @JsStatic
        val EMPTY_BYTE_ARRAY = ByteArray(0)
    }

    override var byteArray: ByteArray = EMPTY_BYTE_ARRAY
        set(value) {
            if (value === field) return
            check(!readOnly && resize)
            field = value
        }
    private var viewStart: Int = 0
    override val byteOffset: Int
        get() = viewStart
    private var viewEnd: Int = 0
    override val byteLength: Int
        get() = viewEnd - viewStart
    override var readOnly: Boolean = false
    override var resize: Boolean = true
    override var pos: Int = 0
        set(value) {
            field = if (value <= 0) 0 else if (value >= byteLength) byteLength else value
        }
    override var end: Int = 0
        set(value) {
            if (value <= 0) {
                field = 0
            } else {
                if (value > byteLength) {
                    check(!readOnly && resize)
                    resizeTo(value)
                }
                field = value
            }
        }
    /** The offset of [pos] */
    private val offset: Int
        get() = viewStart + pos

    override var byteAvailable: Int
        get() = byteLength - end
        set(value) {
            val available = byteLength - end
            if (value > available) {
                check(!readOnly && resize)
                resizeTo(byteLength + value - available)
            }
        }

    override fun reset(): Int {
        val old = end
        pos = 0
        end = 0
        return old
    }

    override fun resizeTo(newSize: Int, exact: Boolean) {
        val byteLength = this.byteLength
        if (newSize != byteLength && (exact || newSize > byteLength)) {
            check(!readOnly && resize)
            val targetSize: Int
            if (exact) {
                targetSize = newSize
            } else if (newSize > byteLength) {
                val need = newSize - byteLength
                targetSize = byteLength + max(max(need * 1.5, byteLength * 0.25), defaultDataViewSize.toDouble()).toInt()
            } else return
            logger.atDebug {
                val op = if (newSize < byteLength) "Shrink" else "Expand"
                "$op view from $byteLength to $newSize"
            }
            byteArray = byteArray.copyOf(targetSize)
        }
    }

    override fun prepareRead(pos: Int, bytes: Int, size: Int) {
        require(pos >= 0)
        require(bytes >= 1)
        val end = pos + bytes
        if (end > byteLength || end > size) throw IndexOutOfBoundsException()
    }

    override fun prepareWrite(pos: Int, bytes: Int, resize: Boolean) {
        require(pos >= 0)
        require(bytes >= 1)
        check(!readOnly)
        val end = pos + bytes
        if (end > byteLength) {
            if (resize) resizeTo(end) else throw IndexOutOfBoundsException()
        }
    }

    override fun getFloat32(pos: Int, littleEndian: Boolean): Float {
        prepareRead(pos, 4, byteLength)
        val offset = viewStart + pos
        return if (littleEndian) byteArray.getFloat32Le(offset) else byteArray.getFloat32Be(offset)
    }

    override fun setFloat32(pos: Int, value: Float, littleEndian: Boolean) {
        prepareWrite(pos, 4)
        val offset = viewStart + pos
        if (littleEndian) byteArray.setFloat32Le(offset, value) else byteArray.setFloat32Be(offset, value)
    }

    override fun readFloat32(littleEndian: Boolean): Float {
        prepareRead(pos, 4, end)
        val offset = viewStart + pos
        val v = if (littleEndian) byteArray.getFloat32Le(offset) else byteArray.getFloat32Be(offset)
        pos += 4
        return v
    }

    override fun writeFloat32(value: Float, littleEndian: Boolean) {
        prepareWrite(end, 4)
        val offset = viewStart + end
        if (littleEndian) byteArray.setFloat32Le(offset, value) else byteArray.setFloat32Be(offset, value)
        end += 4
    }

    override fun getFloat64(pos: Int, littleEndian: Boolean): Double {
        prepareRead(pos, 8, byteLength)
        val offset = viewStart + pos
        return if (littleEndian) byteArray.getFloat64Le(offset) else byteArray.getFloat64Be(offset)
    }

    override fun setFloat64(pos: Int, value: Double, littleEndian: Boolean) {
        prepareWrite(pos, 8)
        val offset = viewStart + pos
        if (littleEndian) byteArray.setFloat64Le(offset, value) else byteArray.setFloat64Be(offset, value)
    }

    override fun readFloat64(littleEndian: Boolean): Double {
        prepareRead(pos, 8, end)
        val offset = viewStart + pos
        val v = if (littleEndian) byteArray.getFloat64Le(offset) else byteArray.getFloat64Be(offset)
        pos += 8
        return v
    }

    override fun writeFloat64(value: Double, littleEndian: Boolean) {
        prepareWrite(end, 8)
        val offset = viewStart + end
        if (littleEndian) byteArray.setFloat64Le(offset, value) else byteArray.setFloat64Be(offset, value)
        end += 8
    }

    override fun getInt8(pos: Int): Byte {
        prepareRead(pos, 1, byteLength)
        val offset = viewStart + pos
        return byteArray.getInt8(offset)
    }

    override fun setInt8(pos: Int, value: Byte) {
        prepareWrite(pos, 1)
        val offset = viewStart + pos
        byteArray.setInt8(offset, value)
    }

    override fun readInt8(): Byte {
        prepareRead(pos, 1, end)
        val offset = viewStart + pos
        val v = byteArray.getInt8(offset)
        pos += 1
        return v
    }

    override fun writeInt8(value: Byte) {
        prepareWrite(end, 1)
        val offset = viewStart + end
        byteArray.setInt8(offset, value)
        end += 1
    }

    override fun getInt16(pos: Int, littleEndian: Boolean): Short {
        prepareRead(pos, 2, byteLength)
        val offset = viewStart + pos
        return if (littleEndian) byteArray.getInt16Le(offset) else byteArray.getInt16Be(offset)
    }

    override fun setInt16(pos: Int, value: Short, littleEndian: Boolean) {
        prepareWrite(pos, 2)
        val offset = viewStart + pos
        if (littleEndian) byteArray.setInt16Le(offset, value) else byteArray.setInt16Be(offset, value)
    }

    override fun readInt16(littleEndian: Boolean): Short {
        prepareRead(pos, 2, end)
        val offset = viewStart + pos
        val v = if (littleEndian) byteArray.getInt16Le(offset) else byteArray.getInt16Be(offset)
        pos += 2
        return v
    }

    override fun writeInt16(value: Short, littleEndian: Boolean) {
        prepareWrite(end, 2)
        val offset = viewStart + end
        if (littleEndian) byteArray.setInt16Le(offset, value) else byteArray.setInt16Be(offset, value)
        end += 2
    }

    override fun getInt32(pos: Int, littleEndian: Boolean): Int {
        prepareRead(pos, 4, byteLength)
        val offset = viewStart + pos
        return if (littleEndian) byteArray.getInt32Le(offset) else byteArray.getInt32Be(offset)
    }

    override fun setInt32(pos: Int, value: Int, littleEndian: Boolean) {
        prepareWrite(pos, 4)
        val offset = viewStart + pos
        if (littleEndian) byteArray.setInt32Le(offset, value) else byteArray.setInt32Be(offset, value)
    }

    override fun readInt32(littleEndian: Boolean): Int {
        prepareRead(pos, 4, end)
        val offset = viewStart + pos
        val v = if (littleEndian) byteArray.getInt32Le(offset) else byteArray.getInt32Be(offset)
        pos += 4
        return v
    }

    override fun writeInt32(value: Int, littleEndian: Boolean) {
        prepareWrite(end, 4)
        val offset = viewStart + end
        if (littleEndian) byteArray.setInt32Le(offset, value) else byteArray.setInt32Be(offset, value)
        end += 4
    }

    override fun getInt64(pos: Int, littleEndian: Boolean): Long {
        prepareRead(pos, 8, byteLength)
        val offset = viewStart + pos
        return if (littleEndian) byteArray.getInt64Le(offset) else byteArray.getInt64Be(offset)
    }

    override fun setInt64(pos: Int, value: Long, littleEndian: Boolean) {
        prepareWrite(pos, 8)
        val offset = viewStart + pos
        if (littleEndian) byteArray.setInt64Le(offset, value) else byteArray.setInt64Be(offset, value)
    }

    override fun readInt64(littleEndian: Boolean): Long {
        prepareRead(pos, 8, end)
        val offset = viewStart + pos
        val v = if (littleEndian) byteArray.getInt64Le(offset) else byteArray.getInt64Be(offset)
        pos += 8
        return v
    }

    override fun writeInt64(value: Long, littleEndian: Boolean) {
        prepareWrite(end, 8)
        val offset = viewStart + end
        if (littleEndian) byteArray.setInt64Le(offset, value) else byteArray.setInt64Be(offset, value)
        end += 8
    }

}