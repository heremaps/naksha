@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package naksha.base

/**
 * General API to access [PlatformMap]'s.
 *
 * ### Note
 * The [platform maps][PlatformMap] are not thread safe, they must not be accessed by multiple threads in parallel!
 *
 * @since 3.0
 */
expect class PlatformMapApi private constructor() {
    // https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Map
    companion object PlatformMapApiCompanion {
        /**
         * Clears the [PlatformMap].
         * @param map The [PlatformMap] to clear.
         * @since 3.0
         */
        fun map_clear(map: PlatformMap?)

        /**
         * Returns the amount of entries in the [PlatformMap].
         * @param map The [PlatformMap] to count entries.
         * @return the amount of entries in the [PlatformMap].
         * @since 3.0
         */
        fun map_size(map: PlatformMap?): Int

        /**
         * Returns the value for the key or `null` _(`undefined` in JavaScript)_ is no such entry exists.
         *
         * ### Note
         * In JavaScript `undefined` is returned when the key does not exist, but as storing a value `undefined` is allowed too, there is not much help by this behavior, and `null == undefined` is _true_, so this can be ignored.
         * @param map The [PlatformMap] to count entries.
         * @param key The key to read.
         * @return the value stored for the key; `null` if either the value is `null` or no such key exists.
         * @since 3.0
         */
        fun map_get(map: PlatformMap?, key: Any?): Any?

        /**
         * Set the key to the given value in the [PlatformMap].
         * @param map The [PlatformMap] to modify.
         * @param key The key to set.
         * @param key The value to set.
         * @return the previously set value.
         * @since 3.0
         */
        fun map_set(map: PlatformMap?, key: Any?, value: Any?): Any?

        /**
         * Tests if the [PlatformMap] contains the key.
         * @param map The [PlatformMap] to query.
         * @param key The key to test.
         * @return _true_ if the key exists; _false_ otherwise.
         * @since 3.0
         */
        fun map_contains_key(map: PlatformMap?, key: Any?): Boolean

        /**
         * Tests if the [PlatformMap] contains the given value.
         * @param map The [PlatformMap] to query.
         * @param value The value to test.
         * @return _true_ if the value exists; _false_ otherwise.
         * @since 3.0
         */
        fun map_contains_value(map: PlatformMap?, value: Any?): Boolean

        /**
         * Remove the given key from the [PlatformMap].
         * @param map The [PlatformMap] to query.
         * @param key The key to remove.
         * @return The value that was assigned; `null` when the value was `null` or no such key existed.
         * @since 3.0
         */
        fun map_remove(map: PlatformMap?, key: Any?): Any?

        /**
         * Returns an iterator above all entries of the [PlatformMap].
         *
         * The iterator provides one array for each entry, with key being stored at index `0`, and the value being at index `1`.
         * @param map The [PlatformMap] to query.
         * @return The iterator above all entries.
         * @since 3.0
         */
        fun map_iterator(map: PlatformMap?): PlatformIterator<PlatformList>

        /**
         * Returns an iterator above all keys stored in the [PlatformMap].
         *
         * @param map The [PlatformMap] to query.
         * @return The iterator above all keys.
         * @since 3.0
         */
        fun map_key_iterator(map: PlatformMap?): PlatformIterator<Any>

        /**
         * Returns an iterator above all values stored in the [PlatformMap].
         *
         * @param map The [PlatformMap] to query.
         * @return The iterator above all values.
         * @since 3.0
         */
        fun map_value_iterator(map: PlatformMap?): PlatformIterator<Any?>
    }
}