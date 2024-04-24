package com.here.naksha.lib.nak

class JvmPArrayIterator(private val arr: JvmPArray) : PIterator<Int, Any?> {
    private var index = -1

    @Suppress("NOTHING_TO_INLINE")
    private inline fun last() = arr.size - 1

    override fun loadNext(): Boolean = if (index <= last()) ++index <= last() else false

    override fun isLoaded(): Boolean = index <= last()

    override fun getKey(): Int {
        require(index <= last())
        return index
    }

    override fun getValue(): Any? {
        require(index <= last())
        return arr[index]
    }
}