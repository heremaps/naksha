@file:Suppress("OPT_IN_USAGE")

package com.here.naksha.lib.base

import com.here.naksha.lib.base.PlatformMapApi.Companion.map_get
import com.here.naksha.lib.base.PlatformMapApi.Companion.map_set
import kotlin.js.JsExport
import kotlin.reflect.KClass

/**
 * The Naksha type for an object.
 * @param entries The entries to add into the object, being a list alternating _key_, _value_ pairs, where _key_ need to a
 * string and _value_ can be anything.
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
        val data = data()
        val raw = map_get(data, key)
        var value = box(raw, Platform.klassOf(alternative))
        if (value == null) {
            value = alternative
            map_set(data, key, unbox(value))
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
        val data = data()
        val raw = map_get(data, key)
        var value = box(raw, klass)
        if (value == null) {
            value = Platform.newInstanceOf(klass)
            map_set(data, key, unbox(value))
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
        val data = data()
        val raw = map_get(data, key)
        val value = box(raw, klass)
        return value ?: alternative
    }

    /**
     * Helper to return the value of the key, if the key does not exist or is not of the expected type, _null_ is returned.
     * @param <T> The expected type.
     * @param key The key to query.
     * @return The value or _null_.
     */
    protected fun <T : Any> getOrNull(key: String, klass: KClass<out T>): T? = box(map_get(data(), key), klass)

    /**
     * Helper to return the value of the key, if the key does not exist or is not of the expected type, _undefined_ is returned.
     * @param <T> The expected type.
     * @param key The key to query.
     * @return The value or _undefined_.
     */
    protected fun <T : Any> getOrUndefined(key: String, klass: KClass<out T>): T = box(map_get(data(), key), klass) ?: Platform.undefinedOf(klass)
}