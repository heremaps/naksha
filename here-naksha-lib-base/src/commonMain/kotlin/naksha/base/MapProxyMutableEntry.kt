@file:Suppress("OPT_IN_USAGE")

package naksha.base

import kotlin.collections.MutableMap.MutableEntry
import kotlin.js.JsExport

@JsExport
internal class MapProxyMutableEntry<K, V> internal constructor(private val map: MapProxy<K,V>, key: K) : MutableEntry<K, V?> {
    override var key: K = key
        internal set
    override val value: V?
        get() = map[key]

    override fun setValue(newValue: V?): V? {
        val old = map[key]
        map[key] = newValue
        return old
    }

    override fun equals(other: Any?): Boolean {
        if (other !is MapProxyMutableEntry<*, *>) return false
        return key == other.key && value == other.value
    }
    override fun hashCode(): Int = key.hashCode() xor value.hashCode()
    override fun toString(): String = "MapProxyMutableEntry(key=$key, value=$value)"
}