@file:Suppress("OPT_IN_USAGE")

package naksha.base

import naksha.base.Platform.PlatformCompanion.forInstance
import naksha.base.Platform.PlatformCompanion.forKClass
import naksha.base.PlatformMapApi.PlatformMapApiCompanion.map_clear
import naksha.base.PlatformMapApi.PlatformMapApiCompanion.map_contains_key
import naksha.base.PlatformMapApi.PlatformMapApiCompanion.map_contains_value
import naksha.base.PlatformMapApi.PlatformMapApiCompanion.map_get
import naksha.base.PlatformMapApi.PlatformMapApiCompanion.map_remove
import naksha.base.PlatformMapApi.PlatformMapApiCompanion.map_set
import naksha.base.PlatformMapApi.PlatformMapApiCompanion.map_size
import naksha.base.fn.Fn2
import kotlin.collections.MutableMap.MutableEntry
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic
import kotlin.reflect.KClass

/**
 * A map that is not thread-safe.
 * @property keyType The [PlatformType] of the keys.
 * @property valueType The [PlatformType] of the values.
 */
@Suppress("NON_EXPORTABLE_TYPE")
@JsExport
open class MapProxy<K, V>(val keyType: PlatformType<K>, val valueType: PlatformType<V>) : Proxy(), MutableMap<K, V?> {

    companion object MapProxyCompanion {
        /**
         * The [PlatformType] of [MapProxy].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(MapProxy::class).withPackageName(PACKAGE_NAME)

        /**
         * Add all given keys into the given map, and return the map.
         * @param map The map into which to add the given key(s).
         * @param keys The key(s) to add _(value will be `null`)_
         * @return The map with the key(s) added.
         * @since 3.0
         */
        @JvmStatic
        @JsStatic
        fun <KEY, VALUE, PROXY : MapProxy<KEY, VALUE>> addAll(map: PROXY, vararg keys: KEY): PROXY {
            for (key in keys) {
                map.put(key, map[key])
            }
            return map
        }
    }

    override fun createData(): PlatformMap = Platform.newMap()
    override fun platformObject(): PlatformMap = super.platformObject() as PlatformMap

    override fun bind(data: PlatformObject, symbol: Symbol) {
        if (data !is PlatformMap) throw illegalArg("Can't bind to non platform object")
        super.bind(data, symbol)
    }

    /**
     * Helper to return the value stored for the key, if the key does not exist or the value is not of the expected type, the alternative is returned.
     * @param key the key to query.
     * @param alternative the alternative to return, when the value is not of the expected type.
     * @return the value.
     */
    fun <T : Any> getOr(key: K, alternative: T): T {
        val data = platformObject()
        val raw = map_get(data, key)
        val value = box(raw, forInstance(alternative))
        return value ?: alternative
    }

    /**
     * Helper to return the value of the key, if the key does not exist or the value is not of the expected type, the alternative is set
     * and returned.
     * @param key the key to query.
     * @param alternative the alternative to set and return, when the value is not of the expected type.
     * @return the value.
     */
    fun <T : Any> getOrSet(key: K, alternative: T): T {
        val data = platformObject()
        val raw = map_get(data, key)
        var value = box(raw, forInstance(alternative))
        if (value == null) {
            value = alternative
            map_set(data, key, unbox(value))
        }
        return value
    }

    /**
     * Helper to return the value of the key, if the key does not exist or is not of the expected type, a new value is created, stored
     * with the key and returned.
     * @param key the key to query.
     * @param type the [KClass] of the expected value type.
     * @param init the initialize method to invoke, when the value is not of the expected type.
     * @return the value.
     */
    fun <T, KEY: K, SELF: MapProxy<K, V>> getOrInit(key: KEY, type: PlatformType<out T>, init: Fn2<out T, in SELF, in KEY>): T {
        val data = platformObject()
        var value: T? = null
        if (map_contains_key(data, key)) {
            val raw = map_get(data, key)
            value = box(raw, type)
        }
        if (value == null) {
            @Suppress("UNCHECKED_CAST")
            value = init.call(this as SELF, key)
            if (value == null) throw illegalState("Failed to get or init '$key', init method returned null")
            map_set(data, key, unbox(value))
        }
        return value
    }

