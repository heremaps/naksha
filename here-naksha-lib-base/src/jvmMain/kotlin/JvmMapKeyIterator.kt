package com.here.naksha.lib.base

internal class JvmMapKeyIterator<K>() : PlatformIterator<K>() {
    private var it:  Iterator<K>? = null
    private lateinit var result: PlatformIteratorResult<K>

    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN", "UNCHECKED_CAST")
    constructor(map: PlatformMap?) : this() {
        it = if (map is java.util.Map<*,*>) (map as java.util.Map<K,*>).keySet().iterator() else null
        result = PlatformIteratorResult(true, null)
    }

    constructor(map: Map<K,*>?) : this() {
        it = map?.keys?.iterator()
        result = PlatformIteratorResult(false, null)
    }

    override fun next(): PlatformIteratorResult<K> {
        val it = this.it
        val result = this.result
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