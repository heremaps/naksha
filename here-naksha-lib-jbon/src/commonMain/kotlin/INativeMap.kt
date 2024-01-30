@file:OptIn(ExperimentalJsExport::class)

package com.here.naksha.lib.jbon

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * An API that grants access to native maps.
 */
@JsExport
interface INativeMap {

    /**
     * Tests if the given object is a native map.
     * @param map The native map.
     * @return true if the given map is a native map; false otherwise.
     */
    fun isInstance(map: Any): Boolean

    fun size(map: Any): Int
    fun containsKey(map: Any): Boolean
    fun get(map: Any, key: String): Any
    fun put(map: Any, key: String, value: Any): Any
    fun remove(map: Any, key: String): Any
    fun clear(map: Any)
    fun keys(map:Any) : Array<String>
}