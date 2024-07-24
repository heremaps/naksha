package naksha.base

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.reflect.KClass

/**
 * A data-view that allows to read and mutate the bytes of a byte-array.
 */
@OptIn(ExperimentalJsExport::class)
@JsExport
@JsName("DataView")
interface PlatformDataView : PlatformObject {
    /**
     * Create a proxy or return the existing proxy.
     * @param klass the proxy class.
     * @return the proxy instance.
     * @throws IllegalArgumentException if this is no [PlatformMap], [PlatformList] or [PlatformMap].
     */
    fun <T : Proxy> proxy(@Suppress("NON_EXPORTABLE_TYPE") klass: KClass<T>): T
}
