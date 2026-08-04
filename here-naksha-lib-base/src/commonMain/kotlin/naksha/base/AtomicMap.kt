package naksha.base

/**
 * A cross-platform [Map] providing thread safety and atomicity guarantees.
 *
 * To maintain the specified guarantees, default implementations of methods including [putIfAbsent] inherited from [MutableMap] must be overridden by implementations of this interface. Similarly, implementations of the collections returned by methods [MutableMap.keys], [MutableMap.values], and [MutableMap.entries] must override methods such as [MutableSet.remove] when necessary to preserve atomicity guarantees.
 *
 * Memory consistency effects: As with other concurrent collections, actions in a thread prior to placing an object into a [AtomicMap] as a key or value **happen-before** actions subsequent to the access or removal of that object from the [AtomicMap] in another thread.
 *
 * Atomic maps must not contain `null` values, therefore the helper [compareAndSet] was added, because `null` effectively means that a value is not present in the map.
 *
 * @param <K> the type of keys maintained by this map
 * @param <V> the type of mapped values
 * @since 3.0
 */
expect open class AtomicMap<K: Any, V: Any>: MutableMap<K, V> {

    /**
     * If the specified key is not already associated with a value, associates it with the given value. This is equivalent to
     * ```kotlin
     * if (!map.containsKey(key))
     *   return map.put(key, value)
     * else
     *   return map.get(key)
     * ```
     * except that the action is performed atomically.
     *
     * @param key key with which the specified value is to be associated
     * @param value value to be associated with the specified key
     * @return the previous value associated with the specified key, or _null_ if there was no mapping for the key.
     * @since 3.0
     */
    fun putIfAbsent(key: K, value: V): V?

    /**
     * Removes the entry for a key only if currently mapped to a given value. This is equivalent to:
     * ```kotlin
     * if (map.containsKey(key) && map[key] == value) {
     *   map.remove(key)
     *   return true
     * }
     * return false
     * ```
     * except that the action is performed atomically.
     *
     * @param key key with which the specified value is associated.
     * @param value value expected to be associated with the specified key.
     * @return _true_ if the value was removed; _false_ otherwise.
     * @since 3.0
     */
    fun remove(key: K, value: V): Boolean

    /**
     * Replaces the entry for a key only if currently mapped to a given value. This is equivalent to:
     * ```kotlin
     * if (map.containsKey(key) && map[key] == oldValue) {
     *   map[key] = newValue
     *   return true
     * }
     * return false
     * ```
     * except that the action is performed atomically.
     *
     * @param key key with which the specified value is associated
     * @param oldValue value expected to be associated with the specified key
     * @param newValue value to be associated with the specified key
     * @return _true_ if the value was replaced; _false_ otherwise.
     * @since 3.0
     */
    fun replace(key: K, oldValue: V, newValue: V): Boolean

    /**
     * Simplified compare-and-set operation. This is equivalent to:
     * ```kotlin
     * if (newValue == null) {
     *   if (oldValue == null) return !containsKey(key)
     *   return remove(key, oldValue)
     * }
     * if (oldValue == null) {
     *   return putIfAbsent(key, newValue) == null
     * }
     * return replace(key, oldValue, newValue)
     * ```
     * @param key the key to compare.
     * @param oldValue the value expected, `null` means key should not exist yet.
     * @param newValue the value to set, `null` means to remove the key value pair.
     * @return _true_ if the operation succeeded; _false_ if the existing value is not what is expected.
     * @since 3.0
     */
    fun compareAndSet(key: K, oldValue: V?, newValue: V?): Boolean

    /**
     * Simple helper that allows to set the value `null`, which actually means to remove the assignment. This is equivalent to:
     * ```kotlin
     * if (newValue == null)
     *   return remove(key)
     * else
     *   return put(key, newValue)
     * ```
     * @param key the key to mutate.
     * @param newValue the new value to set, if `null`, the key is removed.
     * @return the value that was previously assigned; `null` if the key as not contained.
     * @since 3.0
     */
    fun putOrRemove(key: K, newValue: V?): V?

    override val keys: MutableSet<K>
    override val values: MutableCollection<V>
    override val entries: MutableSet<MutableMap.MutableEntry<K, V>>
    override fun put(key: K, value: V): V?
    override fun remove(key: K): V?
    override fun putAll(from: Map<out K, V>)
    override fun clear()
    override val size: Int
    override fun isEmpty(): Boolean
    override fun containsKey(key: K): Boolean
    override fun containsValue(value: V): Boolean
    override fun get(key: K): V?

}