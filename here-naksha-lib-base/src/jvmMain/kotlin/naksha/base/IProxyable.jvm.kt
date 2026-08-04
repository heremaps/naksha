package naksha.base

import kotlin.reflect.KClass

actual interface IProxyable {
    actual fun <T : AbstractProxy> proxy(klass: KClass<T>): T

    /**
     * Create a proxy or return an existing proxy linked to this object.
     *
     * **This method is thread safe.**
     * @param javaClass The Java class of the proxy to query.
     * @return The proxy instance.
     * @since 3.0
     * @see AbstractProxy.proxy
     */
    fun <T : AbstractProxy> proxy(javaClass: Class<T>): T
}