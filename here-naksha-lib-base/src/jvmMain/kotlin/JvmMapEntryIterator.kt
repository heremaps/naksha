package com.here.naksha.lib.base

@Suppress("UNCHECKED_CAST")
internal class JvmMapEntryIterator(map: PlatformMap?) : PlatformIterator<PlatformList>() {
    private val it: MutableIterator<MutableMap. MutableEntry<Any, Any?>>?
    private val result: PlatformIteratorResult<JvmList>
    init {
        if (map is JvmMap) {
            it = map.entrySet().iterator()
            result = PlatformIteratorResult(false, JvmList())
        } else {
            it = null
            result = PlatformIteratorResult(true, null)
        }
    }
    override fun next(): PlatformIteratorResult<PlatformList> {
        if (it?.hasNext() == true) {
            val next = it.next()
            result.value!![0] = next.key
            result.value!![1] = next.value
            result.done = false
        } else {
            result.done = true
            result.value = null
        }
        return result as PlatformIteratorResult<PlatformList>
    }
}