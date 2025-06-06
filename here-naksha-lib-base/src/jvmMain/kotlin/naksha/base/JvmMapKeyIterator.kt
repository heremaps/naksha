package naksha.base

import java.util.ConcurrentModificationException

internal class JvmMapKeyIterator<K>(private val map: Map<K,*>) : PlatformIterator<K>() {
    private val result: PlatformIteratorResult<K> = PlatformIteratorResult(false, null)
    private var pos: Int = 0

    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN", "UNCHECKED_CAST")
    private fun reset(): Iterator<K> {
        // Create an iterator.
        val it: Iterator<K> = if (map is JvmMap) {
            (map as java.util.Map<K,*>).keySet().iterator()
        } else {
            map.keys.iterator()
        }
        // Restore old position, if necessary.
        val old_pos = this.pos
        this.pos = 0
        for (i in 0 until old_pos) {
            if (it.hasNext()) next() else break
        }

        // Return correctly positioned iterator.
        return it
    }

    private var it: Iterator<K>? = reset()

    override fun next(): PlatformIteratorResult<K> {
        val it = this.it
        val result = this.result
        try {
            if (it?.hasNext() == true) {
                result.value = it.next()
                result.done = false
                pos++
            } else {
                this.it = null
                result.value = null
                result.done = true
            }
            return result
        } catch (e: ConcurrentModificationException) {
            this.it = reset()
            return next()
        }
    }
}