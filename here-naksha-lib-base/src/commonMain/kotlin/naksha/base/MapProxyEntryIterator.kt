@file:Suppress("OPT_IN_USAGE")

package naksha.base

import naksha.base.PlatformListApi.PlatformListApiCompanion.array_get
import naksha.base.PlatformListApi.PlatformListApiCompanion.array_get_length
import naksha.base.PlatformMapApi.PlatformMapApiCompanion.map_iterator
import kotlin.collections.MutableMap.MutableEntry
import kotlin.js.JsExport

@JsExport
internal class MapProxyEntryIterator<K, V> internal constructor(private val map: MapProxy<K,V>): MutableIterator<MutableEntry<K, V?>> {
    private val _it: PlatformIterator<PlatformList> = map_iterator(map.platformObject())
    private var _next: PlatformIteratorResult<PlatformList> = _it.next()
    private var _current: PlatformIteratorResult<PlatformList>? = null
    private var _key: K? = null
    private var _entry: MapProxyMutableEntry<K, V>? = null

    override fun hasNext(): Boolean = !_next.done

    @Suppress("UNCHECKED_CAST", "NON_EXPORTABLE_TYPE")
    override fun next(): MutableEntry<K, V?> {
        val current = _next
        if (current.done) throw NoSuchElementException()

        val list = current.value ?: throw NoSuchElementException()
        val len = array_get_length(list)
        if (len < 1) throw NoSuchElementException()
        val key = array_get(list, 0)
        if (!map.keyType.isInstance(key)) throw illegalState("Invalid key ('$key') found in map")
        _current = current
        _key = key as K
        _next = _it.next()

        var entry = _entry
        if (entry == null) {
            entry = MapProxyMutableEntry(map, key)
            _entry = entry
        } else {
            entry.key = key
        }
        return entry
    }

    override fun remove() {
        if (_current == null) throw NoSuchElementException()
        val key = _key ?: throw NoSuchElementException()
        map.remove(key)
        _current = null
        _key = null
    }
}