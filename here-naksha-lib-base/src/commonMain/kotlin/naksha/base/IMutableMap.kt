package naksha.base

/**
 * A simple interface to read and mutate `JSON` maps.
 * @since 3.0
 */
interface IMutableMap: IMap {
    /**
     * Change counter.
     * @since 3.0
     */
    val version: Int

    /**
     * Assigns the given `key` to the given `value`.
     * @param key the key to assign.
     * @param value the value to assign, if being [Base.UNDEFINED] the `key` is removed.
     * @since 3.0
     */
    operator fun set(key: Literal, value: Any?)

    /**
     * Removes `key` from the map.
     * @param key the key to remove.
     * @return `true` if the key existed and was removed; `false` if it was absent.
     * @since 3.0
     */
    fun remove(key: Literal): Boolean

    /**
     * Removes [key] from the map and returns the value that was assigned to.
     * @param key the key to remove.
     * @return the value previously associated with [key]; [Base.UNDEFINED] if absent.
     * @since 3.0
     */
    fun delete(key: Literal): Any?

    /**
     * Assigns [value] to [key] only if [key] is currently absent.
     * @param key the key to assign.
     * @param value the value to assign.
     * @return [Base.UNDEFINED] if the `key` was successfully assgined to the given `value` _(`key` was absent)_; the currently assigned value otherwise _(can be `null`)_.
     * @since 3.0
     */
    fun setIfAbsent(key: Literal, value: Any?): Any?

    /**
     * Sets `newValue` for `key` only if the current value matches `expected`.
     *
     * @param key the key to update.
     * @param expectedValue the expected current value.
     * @param newValue the value to set if the expectation is met.
     * @return `true` if the value was set; `false` if the current value did not match [expectedValue].
     * @since 3.0
     * @throws NakshaException if `expected` or `newValue` are [Base.UNDEFINED].
     */
    fun compareAndSet(key: Literal, expectedValue: Any?, newValue: Any?): Boolean

    /**
     * Removes `key` only if its current value equals `expectedValue`.
     *
     * - If `key` is absent → returns [Base.UNDEFINED]
     * - If `key` is assigned to `expectedValue` → removes the entry, returns `expectedValue`
     * - If `key` is assigned to to a different value → returns the current value _(failure; nothing was changed)_
     *
     * Usage pattern mirrors [setIfAbsent]:
     * ```kotlin
     * val key = literal("key")
     * val expectedValue = "foo"
     * val v = map.removeIf(key, expectedValue);
     * if (v === UNDEFINED) {
     *   // Did not exist.
     * } else if (v === expectedValue) {
     *   // Successfully removed.
     * } else {
     *   // Not removed, current value is `v`
     * }
     * ```
     *
     * @param key the key to conditionally remove.
     * @param expectedValue the value the entry must have for removal to proceed (must not be `null`).
     * @return `null` on success; the current value on failure.
     * @since 3.0
     * @throws NakshaException with error [ILLEGAL_ARGUMENT][NakshaError.ILLEGAL_ARGUMENT] if `expectedValue` is [Base.UNDEFINED].
     */
    fun deleteIf(key: Literal, expectedValue: Any): Any?

    /**
     * Replaces the value for `key` only if its current value equals `expectedValue`.
     *
     * @param key the key to update.
     * @param expectedValue the value the entry must currently have.
     * @param newValue the value to set if the expectation is met.
     * @return `true` if the value was replaced; `false` if the **key** is absent or the current value differs.
     * @since 3.0
     * @throws NakshaException with error [ILLEGAL_ARGUMENT][NakshaError.ILLEGAL_ARGUMENT] if `expectedValue` or `newValue` are [Base.UNDEFINED].
     */
    fun replace(key: Literal, expectedValue: Any, newValue: Any?): Boolean

    /**
     * Removes `key` only if its current value equals `expectedValue`.
     *
     * @param key the key to conditionally remove.
     * @param expectedValue the value the entry must have for removal to proceed.
     * @return `true` if the entry existed with **expectedValue** and was removed, or the **key** was absent; `false` if the **key** exists and is assigned to a different value.
     * @since 3.0
     * @throws NakshaException with error [ILLEGAL_ARGUMENT][NakshaError.ILLEGAL_ARGUMENT] if `expectedValue` or `newValue` are [Base.UNDEFINED].
     */
    fun removeIf(key: Literal, expectedValue: Any): Boolean

    /**
     * Removes all entries from this map.
     * @since 3.0
     */
    fun clear()
}