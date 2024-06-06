package com.here.naksha.lib.base

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual class PlatformMapApi {
    // https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Map
    @OptIn(ExperimentalJsStatic::class)
    actual companion object {
        @JsStatic
        actual fun map_get(map: PlatformMap?, key: Any?): Any? {
            TODO("Not yet implemented")
        }

        @JsStatic
        actual fun map_set(map: PlatformMap?, key: Any?, value: Any?): Any? {
            TODO("Not yet implemented")
        }

        @JsStatic
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