@file:Suppress("OPT_IN_USAGE")

package naksha.base

import kotlin.js.JsExport

@JsExport
internal class MapProxyMutableValueIterator<K, V> internal constructor(map: MapProxy<K,V>) : MutableIterator<V?> {
    private val _it = MapProxyEntryIterator(map)

    override fun hasNext(): Boolean = _it.hasNext()

    override fun next(): V? = _it.next().value

    override fun remove() = _it.remove()
}