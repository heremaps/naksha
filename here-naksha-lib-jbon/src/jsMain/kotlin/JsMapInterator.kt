@file:OptIn(ExperimentalJsExport::class)

package com.here.naksha.lib.jbon

@JsExport
class JsMapInterator(private val map : IMap, private val keys: Array<String>) : IMapIterator {
    private var i = 0
    private lateinit var key : String
    private var value : Any? = null

    override fun hasNext(): Boolean {
        return i < keys.size
    }

    override fun next(): Boolean {
        if (i >= keys.size) return false
        key = keys[i]
        value = map[key]
        i++
        return true
    }

    override fun key(): String = key

    override fun value(): Any? = value
}