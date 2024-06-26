package naksha.base

import naksha.base.PlatformUtil.Companion.defaultDataViewSize
import naksha.base.Platform.Companion.newDataView
import naksha.base.PlatformDataViewApi.Companion.dataview_get_size
import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * The Naksha type for a data view.
 * @property binary The binary editor being used to modify the underlying [PlatformDataView].
 */
@Suppress("OPT_IN_USAGE")
@JsExport
open class DataViewProxy(internal val binary: Binary = Binary()) : Proxy(), BinaryView by binary {

    /**
     * Create a new view with a new byte array of the given size backing it.
     * @param size The amount of byte to allocate.
     */
    @Suppress("LeakingThis")
    @JsName("forSize")
    constructor(size: Int = defaultDataViewSize) : this(Binary()) {
        bind(newDataView(ByteArray(size)), Symbols.of(this::class))
    }

    /**
     * Creates a new view about the given byte-array.
     * @param byteArray The byte-array to view.
     * @param offset The first byte to view; if _null_ index `0` is used.
     * @param length The amount of byte to view; if _null_, everything from [offset] to the end of the [byteArray] is mapped.
     */
    @Suppress("LeakingThis")
    @JsName("forUint8Array")
    constructor(byteArray: ByteArray, offset: Int? = null, length: Int? = null) : this(Binary()) {
        val off = offset ?: 0
        val len = length ?: (byteArray.size - off)
        bind(newDataView(byteArray, off, len), Symbols.of(this::class))
    }

    override fun bind(data: PlatformObject, symbol: Symbol) {
        require(data is PlatformDataView)
        binary.view = data
        binary.resize = false
        binary.pos = 0
        binary.end = dataview_get_size(data)
        super.bind(data, symbol)
    }
    override fun data(): PlatformDataView = super.data() as PlatformDataView
    override fun createData(): PlatformDataView = newDataView(ByteArray(defaultDataViewSize))
}