package naksha.base

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.reflect.KClass

/**
 * A not thread safe list, where values may be _null_, but not _undefined_.
 */
@OptIn(ExperimentalJsExport::class)
@JsExport
@JsName("Array")
interface PlatformList : PlatformObject {
    /**
     * Create a proxy or return the existing proxy.
     * @param klass the proxy class.
     * @return the proxy instance.
     * @throws IllegalArgumentException if this is no [PlatformMap], [PlatformList] or [PlatformMap].
     */
    fun <T : Proxy> proxy(@Suppress("NON_EXPORTABLE_TYPE") klass: KClass<T>): T
}