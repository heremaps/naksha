@file:Suppress("OPT_IN_USAGE")

package naksha.base

import naksha.base.PlatformListApi.Companion.array_get
import naksha.base.PlatformListApi.Companion.array_get_length
import naksha.base.PlatformMapApi.Companion.map_clear
import naksha.base.PlatformMapApi.Companion.map_contains_key
import naksha.base.PlatformMapApi.Companion.map_contains_value
import naksha.base.PlatformMapApi.Companion.map_get
import naksha.base.PlatformMapApi.Companion.map_iterator
import naksha.base.PlatformMapApi.Companion.map_remove
import naksha.base.PlatformMapApi.Companion.map_set
import naksha.base.PlatformMapApi.Companion.map_size
import kotlin.collections.MutableMap.MutableEntry
import kotlin.js.JsExport
import kotlin.reflect.KClass

/**
 * A map that is not thread-safe.
 */
@Suppress("NON_EXPORTABLE_TYPE")
@JsExport
abstract class AbstractMapProxy<K:Any, V:Any>(val keyKlass: KClass<out K>, val valueKlass: KClass<out V>) : Proxy(), MutableMap<K, V?> {
    override fun createData(): PlatformMap = Platform.newMap()
    override fun data(): PlatformMap = super.data() as PlatformMap

    override fun bind(data: PlatformObject, symbol: Symbol) {
        require(data is PlatformMap)
        super.bind(data, symbol)
    }

    /**
     * Helper to return the value of the key, if the key does not exist or is not of the expected type, the
     * provided alternative is returned and the key is set to the alternative.
     * @param <T> The expected type.
     * @param key The key to query.
     * @param alternative The alternative to set and return, when the key does not exist or the value is not of the expected type.
     * @return The value.
     */
    fun <T : Any> getOrSet(key: K, alternative: T): T {
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
    fun <T : Any> getOrCreate(key: K, klass: KClass<out T>): T {
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
     * @return The value.
     */
    fun <T : Any> getAs(key: K, klass: KClass<out T>): T? = box(map_get(data(), key), klass)

    /**
     * Helper to return the value of the key, if the key does not exist or is not of the expected type, _null_ is returned.
     * @param <T> The expected type.
     * @param key The key to query.
     * @return The value or _null_.
     */
    @Deprecated("Does the same as getAs()", ReplaceWith("getAs(key, klass)"))
    fun <T : Any> getOrNull(key: K, klass: KClass<out T>): T? = box(map_get(data(), key), klass)

    /**
     * Convert the given value into a key.
     * @param value The value to convert.
     * @param alt The alternative to return when the value can't be cast.
     * @return The given value as key.
     */
    open fun toKey(value: Any?, alt: K? = null): K? = box(value, keyKlass, alt)

    /**
     * Convert the given value into a value.
     * @param key The key for which to convert the value.
     * @param value The value to convert.
     * @param alt The alternative to return when the value can't be cast.
     * @return The given value as value.
     */
    open fun toValue(key: K, value: Any?, alt: V? = null): V? = box(value, valueKlass, alt)

    override val entries: MutableSet<MutableEntry<K, V?>>
        get() {
            return rawEntries()
                .map { platformList ->
                    require(array_get_length(platformList) == 2) { "Expected PlatformList with size of 2 (key and value)" }
                    val key = toKey(array_get(platformList, 0))
                    requireNotNull(key) { "Key can't be null" }
                    Entry(key, toValue(key, array_get(platformList, 1)))
                }
                .toMutableSet()
        }

    override val keys: MutableSet<K>
        get() {
            return rawEntries()
                .mapNotNull { platformList ->
                    require(array_get_length(platformList) == 2) { "Expected PlatformList with size of 2 (key and value)" }
                    toKey(array_get(platformList, 0))
                }
                .toMutableSet()
        }

    override val size: Int
        get() = map_size(data())
    override val values: MutableCollection<V?>
        get() {
            return rawEntries()
                .mapNotNull { platformList ->
                    require(array_get_length(platformList) == 2) { "Expected PlatformList with size of 2 (key and value)" }
                    box(array_get(platformList, 1), valueKlass)
                }
                .toMutableSet()
        }

    override fun clear() = map_clear(data())

    override fun isEmpty(): Boolean = map_size(data()) == 0

    override fun remove(key: K): V? = toValue(key, map_remove(data(), key))

    override fun putAll(from: Map<out K, V?>) {
        from.onEach { (key, value) -> put(key, value) }
    }

    fun addAll(vararg items: Any?) {
        val data = data()
        var i = 0
        while (i < items.size) {
            val key = toKey(items[i++])
            val value = if (i < items.size) unbox(items[i++]) else null
            require(key != null)
            map_set(data, key, value)
        }
    }

    override fun put(key: K, value: V?): V? = toValue(key, map_set(data(), key, unbox(value)))

    override fun get(key: K): V? = toValue(key, map_get(data(), key))

    /**
     * Returns the raw value stored in the underlying base map.
     * @param key The key to read.
     * @return The raw value, being either a scalar or [PlatformObject].
     */
    fun getRaw(key: Any): Any? = map_get(data(), key)

    /**
     * Sets the raw value stored in the underlying base map.
     * @param key The key to set.
     * @param value The value to set.
     * @return The previously set value.
     */
    fun setRaw(key: Any, value: Any?): Any? = map_set(data(), key, unbox(value))

    /**
     * Tests if the underlying base map stored the given key.
     * @param key The key to test.
     * @return _true_ if the underlying map contains the given key; _false_ otherwise.
     */
    fun hasRaw(key: Any): Boolean = map_contains_key(data(), key)

    /**
     * Removes the key from the underlying base map.
     * @param key The key to remove.
     * @return The value that was removed; _null_ if either the value was _null_ or no such key existed.
     */
    fun removeRaw(key: Any): Any? = map_remove(data(), key)

    override fun containsValue(value: V?): Boolean = map_contains_value(data(), value)

    override fun containsKey(key: K): Boolean = map_contains_key(data(), key)

    class Entry<K, V>(override val key: K, initialValue: V) : MutableEntry<K, V> {

        private var currentValue: V = initialValue

        override fun setValue(newValue: V): V {
            val oldValue = currentValue
            currentValue = newValue
            return oldValue
        }

        override val value: V
            get() = currentValue
    }

    private fun rawEntries(): Sequence<PlatformList> {
        val platformIterator = map_iterator(data())
        return generateSequence(platformIterator.next().value) {
            platformIterator.next().value
        }
    }
}
