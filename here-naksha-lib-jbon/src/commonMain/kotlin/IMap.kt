@file:OptIn(ExperimentalJsExport::class)
@file:Suppress("NOTHING_TO_INLINE")

package com.here.naksha.lib.jbon

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@JsExport
interface IMap

inline fun newMap() = Jb.map.newMap()
inline fun isMap(any:Any?) : Boolean = Jb.map.isMap(any)
inline fun asMap(any:Any?) : IMap = Jb.map.asMap(any)
inline fun IMap.size() : Int = Jb.map.size(this)
inline fun IMap.containsKey(key:String) : Boolean = Jb.map.containsKey(this, key)
inline operator fun IMap.contains(key:String) : Boolean = Jb.map.containsKey(this, key)
@Suppress("UNCHECKED_CAST")
inline operator fun <T> IMap.get(key:String) : T? = Jb.map.get(this, key) as T?
inline operator fun IMap.set(key:String, value:Any?) : Any? = Jb.map.put(this, key, value)
inline operator fun IMap.iterator() : Iterator<Map.Entry<String,Any?>> = JbMapIterator(this)
inline fun IMap.put(key:String, value:Any?) : Any? = Jb.map.put(this, key, value)
inline fun IMap.clear() = Jb.map.clear(this)
inline fun IMap.getAny(key:String) : Any? = Jb.map.get(this, key)
inline fun IMap.overrideBy(map: IMap) : IMap = Jb.map.overrideBy(this, map)
