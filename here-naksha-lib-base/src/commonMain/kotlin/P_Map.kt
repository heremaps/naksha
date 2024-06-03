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
    protected open fun toKey(value: Any?, alt: K? = null): K? = box(value, keyKlass, alt)

    /**
     * Convert the given value into a value.
     * @param value The value to convert.
     * @param alt The alternative to return when the value can't be cast.
     * @return The given value as value.
     */
    protected open fun toValue(value: Any?, alt: V? = null): V? = box(value, valueKlass, alt)

    override fun createData(): PlatformMap = Platform.newMap()
    override fun data(): PlatformMap = super.data() as PlatformMap

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