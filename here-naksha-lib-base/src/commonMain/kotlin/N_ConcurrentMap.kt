@file:Suppress("OPT_IN_USAGE")

package com.here.naksha.lib.base

import kotlin.js.JsExport

/**
 * A thread safe map, where the keys must not be _null_ and values must not be _undefined_. Note that JavaScript does not support
 * concurrency by design, therefore the underlying native object will be as an instance of
 * [Map](https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Map).
 */
@JsExport
interface N_ConcurrentMap : N_Object, Iterable<Map.Entry<Any, Any?>> {
    fun size(): Int
    fun clear()
    fun delete(key: Any): Boolean
    operator fun contains(key: Any): Boolean
    operator fun get(key: Any): Any?
    operator fun set(key: Any, value: Any?)
    fun setIfAbsent(key: Any, value: Any?): Any?
    fun compareAndDelete(key: Any, expected: Any?): Boolean
    fun compareAndSet(key: Any, expected: Any?, value: Any?): Boolean
    fun entries() : N_Iterator<N_Array>
    // TODO:
    // fun keys()
    // fun values()
    // fun forEach()
}