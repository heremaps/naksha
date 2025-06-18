@file:Suppress("OPT_IN_USAGE")

package naksha.base

import kotlin.js.JsExport

@JsExport
internal class MapProxyKeyIterator<K, V> internal constructor(map: MapProxy<K, V>): MutableIterator<K> {
    private val _it = MapProxyEntryIterator(map, true)

    override fun hasNext(): Boolean = _it.hasNext()

    override fun next(): K = _it.next().key

    override fun remove() = _it.remove()
}