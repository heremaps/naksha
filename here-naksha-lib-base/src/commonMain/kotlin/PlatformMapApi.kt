@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package com.here.naksha.lib.base

internal expect class PlatformMapApi {
    // https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Map
    companion object {
        fun map_get(map: PlatformMap?, key: Any?): Any?
        fun map_set(map: PlatformMap?, key: Any?, value: Any?): Any?
        fun map_delete(map: PlatformMap?, key: Any?): Any?
    }
}