@file:Suppress("OPT_IN_USAGE")

package com.here.naksha.lib.base

import kotlin.js.JsExport
import kotlin.reflect.KClass

/**
 * A map that is not thread-safe.
 */
@Suppress("NON_EXPORTABLE_TYPE")
@JsExport
abstract class P_Map<K:Any, V:Any>(val keyKlass: KClass<out K>, val valueKlass: KClass<out V>) : Proxy(), MutableMap<K, V> {

    /**
     * Convert the given value into a key.
     * @param value The value to convert.
     * @param alt The alternative to return when the value can't be cast.
     * @return The given value as key.
     */
    @Suppress("UNCHECKED_CAST")
    protected open fun toKey(value: Any?, alt: K? = null): K? {
        if (keyKlass.isInstance(value)) return value as K
        val data = N.unbox(value)
        if (N.isNil(data)) return alt
        if (keyKlass.isInstance(value)) return value as K
        if (N.isProxyKlass(keyKlass)) return N.proxy(value, keyKlass as KClass<Proxy>) as K
        return alt
    }

    /**
     * Convert the given value into a value.
     * @param value The value to convert.
     * @param alt The alternative to return when the value can't be cast.
     * @return The given value as value.
     */
    @Suppress("UNCHECKED_CAST")
    protected open fun toValue(value: Any?, alt: V? = null): V? {
        val data = N.unbox(value)
        if (N.isNil(data)) return alt
        if (valueKlass.isInstance(value)) return value as V
        if (N.isProxyKlass(valueKlass)) return N.proxy(value, valueKlass as KClass<Proxy>) as V
        return alt
    }

    override fun createData(): N_Map = N.newMap()
    override fun data(): N_Map = super.data() as N_Map

    /**
     * Returns the value of the key. If no such key exists or the value is not of the specified value type,
     * returns the given alternative.
     * @param key The key to query.
     * @param alternative The alternative to return, when the current value is not of the specified value type.
     * @return The value.
     */
    protected open fun getOr(key: K, alternative: V): V {
        val data = data()
        if (!data.contains(key)) return alternative
        return toValue(data()[key], alternative)!!
    }

    /**
     * Returns the value of the key. If no such key exists or the value is not of the specified value type,
     * creates a new value, assigns it and returns it.
     * @param key The key to query.
     * @return The value.
     */
    protected open fun getOrCreate(key: K): V {
        val data = data()
        val raw = data[key]
        var value = toValue(raw, null)
        if (value == null) {
            value = N.newInstanceOf(valueKlass)
            data[key] = N.unbox(value)
        }
        return value
    }

    override val entries: MutableSet<MutableMap.MutableEntry<K, V>>
        get() = TODO("Not yet implemented")
    override val keys: MutableSet<K>
        get() = TODO("Not yet implemented")
    override val size: Int
        get() = TODO("Not yet implemented")
    override val values: MutableCollection<V>
        get() = TODO("Not yet implemented")

    override fun clear() {
        TODO("Not yet implemented")
    }

    override fun isEmpty(): Boolean {
        TODO("Not yet implemented")
    }

    override fun remove(key: K): V? {
        TODO("Not yet implemented")
    }

    override fun putAll(from: Map<out K, V>) {
        TODO("Not yet implemented")
    }

    override fun put(key: K, value: V): V? {
        TODO("Not yet implemented")
    }

    override fun get(key: K): V? {
        TODO("Not yet implemented")
    }

    override fun containsValue(value: V): Boolean {
        TODO("Not yet implemented")
    }

    override fun containsKey(key: K): Boolean {
        TODO("Not yet implemented")
    }

}