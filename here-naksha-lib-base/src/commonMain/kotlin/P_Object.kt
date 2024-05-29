@file:Suppress("OPT_IN_USAGE")

package com.here.naksha.lib.base

import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.reflect.KClass

/**
 * The Naksha type for an object.
 * @param entries The entries to add into the object. Can be a list of [P_Entry] of just alternating (_key_, _value_)'s,
 * where _key_ need to a string and _value_ can be anything.
 */
@Suppress("NON_EXPORTABLE_TYPE", "LeakingThis", "unused")
@JsExport
abstract class P_Object(vararg entries: Any?) : P_Map<String, Any>(String::class, Any::class) {
    init {
        if (entries.isNotEmpty()) {
            bind(N.newObject(*entries), N.symbolOf(this::class))
        }
    }

    override fun createData(): N_Map = N.newMap()

    /**
     * Helper to return the value of the key, if the key does not exist or is not of the expected type, the
     * provided alternative is returned and the key is set to the alternative.
     * @param <T> The expected type.
     * @param key The key to query.
     * @param alternative The alternative to set and return, when the key does not exist or the value is not of the expected type.
     * @return The value.
     */
    protected fun <T : Any> getOrSet(key: String, alternative: T): T {
        val map = data()
        val raw = map[key]
        var value = convert(raw, N.klassOf(alternative))
        if (value == null) {
            value = alternative
            map[key] = N.unbox(value)
        }
        return value
    }

    /**
     * Helper to return the value of the key, if the key does not exist or is not of the expected type, a new
     * value is created, stored with the key and returned.
     * @param <T> The expected type.
     * @param key The key to query.
     * @param klass The [KClass] of the expected value.
     * @return The value.
     */
    protected fun <T : Any> getOrCreate(key: String, klass: KClass<out T>): T {
        val map = data()
        val raw = map[key]
        var value = convert(raw, klass)
        if (value == null) {
            value = N.newInstanceOf(klass)
            map[key] = N.unbox(value)
        }
        return value
    }

    /**
     * Helper to return the value of the key, if the key does not exist or is not of the expected type, the
     * provided alternative is returned.
     * @param <T> The expected type.
     * @param key The key to query.
     * @param alternative The alternative to return, when the key does not exist or the value is not of the expected type.
     * @return The value.
     */
    protected fun <T : Any> getAs(key: String, klass: KClass<out T>, alternative: T): T {
        val map = data()
        val raw = map[key]
        val value = convert(raw, klass)
        return value ?: alternative
    }

    /**
     * Helper to return the value of the key, if the key does not exist or is not of the expected type, _null_ is returned.
     * @param <T> The expected type.
     * @param key The key to query.
     * @return The value or _null_.
     */
    protected fun <T : Any> getOrNull(key: String, klass: KClass<out T>): T? {
        val map = data()
        val raw = map[key]
        return convert(raw, klass)
    }

    /**
     * Helper to return the value of the key, if the key does not exist or is not of the expected type, _undefined_ is returned.
     * @param <T> The expected type.
     * @param key The key to query.
     * @return The value or _undefined_.
     */
    protected fun <T : Any> getOrUndefined(key: String, klass: KClass<out T>): T {
        val map = data()
        val raw = map[key]
        return convert(raw, klass) ?: N.undefinedOf(klass)
    }
}