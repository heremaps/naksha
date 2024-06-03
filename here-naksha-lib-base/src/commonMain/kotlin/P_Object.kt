@file:Suppress("OPT_IN_USAGE")

package com.here.naksha.lib.base

import kotlin.js.JsExport
import kotlin.reflect.KClass

/**
 * The Naksha type for an object.
 * @param entries The entries to add into the object. Can be a list of [P_MapEntry] of just alternating (_key_, _value_)'s,
 * where _key_ need to a string and _value_ can be anything.
 */
@Suppress("NON_EXPORTABLE_TYPE", "LeakingThis", "unused")
@JsExport
abstract class P_Object(vararg entries: Any?) : P_Map<String, Any>(String::class, Any::class) {
    init {
        if (entries.isNotEmpty()) {
            bind(Platform.newObject(*entries), Platform.symbolOf(this::class))
        }
    }

    override fun createData(): PlatformMap = Platform.newMap()

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
        var value = proxy(raw, Platform.klassOf(alternative))
        if (value == null) {
            value = alternative
            map[key] = Platform.unbox(value)
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
        var value = proxy(raw, klass)
        if (value == null) {
            value = Platform.newInstanceOf(klass)
            map[key] = Platform.unbox(value)
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
        val value = proxy(raw, klass)
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
        return proxy(raw, klass)
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
        return proxy(raw, klass) ?: Platform.undefinedOf(klass)
    }
}