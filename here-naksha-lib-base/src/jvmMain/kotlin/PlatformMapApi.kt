package com.here.naksha.lib.base

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
        actual fun map_remove(map: PlatformMap?, key: Any?): Any? {
            TODO("Not yet implemented")
        }

        actual fun map_clear(map: PlatformMap?) {
        }

        actual fun map_size(map: PlatformMap?): Int {
            TODO("Not yet implemented")
        }

        actual fun map_contains_key(map: PlatformMap?, key: Any?): Boolean {
            TODO("Not yet implemented")
        }

        actual fun map_contains_value(map: PlatformMap?, value: Any?): Boolean {
            TODO("Not yet implemented")
        }
    }
}