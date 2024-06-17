package naksha.base

import naksha.base.PlatformUtil.Companion.defaultDataViewSize
import naksha.base.Platform.Companion.newDataView
import naksha.base.PlatformDataViewApi.Companion.dataview_get_byte_array
import naksha.base.PlatformDataViewApi.Companion.dataview_get_float32
import naksha.base.PlatformDataViewApi.Companion.dataview_get_float64
import naksha.base.PlatformDataViewApi.Companion.dataview_get_int16
import naksha.base.PlatformDataViewApi.Companion.dataview_get_int32
import naksha.base.PlatformDataViewApi.Companion.dataview_get_int64
import naksha.base.PlatformDataViewApi.Companion.dataview_get_int8
import naksha.base.PlatformDataViewApi.Companion.dataview_get_size
import naksha.base.PlatformDataViewApi.Companion.dataview_get_start
import naksha.base.PlatformDataViewApi.Companion.dataview_set_float32
import naksha.base.PlatformDataViewApi.Companion.dataview_set_float64
import naksha.base.PlatformDataViewApi.Companion.dataview_set_int16
import naksha.base.PlatformDataViewApi.Companion.dataview_set_int32
import naksha.base.PlatformDataViewApi.Companion.dataview_set_int64
import naksha.base.PlatformDataViewApi.Companion.dataview_set_int8
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmStatic
import kotlin.math.min

