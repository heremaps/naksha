package com.here.naksha.lib.base

class JvmListIterator(val list: PlatformList) : PlatformIterator<Any?>()  {

    private val it: MutableIterator<Any?>? = if (list is JvmList) list.iterator() else null
    private val result: PlatformIteratorResult<Any?> = PlatformIteratorResult(true, null)

    override fun next(): PlatformIteratorResult<Any?> {
        if (it?.hasNext() == true) {
            result.value =  it.next()
            result.done = false
        } else {
            result.done = true
            result.value = null
        }
        return result
    }
}