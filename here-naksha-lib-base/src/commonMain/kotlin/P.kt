@file:Suppress("OPT_IN_USAGE")

package com.here.naksha.lib.base

import kotlin.js.JsExport

/**
 * The base class for proxy types. A proxy type refers to a native object and is bound to the native object using a symbol. The proxy
 * itself is a native object.
 * @param <DATA> The native type, being one of: [N_Object], [N_Array], [N_Map], [N_ConcurrentMap] or [N_DataView].
 */
@JsExport
abstract class P : N_Object {
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
     * @return The member value or [N.undefined] if no such member exist.
     */
    protected fun getMember(key: Symbol): Any? = N.getMember(data(), key)

    /**
     * Sets the value of a protected member field, stored with the underlying native object.
     * @param key The key of the member.
     * @param value The value to assign, if being [N.undefined], then the member is removed.
     * @return The previously assigned member value; [N.undefined] if no such member existed.
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
     * @return The value being removed; [N.undefined] if no such member existed.
     */
    protected fun removeMember(key: Symbol): Any? = N.setMember(data(), key, N.undefined)

    /**
     * Returns the value of a property, stored with the underlying native object.
     * @param key The key of the property.
     * @return The property value or [N.undefined] if no such property exist.
     */
    protected fun getProperty(key: String): Any? = N.get(data(), key)

    /**
     * Sets the value of a property, stored with the underlying native object.
     * @param key The key of the property.
     * @param value The value to assign, if being [N.undefined], then the property is removed.
     * @return The previously assigned property value; [N.undefined] if no such property existed.
     */
    protected fun setProperty(key: String, value: Any?): Any? = N.set(data(), key, value)

    /**
     * Tests if the property exists, stored with the underlying native object.
     * @param key The key of the property.
     * @return _true_ if the property exists; _false_ otherwise.
     */
    protected fun hasProperty(key: String): Boolean = N.has(data(), key)

    /**
     * Removes the property, stored with the underlying native object.
     * @param key The key of the property.
     * @return The value being removed; [N.undefined] if no such property existed.
     */
    protected fun removeProperty(key: String): Any? = N.remove(data(), key)
}