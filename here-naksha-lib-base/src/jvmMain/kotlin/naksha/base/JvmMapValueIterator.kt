package naksha.base

internal class JvmMapValueIterator<V>(private val map: Map<*,V>) : PlatformIterator<V?>() {
    private val it = JvmMapKeyIterator(map)
    private val result = PlatformIteratorResult<V?>(false, null)

    override fun next(): PlatformIteratorResult<V?> {
        val nextKey = it.next()
        if (!nextKey.done) {
            val key = nextKey.value
            val value = map[key]
            result.value = value
            result.done = false
        } else {
            result.value = null
            result.done = true
        }
        return result
    }
}