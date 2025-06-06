@file:Suppress("OPT_IN_USAGE")

package naksha.base

import kotlin.js.JsExport

@Suppress("NON_EXPORTABLE_TYPE")
@JsExport
internal class MapProxyMutableKeySet<K,V> internal constructor(private val map: MapProxy<K,V>): MutableSet<K> {
    override val size: Int
        get() = map.size

    override fun clear() {
        map.clear()
    }

    override fun isEmpty(): Boolean = map.isEmpty()

    override fun containsAll(elements: Collection<K>): Boolean {
        for (element in elements) {
            if (!contains(element)) return false
        }
        return true
    }

    override fun contains(element: K): Boolean = map.containsKey(element)

    override fun addAll(elements: Collection<K>): Boolean {
        var modified = false
        for (element in elements) {
            if (add(element)) modified = true
        }
        return modified
    }

    override fun add(element: K): Boolean {
        if (!map.containsKey(element)) {
            map[element] = null
            return true
        }
        return false
    }

    @Suppress("UNCHECKED_CAST")
    override fun iterator(): MutableIterator<K> = MapProxyKeyIterator(map as MapProxy<K, Any>)

    override fun retainAll(elements: Collection<K>): Boolean {
        var modified = false
        val it = iterator()
        while (it.hasNext()) {
            val key = it.next()
            if (map.containsKey(key)) {
                map.remove(key)
                modified = true
            }
        }
        return modified
    }

    override fun removeAll(elements: Collection<K>): Boolean {
        var modified = false
        for (element in elements) {
            if (remove(element)) modified = true
        }
        return modified
    }

    override fun remove(element: K): Boolean {
        if (map.containsKey(element)) {
            map.remove(element)
            return true
        }
        return false
    }
}