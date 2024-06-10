package naksha.base

import naksha.base.PlatformDataViewApi.Companion.dataview_get_byte_array
import naksha.base.PlatformDataViewApi.Companion.dataview_get_end
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
import kotlin.reflect.KClass

/**
 * The Naksha type for a data view.
 */
@Suppress("OPT_IN_USAGE")
@JsExport
open class P_DataView() : Proxy() {
    /**
     * Create a new view with a new byte array of the given size backing it.
     * @param size The amount of byte to allocate; if _null_, a default size is used.
     */
    @Suppress("LeakingThis")
    @JsName("new")
    constructor(size: Int? = null) : this() {
        bind(Platform.newDataView(ByteArray(size ?: 1024)), Symbols.of(this::class))
    }

    /**
     * Creates a new view about the given byte-array.
     * @param byteArray The byte-array to view.
     * @param offset The first byte to view; if _null_ index `0` is used.
     * @param length The amount of byte to view; if _null_, everything from [offset] to the end of the [byteArray] is mapped.
     */
    @Suppress("LeakingThis")
    @JsName("of")
    constructor(byteArray: ByteArray, offset: Int? = null, length: Int? = null) : this() {
        val off = offset ?: 0
        val len = length ?: (byteArray.size - off)
        bind(Platform.newDataView(byteArray, off, len), Symbols.of(this::class))
    }

    override fun bind(data: PlatformObject, symbol: Symbol) {
        require(data is PlatformDataView)
        super.bind(data, symbol)
    }
    override fun data(): PlatformDataView = super.data() as PlatformDataView
    override fun createData(): PlatformDataView = Platform.newDataView(ByteArray(1024))

    /**
     * Returns the byte-array below the view.
     */
    fun getByteArray(): ByteArray = dataview_get_byte_array(data())

    /**
     * Returns the offset in the underlying byte-array where the view starts.
     */
    fun getStart(): Int = dataview_get_start(data())

    /**
     * Returns the offset in the underlying byte-array where the view end, so the offset that must **not** be read.
     */
    fun getEnd(): Int = dataview_get_end(data())

    /**
     * Returns the amount of byte being available in the view.
     */
    fun getSize(): Int = dataview_get_size(data())

    fun getFloat32(pos: Int, littleEndian: Boolean = false): Float = dataview_get_float32(data(), pos, littleEndian)
    fun setFloat32(pos: Int, value: Float, littleEndian: Boolean = false) = dataview_set_float32(data(), pos, value, littleEndian)
    fun getFloat64(pos: Int, littleEndian: Boolean = false): Double = dataview_get_float64(data(), pos, littleEndian)
    fun setFloat64(pos: Int, value: Double, littleEndian: Boolean = false) = dataview_set_float64(data(), pos, value, littleEndian)

    fun getInt8(pos: Int): Byte = dataview_get_int8(data(), pos)
    fun setInt8(pos: Int, value: Byte) = dataview_set_int8(data(), pos, value)
    fun getInt16(pos: Int, littleEndian: Boolean = false): Short = dataview_get_int16(data(), pos, littleEndian)
    fun setInt16(pos: Int, value: Short, littleEndian: Boolean = false) = dataview_set_int16(data(), pos, value, littleEndian)
    fun getInt32(pos: Int, littleEndian: Boolean = false): Int = dataview_get_int32(data(), pos, littleEndian)
    fun setInt32(pos: Int, value: Int, littleEndian: Boolean = false) = dataview_set_int32(data(), pos, value, littleEndian)
    fun getInt64(pos: Int, littleEndian: Boolean = false): Int64 = dataview_get_int64(data(), pos, littleEndian)
    fun setInt64(pos: Int, value: Int64, littleEndian: Boolean = false) = dataview_set_int64(data(), pos, value, littleEndian)
}