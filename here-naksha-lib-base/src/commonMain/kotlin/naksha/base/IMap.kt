package naksha.base

/**
 * A simple interface to read `JSON` maps.
 * @since 3.0
 */
interface IMap: IObject {
    /**
     * The number of entries in this map.
     * @since 3.0
     */
    val length: Int

    /**
     * Iterates all key-value pairs in this map in insertion order.
     *
     * Modification of the map while iterating are going to impact the iterator. The iterator will not see changed done to keys that it has already iterated, but new keys or modifications of keys it has not yet iterated, are visible. Beware that keys are iterated in insertion order. When the map internally resizes, the iterator will find the previous key and continue with the next key.
     *
     * If the key the iterator is currently processing is deleted, and the underlying entries are resized at the same moment, the iterator may have issues to recover exactly at the right spot. It may potentially miss some keys or re-iterate ones it has already iterated, in such a situation.
     *
     * @param action callback receiving `(key, value)`. It may throw [ForEachAbort] to abort the iteration.
     * @since 3.0
     */
    fun forEach(action: (key: Literal, value: Any?) -> Unit)

    /**
     * Iterates all key-value pairs in this map in insertion order to calculate a result.
     *
     * Modification of the map while iterating are going to impact the iterator. The iterator will not see changed done to keys that it has already iterated, but new keys or modifications of keys it has not yet iterated, are visible. Beware that keys are iterated in insertion order. When the map internally resizes, the iterator will find the previous key and continue with the next key.
     *
     * If the key the iterator is currently processing is deleted, and the underlying entries are resized at the same moment, the iterator may have issues to recover exactly at the right spot. It may potentially miss some keys or re-iterate ones it has already iterated, in such a situation.
     *
     * @param initialValue the initial pass-through value.
     * @param action callback receiving `(key, value, result)`, returning the result. It may throw [ForEachAbort] to abort the iteration.
     * @return the result as returned by the last iterator or `initialValue`.
     * @since 3.0
     */
    fun <R> reduce(initialValue: R? = null, action: (key: Literal, value: Any?, result: R?) -> R?): R?

    /**
     * Returns the value currently being assigned to the given `key`; [UNDEFINED][BaseObject.UNDEFINED] if the `key` is absent.
     * @param key the key to search.
     * @return the value assigned to the given `key`; [UNDEFINED][BaseObject.UNDEFINED] if the `key` was not found.
     * @since 3.0
     */
    operator fun get(key: Literal): Any?

    /**
     * Returns true if `key` exists in this map.
     * @param key the key to test.
     * @return `true` if the `key` has any assignment _(can be `null`)_; `false` if the `key` is not contained in this map.
     * @since 3.0
     */
    fun containsKey(key: Literal): Boolean
}