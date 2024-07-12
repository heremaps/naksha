@file:Suppress("OPT_IN_USAGE")

package naksha.base

import naksha.base.Platform.isNil
import naksha.base.fn.Fn0
import naksha.base.fn.Fn1
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmStatic
import kotlin.reflect.KClass

/**
 * The base class for proxy types bound to [PlatformObject], [PlatformList], [PlatformMap], or [PlatformDataViewApi].
 */
@Suppress("NON_EXPORTABLE_TYPE")
@JsExport
abstract class Proxy : PlatformObject {
    companion object {
        @Suppress("UNCHECKED_CAST")
        private fun <T : Any> proxyOf(data: PlatformObject, klass: KClass<out T>): T {
            if (klass.isInstance(data)) return data as T
            val symbol = Symbols.of(klass)
            val existing = Symbols.get(data, symbol)
            if (klass.isInstance(existing)) return existing as T
            // Create a new instance.
            val instance = Platform.newInstanceOf(klass)
            (instance as Proxy).bind(data, symbol)
            return instance
        }

        /**
         * Convert the given raw value into a value of the given type.
         * @param raw the raw value to convert.
         * @param alternative the alternative to return, when the raw value can't be converted.
         * @param init the initializer, when the raw value can't be converted, preferred above [alternative] if given.
         * @return the given raw as given type, the result of [init] or the given [alternative] (in that order).
         */
        @Suppress("UNCHECKED_CAST")
        @JvmStatic
        @JsStatic
        fun <T : Any> box(raw: Any?, klass: KClass<out T>, alternative: T? = null, init: Fn0<out T?>? = null): T? {
            val data = unbox(raw)
            if (isNil(data)) return if (init != null) init.call() else alternative
            // The data value is a complex object
            if (!Platform.isScalar(data) && data is PlatformObject) {
                // If a proxy is requested.
                if (Platform.isProxyKlass(klass)) return proxyOf(data, klass)
                // A scalar type was requested, but a complex type found.
                // The only acceptable situation is that Any was requested.
                // Then, return the standard types.
                if (klass == Any::class) {
                    if (data is PlatformMap) return data.proxy(ObjectProxy::class) as T
                    if (data is PlatformList) return data.proxy(AnyListProxy::class) as T
                    if (data is PlatformDataView) return data.proxy(DataViewProxy::class) as T
                }
            } else if (klass.isInstance(data)) return data as T
            return if (init != null) init.call() else alternative
        }

        /**
         * Convert the given raw value into a value of the given type.
         * @param raw the raw value to convert.
         * @param key the key to forward to the [init] function.
         * @param init the initializer, when the raw value can't be converted, preferred above [alternative] if given.
         * @return the given raw as given type, the result of [init] or the given [alternative] (in that order).
         */
        @Suppress("UNCHECKED_CAST")
        @JvmStatic
        @JsStatic
        fun <T : Any, K : Any> boxPair(
            raw: Any?,
            klass: KClass<out T>,
            key: K,
            init: Fn1<out T?, in K>
        ): T? {
            val data = unbox(raw)
            if (isNil(data)) return init.call(key)
            // The data value is a complex object
            if (!Platform.isScalar(data) && data is PlatformObject) {
                // If a proxy is requested.
                if (Platform.isProxyKlass(klass)) return proxyOf(data, klass)
                // A scalar type was requested, but a complex type found.
                // The only acceptable situation is that Any was requested.
                // Then, return the standard types.
                if (klass == Any::class) {
                    if (data is PlatformMap) return data.proxy(ObjectProxy::class) as T
                    if (data is PlatformList) return data.proxy(AnyListProxy::class) as T
                    if (data is PlatformDataView) return data.proxy(DataViewProxy::class) as T
                }
            } else if (klass.isInstance(data)) return data as T
            return init.call(key)
        }

        /**
         * Unboxes the given object so that the underlying native value is returned.
         * @param value The object to unbox.
         * @return The unboxed value.
         */
        @JvmStatic
        @JsStatic
        fun unbox(value: Any?): Any? = Platform.unbox(value)
    }

    /**
     * The symbol though which this proxy is linked to the native object.
     */
    private var symbol: Symbol? = null

    /**
     * The native object to which this type is linked.
     */
    internal var data: PlatformObject? = null

    /**
     * A helper method that creates a new data object.
     */
    protected abstract fun createData(): PlatformObject // TODO: Rename to createPlatformObject

    /**
     * Binds this proxy to the given native object and symbol, normally only invoke from [Platform]. This method should only be invoked ones,
     * it will throw an [IllegalStateException] otherwise. It can be overloaded by extending types to perform late initialization. It is
     * guaranteed that the method is invoked at least ones in the lifetime of a proxy.
     * @param data The native object to which to bind.
     * @param symbol The symbol to which to bind.
     * @throws IllegalArgumentException If the given data object is not of the expected type, for example when only [PlatformMap] can be bound.
     * @throws IllegalStateException If the proxy is already linked.
     */
    open fun bind(data: PlatformObject, symbol: Symbol) {
        check(this.data == null)
        this.data = data
        this.symbol = symbol
        Symbols.set(data, symbol, this)
    }

    /**
     * Tests if this proxy is bound to an underlying object.
     * @return _true_ if the proxy is bound; _false_ otherwise.
     */
    fun isBound(): Boolean = data != null

    /**
     * Returns the platform object to which this proxy is bound via the [symbol].
     * - Java: `JvmList`, `JvmMap` or `JvmDataView`
     * - JavaScript: `Array`, `Map` or `DataView`
     */
    open fun platformObject(): PlatformObject {
        var data = this.data
        if (data == null) {
            data = createData()
            bind(data, symbol())
        }
        return data
    }

    /**
     * Returns the symbol through which this proxy is bound to the [platformObject] object.
     */
    fun symbol(): Symbol {
        var symbol = this.symbol
        if (symbol == null) {
            symbol = Symbols.of(this::class)
            this.symbol = symbol
        }
        return symbol
    }

    /**
     * Create a proxy or return the existing proxy. If a proxy of a not compatible type exists already and [doNotOverride]
     * is _true_, the method will throw an _IllegalStateException_; otherwise the current type is simply overridden.
     * @param klass The proxy class.
     * @return The proxy instance.
     */
    fun <T : Proxy> proxy(klass: KClass<T>): T = Platform.proxy(platformObject(), klass)

    override fun equals(other: Any?): Boolean {
        Platform.logger.info("Proxy::equals")
        if (this === other) return true
        if (other is PlatformObject) return platformObject() == other
        if (other is Proxy) return platformObject() == other.platformObject()
        Platform.logger.info("Proxy::equals -> false")
        return false
    }
}
