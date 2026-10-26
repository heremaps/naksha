@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package naksha.base

expect class PlatformMapApi {
    // https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Map
    companion object PlatformMapApiCompanion {
        fun map_clear(map: PlatformMap?)
        fun map_size(map: PlatformMap?): Int
        fun map_get(map: PlatformMap?, key: Any?): Any?
        fun map_set(map: PlatformMap?, key: Any?, value: Any?): Any?
        fun map_contains_key(map: PlatformMap?, key: Any?): Boolean
        fun map_contains_value(map: PlatformMap?, value: Any?): Boolean
        fun map_remove(map: PlatformMap?, key: Any?): Any?

        /**
         * Returns the platform iterator above a map.
         *
         * Usage example for a map iterator:
         * ```kotlin
         * val it = map_iterator(map)
         * var next = it.next()
         * while (!next.done) {
         *   val entry: PlatformList = next.value
         *   val key = array_get(entry, 0)
         *   val value = array_get(entry, 1)
         *   // Do something
         *   next = it.next()
         * }
         * ```
         * @param map the map to iterate.
         * @return the iterator.
         * @since 3.0
         */
        fun map_iterator(map: PlatformMap?): PlatformIterator<PlatformList>
        fun map_key_iterator(map: PlatformMap?): PlatformIterator<Any>
        fun map_value_iterator(map: PlatformMap?): PlatformIterator<Any?>
    }
}