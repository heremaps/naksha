package naksha.base

@Suppress("UNCHECKED_CAST")
internal class JvmEmptyIterator<T> : PlatformIterator<T>() {
    companion object {
        internal val emptyResult = PlatformIteratorResult(true, null)
        internal val emptyIterator = JvmEmptyIterator<Any?>()
    }
    override fun next(): PlatformIteratorResult<T> = emptyResult as PlatformIteratorResult<T>
}