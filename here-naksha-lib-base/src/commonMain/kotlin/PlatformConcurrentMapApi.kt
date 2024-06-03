@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package com.here.naksha.lib.base

internal expect class PlatformConcurrentMapApi {
    // A mix between:
    // https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Map
    // https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/ConcurrentMap.html
    companion object {
        fun cmap_setIfAbsent(key: Any, value: Any?): Any?
        fun cmap_compareAndDelete(key: Any, expected: Any?): Boolean
        fun cmap_compareAndSet(key: Any, expected: Any?, value: Any?): Boolean
    }
}