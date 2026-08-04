package naksha.base

import kotlin.reflect.KClass

@JsExport
actual interface IProxyable {
    @Suppress("NON_EXPORTABLE_TYPE")
    actual fun <T : AbstractProxy> proxy(klass: KClass<T>): T

    /**
     * Create a proxy or return an existing proxy linked to this object.
     *
     * **This method is thread safe.**
     * @param constructor The constructor of the proxy to query.
     * @return The proxy instance.
     * @since 3.0
     * @see AbstractProxy.proxy
     */
    @JsName("proxyJs")
    fun <T : AbstractProxy> proxy(constructor: JsClass<T>): T
}