/**
 * A class to read or modify binary data stored in a byte-array. The class operates either directly on a [PlatformDataView].
 * The [PlatformDataView] maps a [ByteArray] that should be read and/or modified. The [byteLength] is basically the capacity
 * available for reading and writing.
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
    @JsName("forSize")
    constructor(size: Int) : this() {
        view = newDataView(ByteArray(size))
        this.readOnly = false
        this.resize = true
    }

    /**
     * Creates a new read-only about the given data-view.
     * @param view The view for which to create a proxy.
     * @param pos The position in the view to start reading; defaults to `0`.
     * @param end The position in the view to stop reading at (first position to **not** read); defaults to `view.byteLength`.
     */
    @Suppress("LeakingThis")
    @JsName("forDataView")
    constructor(view: PlatformDataView, pos: Int = 0, end: Int = dataview_get_size(view)) : this() {
        this.view = view
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
    @JsName("forUint8Array")
    constructor(byteArray: ByteArray, offset: Int = 0, length: Int = byteArray.size - offset) : this() {
        view = newDataView(byteArray, offset, length)
        this.pos = 0
        this.end = dataview_get_size(view)
        this.readOnly = true
        this.resize = false
    }

    @Suppress("UNUSED_PARAMETER")
    companion object {
        /**
         * The default empty byte-array.
         */
        @JvmStatic
        @JsStatic
        val EMPTY_BYTE_ARRAY = ByteArray(0)

        /**
         * The default empty [PlatformDataView] using the [EMPTY_BYTE_ARRAY].
         */
        @JvmStatic
        @JsStatic
        val EMPTY_PLATFORM_VIEW = newDataView(EMPTY_BYTE_ARRAY)

        /**
         * A special instance that represents an immutable empty binary using the [EMPTY_PLATFORM_VIEW] and [EMPTY_BYTE_ARRAY].
         */
        @JvmStatic
        @JsStatic
        var EMPTY_IMMUTABLE = object : Binary() {
            override var byteArray = EMPTY_BYTE_ARRAY
                set(value) = throw UnsupportedOperationException()
            override var view = EMPTY_PLATFORM_VIEW
                set(value) = throw UnsupportedOperationException()
            override var readOnly = false
                set(value) {
                    require(!value)
                    field = value
                }
            override var resize = false
                set(value) {
                    require(!value)
                    field = value
                }
        }
    }

    override var byteArray: ByteArray
        get() = dataview_get_byte_array(view)
        set(value) {
            if (value !== dataview_get_byte_array(view)) {
                check(!readOnly && resize)
                view = newDataView(value)
            }
        }
    override var view: PlatformDataView = EMPTY_PLATFORM_VIEW
        set(value) {
            if (value !== field) {
                check(!readOnly && resize)
                field = value
                if (end > byteLength) end = byteLength
                if (pos > byteLength) pos = byteLength
            }
        }
    override val byteOffset: Int
        get() = dataview_get_start(view)
    override val byteLength: Int
        get() = dataview_get_size(view)
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
                    resizeTo(min((value * 1.5).toInt(), defaultDataViewSize))
                }
                field = value
            }
        }

    override var byteAvailable: Int
        get() = byteLength - end
        set(value) {
            val available = byteLength - end
            if (value > available) {
                check(!readOnly && resize)
                resizeTo(min(((byteLength - available + value) * 1.2).toInt(), defaultDataViewSize))
            }
        }

    override fun reset(): Int {
        val old = end
        pos = 0
        end = 0
        return old
    }

    override fun resizeTo(newSize: Int) {
        val byteLength = this.byteLength
        if (newSize != byteLength) {
            check(!readOnly && resize)
            Platform.logger.atDebug {
                val op = if (newSize < byteLength) "Shrink" else "Expand"
                "$op view from $byteLength to $newSize"
            }
            view = newDataView(byteArray.copyOf(newSize))
        }
    }

    override fun prepareRead(pos: Int, bytes: Int) {
        require(pos >= 0)
        require(bytes >= 1)
        val newEnd = pos + bytes
        if (newEnd > end) throw IndexOutOfBoundsException()
    }

    override fun prepareWrite(pos: Int, bytes: Int, resize: Boolean) {
        require(pos >= 0)
        require(bytes >= 1)
        check(!readOnly)
        val end = pos + bytes
        if (end > byteLength) {
            if (resize) byteAvailable = bytes else throw IndexOutOfBoundsException()
        }
    }

    override fun getFloat32(pos: Int, littleEndian: Boolean): Float {
        prepareRead(pos, 4)
        return dataview_get_float32(view, pos, littleEndian)
    }

    override fun setFloat32(pos: Int, value: Float, littleEndian: Boolean) {
        prepareWrite(pos, 4)
        dataview_set_float32(view, pos, value, littleEndian)
    }

    override fun readFloat32(littleEndian: Boolean): Float {
        prepareRead(pos, 4)
        val v = dataview_get_float32(view, pos, littleEndian)
        pos += 4
        return v
    }

    override fun writeFloat32(value: Float, littleEndian: Boolean) {
        prepareWrite(end, 4)
        dataview_set_float32(view, end, value, littleEndian)
        end += 4
    }

    override fun getFloat64(pos: Int, littleEndian: Boolean): Double {
        prepareRead(pos, 8)
        return dataview_get_float64(view, pos, littleEndian)
    }

    override fun setFloat64(pos: Int, value: Double, littleEndian: Boolean) {
        prepareWrite(pos, 8)
        dataview_set_float64(view, pos, value, littleEndian)
    }

    override fun readFloat64(littleEndian: Boolean): Double {
        prepareRead(pos, 8)
        val v = dataview_get_float64(view, pos, littleEndian)
        pos += 8
        return v
    }

    override fun writeFloat64(value: Double, littleEndian: Boolean) {
        prepareWrite(end, 8)
        dataview_set_float64(view, end, value, littleEndian)
        end += 8
    }

    override fun getInt8(pos: Int): Byte {
        prepareRead(pos, 1)
        return dataview_get_int8(view, pos)
    }

    override fun setInt8(pos: Int, value: Byte) {
        prepareWrite(pos, 1)
        dataview_set_int8(view, pos, value)
    }

    override fun readInt8(): Byte {
        prepareRead(pos, 1)
        val v = dataview_get_int8(view, pos)
        pos += 1
        return v
    }

    override fun writeInt8(value: Byte) {
        prepareWrite(end, 1)
        dataview_set_int8(view, end, value)
        end += 1
    }

    override fun getInt16(pos: Int, littleEndian: Boolean): Short {
        prepareRead(pos, 2)
        return dataview_get_int16(view, pos, littleEndian)
    }

    override fun setInt16(pos: Int, value: Short, littleEndian: Boolean) {
        prepareWrite(pos, 2)
        dataview_set_int16(view, pos, value, littleEndian)
    }

    override fun readInt16(littleEndian: Boolean): Short {
        prepareRead(pos, 2)
        val v = dataview_get_int16(view, pos, littleEndian)
        pos += 2
        return v
    }

    override fun writeInt16(value: Short, littleEndian: Boolean) {
        prepareWrite(end, 2)
        dataview_set_int16(view, end, value, littleEndian)
        end += 2
    }

    override fun getInt32(pos: Int, littleEndian: Boolean): Int {
        prepareRead(pos, 4)
        return dataview_get_int32(view, pos, littleEndian)
    }

    override fun setInt32(pos: Int, value: Int, littleEndian: Boolean) {
        prepareWrite(pos, 4)
        dataview_set_int32(view, pos, value, littleEndian)
    }

    override fun readInt32(littleEndian: Boolean): Int {
        prepareRead(pos, 4)
        val v = dataview_get_int32(view, pos, littleEndian)
        pos += 4
        return v
    }

    override fun writeInt32(value: Int, littleEndian: Boolean) {
        prepareWrite(end, 4)
        dataview_set_int32(view, end, value, littleEndian)
        end += 4
    }

    override fun getInt64(pos: Int, littleEndian: Boolean): Int64 {
        prepareRead(pos, 8)
        return dataview_get_int64(view, pos, littleEndian)
    }

    override fun setInt64(pos: Int, value: Int64, littleEndian: Boolean) {
        prepareWrite(pos, 8)
        dataview_set_int64(view, pos, value, littleEndian)
    }

    override fun readInt64(littleEndian: Boolean): Int64 {
        prepareRead(pos, 8)
        val v = dataview_get_int64(view, pos, littleEndian)
        pos += 8
        return v
    }

    override fun writeInt64(value: Int64, littleEndian: Boolean) {
        prepareWrite(end, 8)
        dataview_set_int64(view, end, value, littleEndian)
        end += 8
    }

}