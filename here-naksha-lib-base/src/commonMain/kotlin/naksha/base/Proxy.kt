@file:Suppress("OPT_IN_USAGE")

package naksha.base

import naksha.base.Platform.Companion.isNil
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmStatic
import kotlin.reflect.KClass

/**
 * The base class for proxy types bound to [PlatformObject], [PlatformList], [PlatformMap], [PlatformConcurrentMap] or
 * [PlatformDataViewApi].
 */
@Suppress("NON_EXPORTABLE_TYPE")
@JsExport
abstract class Proxy : PlatformObject {
    companion object {
        /**
         * Convert the given raw value into a value of the given type.
         * @param <E> The type to convert into.
         * @param raw The raw value to convert.
         * @param alternative The alternative to return when the raw can't be converted.
         * @return The given raw as given type or the [alternative].
         */
        @Suppress("UNCHECKED_CAST")
        @JvmStatic
        @JsStatic
        fun <T : Any> box(raw: Any?, klass: KClass<out T>, alternative: T? = null): T? {
            if (klass.isInstance(raw)) return raw as T
            val data = unbox(raw)
            if (isNil(data)) return alternative
            if (klass.isInstance(raw)) return raw as T
            if (data is PlatformObject && Platform.isProxyKlass(klass)) {
                val symbol = Symbols.of(klass)
                val existing = Symbols.get(data, symbol)
                if (klass.isInstance(existing)) return existing as T
                if (existing == null) {
                    val instance = Platform.newInstanceOf(klass)
                    Symbols.set(data, symbol, instance)
                    return instance
                }
            }
            return alternative
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
    private var data: PlatformObject? = null

    /**
     * A helper method that creates a new data object.
     */
    protected abstract fun createData(): PlatformObject

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
     * Returns the data (native) object to which this proxy is bound via the [symbol].
     */
    open fun data(): PlatformObject {
        var data = this.data
        if (data == null) {
            data = createData()
            bind(data, symbol())
        }
        return data
    }

    /**
     * Returns the symbol through which this proxy is bound to the [data] object.
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
     * @param doNotOverride If _true_, do not override existing symbols bound to incompatible types, but throw an [IllegalStateException]
     * @return The proxy instance.
     * @throws IllegalStateException If [doNotOverride] is _true_ and the symbol is already bound to an incompatible type.
     */
    fun <T : Proxy> proxy(klass: KClass<T>, doNotOverride: Boolean = false): T = Platform.proxy(data(), klass, doNotOverride)
}