package naksha.base

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.reflect.KClass

/**
 * A data-view that allows to read and mutate the bytes of a byte-array.
 */
@OptIn(ExperimentalJsExport::class)
@Suppress("NON_EXPORTABLE_TYPE")
@JsExport
@JsName("DataView")
interface PlatformDataView : PlatformObject {
    /**
     * Create a proxy or return the existing proxy. If a proxy of a not compatible type exists already and [doNotOverride]
     * is _true_, the method will throw an _IllegalStateException_; otherwise the current type is simply overridden.
     * @param <T> The type to proxy, must extend [Proxy].
     * @param klass The proxy class.
     * @param doNotOverride If _true_, do not override existing symbols bound to incompatible types, but throw an [IllegalStateException]
     * @return The proxy instance.
     * @throws IllegalStateException If [doNotOverride] is _true_ and the symbol is already bound to an incompatible type.
     */
    fun <T : P_DataView> proxy(klass: KClass<out T>, doNotOverride: Boolean = false): T
}
