@file:Suppress("OPT_IN_USAGE")

package naksha.base

import kotlin.js.JsExport

@Suppress("NON_EXPORTABLE_TYPE")
@JsExport
internal class MapProxyMutableValueCollection<K,V> internal constructor(private val map: MapProxy<K, V>): MutableCollection<V?> {
    override val size: Int
        get() = map.size

    override fun clear() {
        map.clear()
    }

    override fun isEmpty(): Boolean = map.isEmpty()

    override fun iterator(): MutableIterator<V?> = MapProxyMutableValueIterator(map)

    override fun retainAll(elements: Collection<V?>): Boolean {
        var modified = false
        val it = map.entries.iterator()
        while (it.hasNext()) {
            val entry = it.next()
            var keep = false
            for (e in elements) {
                if (entry.value == e) {
                    keep = true
                    break
                }
            }
            if (!keep) {
                it.remove()
                modified = true
            }
        }
        return modified
    }

    override fun removeAll(elements: Collection<V?>): Boolean {
        var modified = false
        val it = map.entries.iterator()
        while (it.hasNext()) {
            val entry = it.next()
            for (e in elements) {
                if (entry.value == e) {
                    it.remove()
                    modified = true
                    break
                }
            }
        }
        return modified
    }

    override fun remove(element: V?): Boolean {
        var modified = false
        val it = map.entries.iterator()
        while (it.hasNext()) {
            val entry = it.next()
            if (entry.value == element) {
                it.remove()
                modified = true
            }
        }
        return modified
    }

    override fun containsAll(elements: Collection<V?>): Boolean {
        val it = map.entries.iterator()
        val found = BooleanArray(elements.size)
        while (it.hasNext()) {
            val entry = it.next()
            var i = 0
            for (e in elements) {
                if (entry.value == e) {
                    found[i] = true
                }
                i++
            }
        }
        for (e in found) {
            if (!e) return false
        }
        return true
    }

    override fun contains(element: V?): Boolean {
        val it = map.entries.iterator()
        while (it.hasNext()) {
            val entry = it.next()
            if (entry.value == element) return true
        }
        return false
    }

    override fun addAll(elements: Collection<V?>): Boolean {
        throw UnsupportedOperationException("Can't add values without key into a map")
    }

    override fun add(element: V?): Boolean {
        throw UnsupportedOperationException("Can't add values without key into a map")
    }
}