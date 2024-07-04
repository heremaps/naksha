@file:Suppress("OPT_IN_USAGE")

package naksha.base

import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.reflect.KClass

/**
 * The platform object is any object that allows to apply proxies. All platform objects can store symbols, which will not
 * be serialized and are hidden properties of objects.
 */
@JsExport
@JsName("Object")
interface PlatformObject {
    /**
     * Create a proxy or return the existing proxy. If a proxy of a not compatible type exists already and [doNotOverride]
     * is _true_, the method will throw an [IllegalStateException]; otherwise the current type is simply overridden.
     * @param klass The proxy class.
     * @return The proxy instance.
     */
    @Suppress("NON_EXPORTABLE_TYPE")
    fun <T : Proxy> proxy(klass: KClass<T>): T
}