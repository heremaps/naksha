@file:Suppress("OPT_IN_USAGE")

package com.here.naksha.lib.base

import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * A not thread safe map, where the keys must not be _null_ and values must not be _undefined_.
 */
@JsExport
@JsName("Map")
interface N_Map : N_Object, Iterable<Map.Entry<Any, Any?>> {
    // TODO: Copy missing code over from old classes
    fun size(): Int
    fun clear()
    fun delete(key: Any): Boolean

    @JsName("has")
    operator fun contains(key: Any): Boolean
    operator fun get(key: Any): Any?
    operator fun set(key: Any, value: Any?)

    /**
     * Returns an iterator where for each key-value pair an array is returned as value with the first element in the array being
     * the key and the second element in the array being the value.
     */
    fun entries() : N_Iterator<N_Array>
    // TODO:
    // fun forEach()
    // fun keys()
    // fun values()
}
