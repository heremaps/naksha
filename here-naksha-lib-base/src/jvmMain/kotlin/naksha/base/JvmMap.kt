package naksha.base

import java.util.LinkedHashMap
import kotlin.reflect.KClass
import kotlin.reflect.full.primaryConstructor

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
    internal var map: LinkedHashMap<Any, Any?>? = null

    /**
     * Returns the internally used map, if none exists yet, creates a new one.
     * @return The internally used map.
     */
    fun map(): LinkedHashMap<Any, Any?> {
        var p = map
        if (p == null) {
            p = LinkedHashMap()
            map = p
        }
        return p
    }

    /**
     * Tests if this map contains the given key.
     * @param key The key to lookup.
     * @return _true_ if the map contains the given [key]; _false_ otherwise.
     */
    open operator fun contains(key: Any?): Boolean = if (key == null) false else map?.containsKey(key) == true

    /**
     * Searches for the first occurrence of the given value in the map and returns the key.
     * @param value The value to search for.
     * @return The key or _null_, if the value is not stored in the map.
     */
    fun keyOf(value: Any?): Any? {
        val properties = this.map ?: return null
        for ((k, v) in properties) if (value == v) return k
        return null
    }

    /**
     * Returns the value assigned to the given key.
     * @param key The key to query.
     * @return The value or _null_.
     */
    override operator fun get(key: Any): Any? = if (map?.containsKey(key) == true) map?.get(key) else null

    /**
     * Removes the given key.
     * @param key The key to remove.
     * @return _true_ if the key was removed; _false_ otherwise.
     */
    open fun delete(key: Any?): Boolean {
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
        // Note: This is incompatible with JavaScript default behavior, but makes Kotlin code better!
        //       We do not want properties with the value undefined!
        if (value === undefined) return delete(key)
        val old = get(key)
        map()[key] = value
        return old
    }

    override fun isEmpty(): Boolean = size == 0
    override fun putAll(from: kotlin.collections.Map<out Any, Any?>) = map().putAll(from)

    override fun remove(key: Any): Any? = map?.remove(key)

    override fun containsKey(key: Any): Boolean = contains(key)

    override fun containsValue(value: Any?): Boolean {
        val p = map ?: return false
        for (key in p.keys) if (p[key] == value) return true
        return false
    }

    override fun put(key: Any, value: Any?): Any? = set(key, value)

    override val entries: MutableSet<MutableMap.MutableEntry<Any, Any?>>
        get() = map().entries
    override val keys: MutableSet<Any>
        get() = map().keys
    override val size: Int
        get() = map?.size ?: 0
    override val values: MutableCollection<Any?>
        get() = map().values

    override fun clear() {
        map?.clear()
    }
}