    /**
     * Helper to return the value of the key, if the key does not exist or is not of the expected type, a new
     * value is created, stored with the key and returned.
     * @param key the key to query.
     * @param type the [PlatformType] of the expected value.
     * @param init the initialize method to invoke, when the value is not of the expected type.
     * @return The value.
     */
    fun <T, KEY: K, SELF: MapProxy<K, V>> getOrCreate(
        key: KEY,
        type: PlatformType<T>,
        init: Fn2<out T?, in SELF, in KEY>? = null
    ): T {
        val data = platformObject()
        var raw: Any? = null
        if (map_contains_key(data, key)) {
            raw = map_get(data, key)
            val value = box(raw, type)
            if (value != null) return value
        }
        if (init != null) {
            @Suppress("UNCHECKED_CAST")
            val value = init.call(this as SELF, key)
            if (value != null) {
                val unboxed = unbox(value)
                map_set(data, key, unboxed)
                return value
            }
        }
        val value: T?
        if (type.isAssignableTo(JsEnum.TYPE)) {
            @Suppress("UNCHECKED_CAST")
            value = JsEnum.get(raw, type as PlatformType<out JsEnum>) as T
        } else {
            value = type.newInstance()
        }
        val unboxed = unbox(value)
        map_set(data, key, unboxed)
        return value
    }

    /**
     * Helper to return the value of the key in the desired type. If the key does not exist, or is not of the expected type, `null` is returned.
     * @param key The key to query.
     * @param type The expected type.
     * @return The value as expected type or `null`, if no such key exists, or the value can't be proxied as the desired type.
     * @see [getOrNull]
     */
    fun <T> getAs(key: K, type: PlatformType<T>): T? = box(map_get(platformObject(), key), type)

    /**
     * Helper to return the value of the key in the desired type. If the key does not exist, or is not of the expected type, `null` is returned.
     * @param <T> The expected type.
     * @param key The key to query.
     * @return The value as expected type or `null`, if no such key exists, or the value can't be proxied as the desired type.
     * @see [getAs]
     */
    fun <T> getOrNull(key: K, type: PlatformType<T>): T? = box(map_get(platformObject(), key), type)

    /**
     * Convert the given value into a key.
     * @param value The value to convert.
     * @param alt The alternative to return when the value can't be cast.
     * @return The given value as key.
     */
    open fun toKey(value: Any?, alt: K? = null): K? = box(value, keyType, alt)

    /**
     * Convert the given value into a value.
     * @param key The key for which to convert the value.
     * @param value The value to convert.
     * @param alt The alternative to return when the value can't be cast.
     * @return The given value as value.
     */
    open fun toValue(key: K, value: Any?, alt: V? = null): V? = box(value, valueType, alt)

    override val entries: MutableSet<MutableEntry<K, V?>>
        get() = MapProxyMutableEntrySet(this)

    override val keys: MutableSet<K>
        get() = MapProxyMutableKeySet(this)

    override val size: Int
        get() = map_size(platformObject())

    override val values: MutableCollection<V?>
        get() = MapProxyMutableValueCollection(this)

    override fun clear() = map_clear(platformObject())

    override fun isEmpty(): Boolean = map_size(platformObject()) == 0

    override fun remove(key: K): V? = toValue(key, map_remove(platformObject(), key))

    override fun putAll(from: Map<out K, V?>) {
        from.onEach { (key, value) -> put(key, value) }
    }

    open fun addAll(vararg items: Any?) {
        val data = platformObject()
        var i = 0
        while (i < items.size) {
            val original = items[i++]
            val key = toKey(original)
            val value = if (i < items.size) unbox(items[i++]) else null
            if (key == null) {
                if (original == null) throw illegalArg("Invalid key: null")
                val originalType = forInstance(original)
                throw illegalArg("Invalid key: '${originalType.name}', expected: '${keyType.name}'")
            }
            map_set(data, key, value)
        }
    }

    override fun put(key: K, value: V?): V? = toValue(key, map_set(platformObject(), key, unbox(value)))

    override fun get(key: K): V? = toValue(key, map_get(platformObject(), key))

    /**
     * Returns the raw value stored in the underlying base map.
     * @param key The key to read.
     * @return The raw value, being either a scalar or [PlatformObject].
     */
    fun getRaw(key: Any): Any? = map_get(platformObject(), key)

    /**
     * Sets the raw value stored in the underlying base map.
     * @param key The key to set.
     * @param value The value to set.
     * @return The previously set value.
     */
    fun setRaw(key: Any, value: Any?): Any? = map_set(platformObject(), key, unbox(value))

    /**
     * Tests if the underlying base map stored the given key.
     * @param key The key to test.
     * @return _true_ if the underlying map contains the given key; _false_ otherwise.
     */
    fun hasRaw(key: Any): Boolean = map_contains_key(platformObject(), key)

    /**
     * Removes the key from the underlying base map.
     * @param key The key to remove.
     * @return The value that was removed; _null_ if either the value was _null_ or no such key existed.
     */
    fun removeRaw(key: Any): Any? = map_remove(platformObject(), key)

    override fun containsValue(value: V?): Boolean = map_contains_value(platformObject(), unbox(value))

    override fun containsKey(key: K): Boolean = map_contains_key(platformObject(), key)
}
