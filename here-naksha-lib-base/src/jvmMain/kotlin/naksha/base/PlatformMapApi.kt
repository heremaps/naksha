package naksha.base

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual class PlatformMapApi {
    // https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Map
    actual companion object PlatformMapApiCompanion {
        @JvmStatic
        actual fun map_get(map: PlatformMap?, key: Any?): Any? = if (map is JvmMap && key != null) map[key] else null

        @JvmStatic
        actual fun map_set(map: PlatformMap?, key: Any?, value: Any?): Any? {
            var old: Any? = null
            if (map is JvmMap && key != null) {
                old = map[key]
                map[key] = value
            }
            return old
        }

        @JvmStatic
        actual fun map_remove(map: PlatformMap?, key: Any?): Any? =
            if (map is JvmMap && key != null) map.remove(key) else null

        @JvmStatic
        actual fun map_clear(map: PlatformMap?) {
            if (map is JvmMap) map.clear()
        }

        @JvmStatic
        actual fun map_size(map: PlatformMap?): Int = if (map is JvmMap) map?.size ?: 0 else 0

        @JvmStatic
        actual fun map_contains_key(map: PlatformMap?, key: Any?): Boolean =
            if (map is JvmMap && key != null) map.containsKey(key) else false

        @JvmStatic
        actual fun map_contains_value(map: PlatformMap?, value: Any?): Boolean =
            if (map is JvmMap && value != null) map.containsValue(value) else false

        @JvmStatic
        actual fun map_iterator(map: PlatformMap?): PlatformIterator<PlatformList> = JvmMapEntryIterator(map)

        @JvmStatic
        actual fun map_key_iterator(map: PlatformMap?): PlatformIterator<Any> = JvmMapKeyIterator(map)

        @JvmStatic
        actual fun map_value_iterator(map: PlatformMap?): PlatformIterator<Any?> = JvmMapValueIterator(map)
    }
}
