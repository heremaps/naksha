package naksha.base

import naksha.base.Platform.PlatformCompanion.useNewJson
import java.util.LinkedHashMap

open class JvmMap() : JvmObject(), MutableMap<Any, Any?>, PlatformMap {

    constructor(vararg entries: Any?) : this() {
        var i = 0
        while (i < entries.size) {
            val key = entries[i++]
            val value = if (i < entries.size) entries[i++] else null
            @Suppress("LeakingThis")
            if (key is Symbol) setSymbol(key, value)
            else if (key != null) set(key, value)
            else throw IllegalArgumentException("The key must not be null at index ${i-1}")
        }
    }

    /**
     * The key-value pairs; if any.
     */
    @JvmField
    internal var map: LinkedHashMap<Any, Any?>? = null

    /**
     * The key-value pairs; as long as only `String`'s are used as keys and [Platform.useNewJson] is enabled.
     */
    @JvmField
    internal var jsonMap: JsonMap? = null

    /**
     * Returns the backing [LinkedHashMap]; if currently backed by a [JsonMap], copies the values from the [JsonMap] into a [LinkedHashMap].
     * @return the [LinkedHashMap] backing this map.
     */
    private fun map(): LinkedHashMap<Any, Any?> {
        val jsonMap = this.jsonMap
        if (jsonMap != null) {
            val map = LinkedHashMap<Any, Any?>()
            map.putAll(jsonMap)
            this.jsonMap = null
            this.map = map
            return map
        }
        var map = this.map
        if (map == null) {
            map = LinkedHashMap()
            this.map = map
        }
        return map
    }

    /**
     * Returns the backing [JsonMap], if available and enabled.
     * @return the backing [JsonMap], if available and enabled.
     */
    private fun jsonMap(): JsonMap? {
        val map = this.map
        if (map != null) return null
        var jsonMap = this.jsonMap
        if (jsonMap == null) {
            if (!useNewJson()) return null
            jsonMap = JsonMap()
            this.jsonMap = jsonMap
        }
        return jsonMap
    }

    /**
     * Tests if this map contains the given key.
     * @param key The key to lookup.
     * @return _true_ if the map contains the given [key]; _false_ otherwise.
     */
    open operator fun contains(key: Any?): Boolean { // is redirected from containsKey!
        if (this.map == null && this.jsonMap == null) return false
        val jm = jsonMap()
        if (jm != null) return jm.containsKey(key)
        return if (key == null) false else map().containsKey(key)
    }

    /**
     * Searches for the first occurrence of the given value in the map and returns the key.
     * @param value The value to search for.
     * @return The key or _null_, if the value is not stored in the map.
     */
    fun keyOf(value: Any?): Any? {
        if (this.map == null && this.jsonMap == null) return null
        val jm = jsonMap()
        if (jm != null) {
            val i = jm.indexOfValue(value, 0)
            return if (i < 0) null else jm.map[i]
        }
        val map = map()
        for ((k, v) in map) if (value == v) return k
        return null
    }

    /**
     * Returns the value assigned to the given key.
     * @param key The key to query.
     * @return The value or _null_.
     */
    override operator fun get(key: Any): Any? {
        if (this.map == null && this.jsonMap == null) return null
        val jm = jsonMap()
        if (jm != null) return jm[key]
        return if (map?.containsKey(key) == true) map?.get(key) else null
    }

    /**
     * Removes the given key.
     * @param key The key to remove.
     * @return _true_ if the key was removed; _false_ otherwise.
     */
    open fun delete(key: Any?): Boolean {
        if (this.map == null && this.jsonMap == null) return false
        val jm = jsonMap()
        if (jm != null) return if (key is String) jm.delete(key, false) else false
        if (key != null && map?.containsKey(key) == true) {
            map?.remove(key)
            return true
        }
        return false
    }

    /**
     * Set the value of the key.
     * @param key The key to set.
     * @param value The value to set.
     * @return The previous value or _null_.
     */
    open operator fun set(key: Any, value: Any?): Any? {
        val jm = jsonMap()
        if (jm != null && key is String) return jm.set(key, value)

        // Note: This is incompatible with JavaScript default behavior, but makes Kotlin code better!
        //       We do not want properties with the value undefined!
        if (value === undefined) return delete(key)
        val old = get(key)
        map()[key] = value
        return old
    }

    override fun isEmpty(): Boolean = size == 0
    override fun putAll(from: Map<out Any, Any?>) {
        val jm = jsonMap()
        if (jm != null) {
            jm.ensure(from.size)
            for ((k, v) in from) {
                if (k !is String) {
                    // We need to add a key that is no string, fallback to old LinkedHashMap!
                    this.map()
                    this.putAll(from)
                    return
                }
                jm.put(k, v)
            }
            return
        }
        map().putAll(from)
    }

    override fun remove(key: Any): Any? {
        if (this.map == null && this.jsonMap == null) return null
        return (jsonMap() ?: map()).remove(key)
    }

    override fun containsKey(key: Any): Boolean = contains(key)

    override fun containsValue(value: Any?): Boolean {
        if (this.map == null && this.jsonMap == null) return false
        val jm = jsonMap()
        if (jm != null) return jm.indexOfValue(value, 0) >= 0
        val map = map()
        for (key in map.keys) if (map[key] == value) return true
        return false
    }

    override fun put(key: Any, value: Any?): Any? {
        if (key is String) {
            val jm = jsonMap()
            if (jm != null) return jm.put(key, value)
        }
        val map = map()
        return map.put(key, value)
    }

    @Suppress("UNCHECKED_CAST")
    override val entries: MutableSet<MutableMap.MutableEntry<Any, Any?>>
        get() = (jsonMap()?.entries ?: map().entries) as MutableSet<MutableMap.MutableEntry<Any, Any?>>
    @Suppress("UNCHECKED_CAST")
    override val keys: MutableSet<Any>
        get() = (jsonMap()?.keys ?: map().keys) as MutableSet<Any>
    override val size: Int
        get() {
            if (this.map == null && this.jsonMap == null) return 0
            return jsonMap()?.size ?: map().size
        }
    override val values: MutableCollection<Any?>
        get() = jsonMap()?.values ?: map().values

    override fun clear() {
        this.jsonMap = null
        this.map = null
    }
}
