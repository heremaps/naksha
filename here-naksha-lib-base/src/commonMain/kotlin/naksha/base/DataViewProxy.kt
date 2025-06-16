package naksha.base

import naksha.base.Platform.Platform_C.forKClass
import naksha.base.Platform.Platform_C.newDataView
import naksha.base.PlatformDataViewApi.PlatformDataViewApi_C.dataview_get_size
import naksha.base.PlatformUtil.PlatformUtil_C.defaultDataViewSize
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * The proxy type for a data view.
 *
 * This class helps in implementing specific binary types.
 * @property binary The binary editor being used to modify the underlying [PlatformDataView].
 * @see DataViewProxy
 * @see AnyList
 * @see AnyMap
 * @see AnyObject
 * @see AnyTypedObject
 * @see AnyTypedIdObject
 */
@Suppress("OPT_IN_USAGE")
@JsExport
open class DataViewProxy(internal val binary: Binary = Binary()) : Proxy(), BinaryView by binary {

    companion object DataViewProxy_C {
        /**
         * The [PlatformType] of [AnyObject].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(DataViewProxy::class).withPackageName(PACKAGE_NAME)

        init { initialize() }
    }

    /**
     * Create a new view with a new byte array of the given size backing it.
     * @param size The amount of byte to allocate.
     */
    @JsName("forSize")
    constructor(size: Int = defaultDataViewSize) : this(Binary()) {
        createSize = size
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
        val data = newDataView(byteArray, off, len)
        val sym = Symbols.of(TYPE)
        bind(data, sym)
    }

    override fun bind(data: PlatformObject, symbol: Symbol) {
        require(data is PlatformDataView)
        binary.view = data
        binary.resize = false
        binary.pos = 0
        binary.end = dataview_get_size(data)
        super.bind(data, symbol)
    }

    override fun platformObject(): PlatformDataView = super.platformObject() as PlatformDataView
    private var createSize: Int = defaultDataViewSize
    override fun createData(): PlatformDataView = newDataView(ByteArray(createSize))
}