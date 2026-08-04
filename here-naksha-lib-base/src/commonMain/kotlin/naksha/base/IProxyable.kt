package naksha.base

import kotlin.reflect.KClass

/**
 * An interface implemented by all classes that allow to bind proxies _(data model applicable at runtime)_.
 * @since 3.0
 */
expect interface IProxyable {
    /**
     * Create a proxy or return an existing proxy linked.
     *
     * **This method is thread safe.**
     * @param klass The Kotlin class of the proxy to query.
     * @return The proxy instance.
     * @since 3.0
     * @see AbstractProxy.proxy
     */
    fun <T : AbstractProxy> proxy(klass: KClass<T>): T
}