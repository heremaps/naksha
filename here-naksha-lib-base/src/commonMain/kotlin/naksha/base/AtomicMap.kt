package naksha.base

import kotlin.js.JsExport

/**
 * A [Map] providing thread safety and atomicity guarantees.
 *
 * To maintain the specified guarantees, default implementations of
 * methods including [putIfAbsent] inherited from [MutableMap]
 * must be overridden by implementations of this interface. Similarly,
 * implementations of the collections returned by methods [MutableMap.keys],
 * [MutableMap.values], and [MutableMap.entries] must override
 * methods such as [MutableSet.remove] when necessary to
 * preserve atomicity guarantees.
 *
 * Memory consistency effects: As with other concurrent
 * collections, actions in a thread prior to placing an object into a
 * [AtomicMap] as a key or value **happen-before** actions subsequent to the
 * access or removal of that object from the [AtomicMap] in another thread.
 *
 * @param <K> the type of keys maintained by this map
 * @param <V> the type of mapped values
 * @since 3.0
 */
@Suppress("OPT_IN_USAGE")
@JsExport
interface AtomicMap<K : Any, V : Any> : MutableMap<K, V> {

    /**
     * If the specified key is not already associated with a value, associates it with the given value.
     * This is equivalent to
     * ```
     * if (!map.containsKey(key))
     *   return map.put(key, value);
     * else
     *   return map.get(key);
     * ```
     * except that the action is performed atomically.
     *
     * @param key key with which the specified value is to be associated
     * @param value value to be associated with the specified key
     * @return the previous value associated with the specified key, or _null_ if there was no mapping for the key.
     * @throws UnsupportedOperationException if the **put** operation is not supported by this map
     * @throws IllegalArgumentException if some property of the specified key or value prevents it from being stored in this map
     * @since 3.0
     */
    fun putIfAbsent(key: K, value: V): V?

    /**
     * Removes the entry for a key only if currently mapped to a given value.
     * This is equivalent to:
     * ```
     * if (map.containsKey(key) && map.get(key)===value) {
     *   map.remove(key);
     *   return true;
     * }
     * return false;
     * ```
     * except that the action is performed atomically.
     *
     * @param key key with which the specified value is associated
     * @param value value expected to be associated with the specified key
     * @return _true_ if the value was removed
     * @throws UnsupportedOperationException if the **remove** operation is not supported by this map
     * @throws IllegalArgumentException if some property of the specified key or value prevents it from being stored in this map
     * @since 3.0
     */
    fun remove(key: K, value: V): Boolean

    /**
     * Replaces the entry for a key only if currently mapped to a given value.
     * This is equivalent to:
     * ```
     * if (map.containsKey(key) &&
     *   Objects.equals(map.get(key), oldValue)) {
     *   map.put(key, newValue);
     *   return true;
     * } else {
     *   return false;
     * }
     * ```
     * except that the action is performed atomically.
     *
     * @param key key with which the specified value is associated
     * @param oldValue value expected to be associated with the specified key
     * @param newValue value to be associated with the specified key
     * @return _true_ if the value was replaced
     * @throws UnsupportedOperationException if the **put** operation is not supported by this map
     * @throws IllegalArgumentException if some property of the specified key or value prevents it from being stored in this map
     * @since 3.0
     */
    fun replace(key: K, oldValue: V, newValue: V): Boolean
}