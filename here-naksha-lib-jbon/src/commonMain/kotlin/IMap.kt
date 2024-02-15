@file:OptIn(ExperimentalJsExport::class)
@file:Suppress("NOTHING_TO_INLINE")

package com.here.naksha.lib.jbon

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@JsExport
interface IMap

inline fun newMap() = JbSession.map.newMap()
inline fun isMap(any:Any?) : Boolean = JbSession.map.isMap(any)
inline fun asMap(any:Any?) : IMap = JbSession.map.asMap(any)
inline fun IMap.size() : Int = JbSession.map.size(this)
inline fun IMap.containsKey(key:String) : Boolean = JbSession.map.containsKey(this, key)
inline operator fun IMap.contains(key:String) : Boolean = JbSession.map.containsKey(this, key)
inline operator fun IMap.get(key:String) : Any? = JbSession.map.get(this, key)
inline operator fun IMap.set(key:String, value:Any?) : Any? = JbSession.map.put(this, key, value)
inline operator fun IMap.iterator() : Iterator<Map.Entry<String,Any?>> = JbMapIterator(this)
inline fun IMap.put(key:String, value:Any?) : Any? = JbSession.map.put(this, key, value)
inline fun IMap.clear() = JbSession.map.clear(this)
