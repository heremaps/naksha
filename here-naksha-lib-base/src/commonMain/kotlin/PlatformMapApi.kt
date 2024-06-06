@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package com.here.naksha.lib.base

expect class PlatformMapApi {
    // https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Map
    companion object {
        fun map_clear(map: PlatformMap?)
        fun map_size(map: PlatformMap?): Int
        fun map_get(map: PlatformMap?, key: Any?): Any?
        fun map_set(map: PlatformMap?, key: Any?, value: Any?): Any?
        fun map_contains_key(map: PlatformMap?, key: Any?): Boolean
        fun map_contains_value(map: PlatformMap?, value: Any?): Boolean
        fun map_remove(map: PlatformMap?, key: Any?): Any?
        fun map_iterator(map: PlatformMap?): PlatformIterator<PlatformList?>?
        fun map_key_iterator(map: PlatformMap?): PlatformIterator<Any?>?
        fun map_value_iterator(map: PlatformMap?): PlatformIterator<Any?>?
    }
}