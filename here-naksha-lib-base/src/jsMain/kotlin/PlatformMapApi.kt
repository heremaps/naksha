package com.here.naksha.lib.base

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
internal actual class PlatformMapApi {
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
        actual fun map_delete(map: PlatformMap?, key: Any?): Any? {
            TODO("Not yet implemented")
        }
    }
}