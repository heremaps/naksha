package naksha.base

@Suppress("UNCHECKED_CAST")
internal class JvmMapEntryIterator(private val map: Map<*,*>) : PlatformIterator<PlatformList>() {
    private val it = JvmMapKeyIterator(map)
    private val result = PlatformIteratorResult(false, JvmList(null, null))

    override fun next(): PlatformIteratorResult<PlatformList> {
        val nextKey = it.next()
        if (!nextKey.done) {
            val key = nextKey.value
            val value = map[key]
            result.value!![0] = key
            result.value!![1] = value
            result.done = false
        } else {
            result.value = null
            result.done = true
        }
        return result as PlatformIteratorResult<PlatformList>
    }
}