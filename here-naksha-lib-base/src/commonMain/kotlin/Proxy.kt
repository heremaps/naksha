@file:Suppress("OPT_IN_USAGE")

package com.here.naksha.lib.base

import kotlin.DeprecationLevel.ERROR
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmStatic
import kotlin.reflect.KClass

/**
 * The base class for proxy types bound to [N_Object], [N_Array], [N_Map], [N_ConcurrentMap] or [N_DataView]. Note that
 * native objects have generally all members and attributes. Generally members are only used to bind proxies. Attributes
 * are technically support, but need to be discussed, if we want to support them and for what purpose. Since the introduction
 * of [N_Map] the should not be needed anymore.
 */
@Suppress("NON_EXPORTABLE_TYPE")
@JsExport
abstract class Proxy : N_Object {
    companion object {
        /**
         * Convert the given raw value into a value of the given type.
         * @param <T> The type to convert into.
         * @param raw The raw value to convert.
         * @param alternative The alternative to return when the raw can't be converted.
         * @return The given raw as given type or the [alternative].
         */
        @Suppress("UNCHECKED_CAST")
        @JvmStatic
        @JsStatic
        fun <T : Any> convert(raw: Any?, klass: KClass<out T>, alternative: T? = null): T? {
            val data = N.unbox(raw)
            if (N.isNil(data)) return alternative
            if (klass.isInstance(raw)) return raw as T
            if (N.isProxyKlass(klass)) return N.proxy(raw, klass as KClass<Proxy>) as T
            return alternative
        }

        /**
         * Returns the raw value converted to the given type. If the convert fails, a new instance of the type is
         * created and returned.
         * @param <T> The type to convert into.
         * @param raw The raw value to convert.
         * @param klass The [KClass] of the type.
         * @return The value.
         */
        @JvmStatic
        @JsStatic
        fun <T : Any> convertOrCreate(raw: Any?, klass: KClass<out T>): T = convert(raw, klass, null) ?: N.newInstanceOf(klass)

    }

    /**
     * The symbol though which this proxy is linked to the native object.
     */
    private var symbol: Symbol? = null

    /**
     * The native object to which this type is linked.
     */
    private var data: N_Object? = null

    /**
     * A helper method that creates a new data object.
     */
    protected abstract fun createData(): N_Object

    /**
     * Binds this proxy to the given native object and symbol, normally only invoke from [N]. This method should only be invoked ones,
     * it will throw an [IllegalStateException] otherwise. It can be overloaded by extending types to perform late initialization. It is
     * guaranteed that the method is invoked at least ones in the lifetime of a proxy.
     * @param data The native object to which to bind.
     * @param symbol The symbol to which to bind.
     * @throws IllegalArgumentException If the given data object is not of the expected type, for example when only [N_Map] can be bound.
     * @throws IllegalStateException If the proxy is already linked.
     */
    open fun bind(data: N_Object, symbol: Symbol) {
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
    open fun data(): N_Object {
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
            symbol = N.symbolOf(this::class)
            this.symbol = symbol
        }
        return symbol
    }

    /**
     * Returns the value of a member field, stored with the underlying native object.
     * @param key The key of the member.
     * @return The member value or _undefined_ if no such member exist.
     */
    protected fun getMember(key: Symbol): Any? = N.getMember(data(), key)

    /**
     * Sets the value of a protected member field, stored with the underlying native object.
     * @param key The key of the member.
     * @param value The value to assign, if being _undefined_, then the member is removed.
     * @return The previously assigned member value; _undefined_ if no such member existed.
     */
    protected fun setMember(key: Symbol, value: Any?): Any? = N.setMember(data(), key, value)

    /**
     * Tests if the protected member exists.
     * @param key The key of the member.
     * @return _true_ if the member exists; _false_ otherwise.
     */
    protected fun hasMember(key: Symbol): Boolean = N.isUndefined(N.getMember(data(), key))

    /**
     * Removes the protected member.
     * @param key The key of the member.
     * @return The value being removed; _undefined_ if no such member existed.
     */
    protected fun removeMember(key: Symbol): Any? = N.setMember(data(), key, N.undefinedOf(N.anyKlass))

    /**
     * Returns the value of an attribute, stored with the underlying native object.
     * @param key The key of the attribute.
     * @return The attribute value or _undefined_ if no such attribute exist.
     */
    @Deprecated("To be discussed if we want to support this at all!", ReplaceWith(""), ERROR)
    protected fun getAttribute(key: String): Any? = N.get(data(), key)

    /**
     * Sets the value of an attribute, stored with the underlying native object.
     * @param key The key of the attribute.
     * @param value The value to assign, if being _undefined_, then the attribute is removed.
     * @return The previously assigned attribute value; _undefined_ if no such attribute existed.
     */
    @Deprecated("To be discussed if we want to support this at all!", ReplaceWith(""), ERROR)
    protected fun setAttribute(key: String, value: Any?): Any? = N.set(data(), key, value)

    /**
     * Tests if the attribute exists, stored with the underlying native object.
     * @param key The key of the attribute.
     * @return _true_ if the attribute exists; _false_ otherwise.
     */
    @Deprecated("To be discussed if we want to support this at all!", ReplaceWith(""), ERROR)
    protected fun hasAttribute(key: String): Boolean = N.has(data(), key)

    /**
     * Removes the attribute, stored with the underlying native object.
     * @param key The key of the attribute.
     * @return The value being removed; _undefined_ if no such attribute existed.
     */
    @Deprecated("To be discussed if we want to support this at all!", ReplaceWith(""), ERROR)
    protected fun removeAttribute(key: String): Any? = N.remove(data(), key)
}