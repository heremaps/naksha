@file:Suppress("OPT_IN_USAGE")

package com.here.naksha.lib.base

import com.here.naksha.lib.base.Platform.Companion.isNil
import com.here.naksha.lib.base.Platform.Companion.isProxyKlass
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
        fun <E : Any> box(raw: Any?, klass: KClass<out E>, alternative: E? = null): E? {
            val data = unbox(raw)
            if (isNil(data)) return alternative
            if (klass.isInstance(raw)) return raw as E
            // TODO: Fix this, re-introduce the proxy method!
            //if (isProxyKlass(klass)) return Platform.proxy(raw, klass as KClass<Proxy>) as E
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
            symbol = Symbols.symbolOf(this::class)
            this.symbol = symbol
        }
        return symbol
    }
}