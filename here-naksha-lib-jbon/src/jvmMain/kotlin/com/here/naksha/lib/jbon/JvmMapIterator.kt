package com.here.naksha.lib.jbon

class JvmMapIterator(val map: Map<String, Any?>) : IMapIterator {
    val it = map.iterator()
    var current: Map.Entry<String, Any?>? = null

    override fun hasNext(): Boolean = it.hasNext()

    override fun next(): Boolean {
        if (!it.hasNext()) return false
        current = it.next()
        return true
    }

    override fun key(): String = current?.key ?: throw IllegalStateException()

    override fun value(): Any? = current?.value
}