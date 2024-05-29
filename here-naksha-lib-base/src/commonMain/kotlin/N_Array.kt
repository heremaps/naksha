@file:Suppress("OPT_IN_USAGE")

package com.here.naksha.lib.base

import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * A flexible (re-sizable) array.
 */
@JsExport
@JsName("Array")
interface N_Array : N_Object, Iterable<Any?> {
    // TODO: Copy missing code over from old classes
    fun size(): Int
    @JsName("at")
    operator fun get(i: Int): Any?
    operator fun set(i: Int, value: Any?)
    fun splice(start: Int = 0, deleteCount: Int = 0, vararg add: Any?): N_Array
    fun entries() : N_Iterator<Any?>

    /**
     * Appends values to an array.
     * @param elements The elements to append.
     * @return The new length of the array.
     */
    fun push(vararg elements: Any?): Int
    // TODO:
    // forEach()
    // pop()
    // push()
    // ...
}
