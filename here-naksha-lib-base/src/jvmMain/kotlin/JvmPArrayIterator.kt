package com.here.naksha.lib.base

class JvmPArrayIterator(private val arr: JvmPList) : PlatformIterator<Int, Any?> {
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