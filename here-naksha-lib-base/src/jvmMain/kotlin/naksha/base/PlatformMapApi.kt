package naksha.base

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual class PlatformMapApi {
    // https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Map
    actual companion object {
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

        actual fun map_clear(map: PlatformMap?) {
            if (map is JvmMap) map.clear()
        }

        actual fun map_size(map: PlatformMap?): Int = if (map is JvmMap) map.size() else 0

        actual fun map_contains_key(map: PlatformMap?, key: Any?): Boolean =
            if (map is JvmMap && key != null) map.containsKey(key) else false

        actual fun map_contains_value(map: PlatformMap?, value: Any?): Boolean =
            if (map is JvmMap && value != null) map.containsValue(value) else false

        actual fun map_iterator(map: PlatformMap?): PlatformIterator<PlatformList> = JvmMapEntryIterator(map)

        actual fun map_key_iterator(map: PlatformMap?): PlatformIterator<Any> = JvmMapKeyIterator(map)

        actual fun map_value_iterator(map: PlatformMap?): PlatformIterator<Any?> = JvmMapValueIterator(map)
    }
}
