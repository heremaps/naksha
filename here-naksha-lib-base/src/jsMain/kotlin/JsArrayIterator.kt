package com.here.naksha.lib.base

class JsArrayIterator(private val a: dynamic) : N_Iterator<Int, Any?> {
    private val last = a.length.unsafeCast<Int>()
    private var i = 0

    override fun loadNext(): Boolean = if (i <= last) { ++i <= last } else false

    override fun isLoaded(): Boolean = i <= last

    override fun getKey(): Int {
        require(i <= last)
        return i
    }

    override fun getValue(): Any? {
        require(i <= last)
        return a[i]
    }
}