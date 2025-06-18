@file:Suppress("OPT_IN_USAGE", "NON_EXPORTABLE_TYPE")

package naksha.base

import kotlin.collections.MutableMap.MutableEntry
import kotlin.js.JsExport

@JsExport
internal class MapProxyMutableEntrySet<K, V> internal constructor(
    private val map: MapProxy<K,V>,
    private val reuseMapEntry: Boolean
): MutableSet<MutableEntry<K, V?>> {
    override val size: Int
        get() = map.size

    override fun clear() {
        map.clear()
    }

    override fun isEmpty(): Boolean = map.isEmpty()

    override fun containsAll(elements: Collection<MutableEntry<K, V?>>): Boolean {
        for (element in elements) {
            if (!contains(element)) return false
        }
        return true
    }

    override fun contains(element: MutableEntry<K, V?>): Boolean
        = map.containsKey(element.key) && map[element.key] === element.value

    override fun addAll(elements: Collection<MutableEntry<K, V?>>): Boolean {
        var modified = false
        for (element in elements) {
            if (add(element)) modified = true
        }
        return modified
    }

    override fun add(element: MutableEntry<K, V?>): Boolean {
        val key = element.key
        val value = element.value
        if (!map.containsKey(key) || map[key] !== value) {
            map[key] = value
            return true
        }
        return false
    }

    override fun iterator(): MutableIterator<MutableEntry<K, V?>> = MapProxyEntryIterator(map, reuseMapEntry)

    override fun retainAll(elements: Collection<MutableEntry<K, V?>>): Boolean {
        var modified = false
        val it = iterator()
        while (it.hasNext()) {
            val entry = it.next()
            if (!elements.contains(entry)) {
                map.remove(entry.key)
                modified = true
            }
        }
        return modified
    }

    override fun removeAll(elements: Collection<MutableEntry<K, V?>>): Boolean {
        var modified = false
        for (element in elements) {
            if (contains(element)) {
                map.remove(element.key)
                modified = true
            }
        }
        return modified
    }

    override fun remove(element: MutableEntry<K, V?>): Boolean {
        if (map.containsKey(element.key)) {
            map.remove(element.key)
            return true
        }
        return false
    }
}