package com.here.naksha.lib.base

@Suppress("UNCHECKED_CAST")
internal class JvmMapEntryIterator() : PlatformIterator<PlatformList>() {
    private var it:  Iterator<Map. Entry<Any?, Any?>>? = null
    private lateinit var result: PlatformIteratorResult<JvmList>

    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
    constructor(map: PlatformMap?) : this() {
        if (map is java.util.Map<*,*>) {
            it = map.entrySet().iterator()
            result = PlatformIteratorResult(false, JvmList())
        } else {
            it = null
            result = PlatformIteratorResult(true, null)
        }
    }

    constructor(map: Map<*,*>?) : this() {
        if (map != null) {
            it = map.iterator()
            result = PlatformIteratorResult(false, JvmList())
        } else {
            it = null
            result = PlatformIteratorResult(true, null)
        }
    }


    override fun next(): PlatformIteratorResult<PlatformList> {
        val it = this.it
        val result = this.result
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