package com.here.naksha.lib.base

internal class JvmMapValueIterator<V>() : PlatformIterator<V?>() {
    private var it:  Iterator<V?>? = null
    private lateinit var result: PlatformIteratorResult<V?>

    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN", "UNCHECKED_CAST")
    constructor(map: PlatformMap?) : this() {
        it = if (map is java.util.Map<*,*>) (map as java.util.Map<*,V>).values().iterator() else null
        result = PlatformIteratorResult(true, null)
    }

    constructor(map: Map<*,V>?) : this() {
        it = map?.values?.iterator()
        result = PlatformIteratorResult(true, null)
    }

    override fun next(): PlatformIteratorResult<V?> {
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