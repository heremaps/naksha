@file:Suppress("OPT_IN_USAGE")

package naksha.base

import naksha.base.Base.BaseCompanion.UNDEFINED
import naksha.base.Literal.Literal_C.literal
import naksha.base.PlatformListApi.PlatformListApiCompanion.array_delete
import naksha.base.PlatformListApi.PlatformListApiCompanion.array_get
import naksha.base.PlatformListApi.PlatformListApiCompanion.array_set
import naksha.base.PlatformMapApi.PlatformMapApiCompanion.map_get
import naksha.base.PlatformMapApi.PlatformMapApiCompanion.map_remove
import naksha.base.PlatformMapApi.PlatformMapApiCompanion.map_set
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.jvm.JvmOverloads
import kotlin.math.min

/**
 * The base class for proxies.
 * @param baseObject the object to which this proxy is linked.
 * @since 3.0
 */
@JsExport
abstract class Proxy(baseObject: BaseObject): AbstractProxy(baseObject) {
    override fun hashCode(): Int = this@Proxy.baseObject.hashCode()
    override fun equals(other: Any?): Boolean {
        return this === other || this@Proxy.baseObject == this@Proxy.baseObject.unbox(other)
    }
    override fun toString(): String = this@Proxy.baseObject.toString()

    /**
     * Create a copy of this object.
     *
     * @param recursive _true_ if this method should make a recursive copy; _false_ (default) and a shallow copy is made.
     * @return the (optionally recursive) copy.
     */
    @Suppress("UNCHECKED_CAST")
    fun <SELF : Proxy> copy(recursive: Boolean = false): SELF = Base.copy(baseObject, recursive).proxy(this::class) as SELF

    /**
     * Recursively compare this object with another, checking for values instead of just referential.
     * This is needed because for arrays, the == operation compares whether the arrays are the same object.
     * This will work for any nested structures of maps, lists, and arrays.
     */
    fun contentDeepEquals(other: Proxy): Boolean = BaseUtil.deepEquals(this, other)

    /**
     * Get the property from the given path.
     *
     * @param path the JSON path to query.
     * @return the value at the path or `null`, if the path does not exist or the value is actually `null`.
     * @since 3.0
     */
    @JsName("getPathByList")
    @JvmOverloads
    fun getPath(path: List<Any?>, end: Int = path.size): Any? {
        var current: Any? = this.baseObject
        for (i in 0 until min(path.size, end)) {
            val key = path[i]
            if (key is Literal) {
                if (current !is IMap) return null
                current = current[key]
                continue
            }
            if (key is String) {
                if (current !is IMap) return null
                current = current[literal(key)]
                continue
            }
            if (key is Number) {
                if (current !is IArray) return null
                val index = key.toInt()
                current = current[index]
                continue
            }
            return null
        }
        if (current is BaseArray) return current.proxy(PAnyMap::class)
        if (current is BaseMap) return current.proxy(PAnyArray::class)
        return current
    }

    /**
     * Set the property at the given path. Creates the path, if it does not exist yet. Throws an [RuntimeException] if the path exists, but is of wrong type, for example an array is expected, but an object found or vice versa.
     *
     * @param value the value to set; if [BaseCompanion.UNDEFINED], the value will be removed.
     * @param path the JSON path to mutate.
     * @param end the optional end in the JSON path, defaults to max _(used to select a zero-copy sub-path)_.
     * @return the previous value.
     * @since 3.0
     * @throws NakshaException with [NakshaError.ILLEGAL_ARGUMENT] if the path is invalid and the value is not [BaseCompanion.UNDEFINED].
     */
    @JsName("setPathByList")
    @JvmOverloads
    fun setPath(value: Any?, path: List<Any?>, end: Int = path.size): Any? {
        val isRemove = value == UNDEFINED
        val pathEnd = min(path.size, end)
        if (pathEnd == 0 && isRemove) return UNDEFINED
        val pathLast = pathEnd - 1
        var current: Any = this.baseObject
        for (i in 0 until pathLast) {
            var key = path[i]
            if (key is String) key = literal(key)
            if (key is Literal) {
                if (current !is IMap) {
                    if (isRemove) return null
                    throw illegalArg("Invalid value at key '$key', expected IMutableMap")
                }
                // --- current is IMap ---
                val value = current[key]
                if (value == null) {
                    // The key does not exist, check if we should create a map or list.
                    var next_key = path[i + 1]
                    if (next_key is String) next_key = literal(next_key)
                    if (next_key is Literal) {
                        if (current !is IMutableMap) throw illegalArg("Invalid value at key '$key', expected IMutableMap")
                        val new_map = Base.newMap()
                        map_set(current, key, new_map)
                        current = new_map
                        continue
                    }
                    if (next_key is Number) {
                        val new_list = Base.newList()
                        map_set(current, key, new_list)
                        current = new_list
                        continue
                    }
                    // The next key is invalid
                    if (isRemove) return null
                    throw illegalArg("Invalid key in path: '$next_key' at position ${i+1}")
                }
                current = value
                continue
            }
            if (key is Number) {
                if (current !is PlatformList) {
                    if (isRemove) return null
                    throw illegalArg("Invalid value at key '$key', expected array")
                }
                // --- current is PlatformList ---
                val index: Int = key.toInt()
                val value = array_get(current, index)
                if (value == null) {
                    // The index does not exist, check if we should create a map or list.
                    val next_key = path[i + 1]
                    if (next_key is String) {
                        val new_map = Base.newMap()
                        array_set(current, index, new_map)
                        current = new_map
                        continue
                    }
                    if (next_key is Number) {
                        val new_list = Base.newList()
                        array_set(current, index, new_list)
                        current = new_list
                        continue
                    }
                    // The next key is invalid
                    if (isRemove) return null
                    throw illegalArg("Invalid key in path: '$next_key' at position ${i+1}")
                }
                current = value
                continue
            }
            if (isRemove) return null
            throw illegalArg("Invalid key in path: '$key' at position $i")
        }
        val key = path[pathLast]
        if (key is String) {
            if (current !is PlatformMap) {
                if (isRemove) return null
                throw illegalArg("Invalid value at key '$key', expected object")
            }
            val oldValue = map_get(current, key)
            if (value == UNDEFINED) map_remove(current, key) else map_set(current, key, value)
            return oldValue
        }
        if (key is Number) {
            if (current !is PlatformList) {
                if (isRemove) return null
                throw illegalArg("Invalid value at key '$key', expected array")
            }
            val index = key.toInt()
            val oldValue = array_get(current, index)
            if (value == UNDEFINED) array_delete(current, index) else array_set(current, index, value)
            return oldValue
        }
        if (isRemove) return null
        throw illegalArg("Invalid key in path: '$key' at position $pathLast")
    }

    /**
     * Get the property from the given path.
     *
     * @param path the JSON path to query.
     * @return the value at the path or `null`, if the path does not exist or the value is actually `null`.
     * @since 3.0
     */
    @JsName("getPathByArray")
    fun getPath(path: Array<Any?>, length: Int = path.size): Any? {
        var current: Any? = this.platformObject()
        for (i in 0 until length) {
            if (i >= path.size) return null
            val key = path[i]
            if (key is String) {
                if (current !is PlatformMap) return null
                current = map_get(current, key)
                continue
            }
            if (key is Number) {
                if (current !is PlatformList) return null
                val index = key.toInt()
                current = array_get(current, index)
                continue
            }
            return null
        }
        if (current is PlatformMap) return Base.proxy(current, PAnyMap::class)
        if (current is PlatformList) return Base.proxy(current, PAnyArray::class)
        return current
    }

    /**
     * Set the property at the given path. Creates the path, if it does not exist yet. Throws an [RuntimeException] if the path exists, but is of wrong type, for example an array is expected, but an object found or vice versa.
     *
     * @param value the value to set; if [BaseCompanion.UNDEFINED], the value will be removed.
     * @param path the JSON path to mutate.
     * @param end the optional end in the JSON path, defaults to max _(used to select a zero-copy sub-path)_.
     * @return the previous value.
     * @since 3.0
     * @throws NakshaException with [NakshaError.ILLEGAL_ARGUMENT] if the path is invalid and the value is not [BaseCompanion.UNDEFINED].
     */
    @JsName("setPathByArray")
    fun setPath(value: Any?, path: Array<Any?>, end: Int = path.size): Any? {
        val isRemove = value == UNDEFINED
        val pathEnd = min(path.size, end)
        if (pathEnd == 0 && isRemove) return UNDEFINED
        val pathLast = pathEnd - 1
        var current: Any = this.platformObject()
        for (i in 0 until pathLast) {
            val key = path[i]
            if (key is String) {
                if (current !is PlatformMap) {
                    if (isRemove) return null
                    throw illegalArg("Invalid value at key '$key', expected object")
                }
                // --- current is PlatformMap ---
                val value = map_get(current, key)
                if (value == null) {
                    // The key does not exist, check if we should create a map or list.
                    val next_key = path[i + 1]
                    if (next_key is String) {
                        val new_map = Base.newMap()
                        map_set(current, key, new_map)
                        current = new_map
                        continue
                    }
                    if (next_key is Number) {
                        val new_list = Base.newList()
                        map_set(current, key, new_list)
                        current = new_list
                        continue
                    }
                    // The next key is invalid
                    if (isRemove) return null
                    throw illegalArg("Invalid key in path: '$next_key' at position ${i+1}")
                }
                current = value
                continue
            }
            if (key is Number) {
                if (current !is PlatformList) {
                    if (isRemove) return null
                    throw illegalArg("Invalid value at key '$key', expected array")
                }
                // --- current is PlatformList ---
                val index: Int = key.toInt()
                val value = array_get(current, index)
                if (value == null) {
                    // The index does not exist, check if we should create a map or list.
                    val next_key = path[i + 1]
                    if (next_key is String) {
                        val new_map = Base.newMap()
                        array_set(current, index, new_map)
                        current = new_map
                        continue
                    }
                    if (next_key is Number) {
                        val new_list = Base.newList()
                        array_set(current, index, new_list)
                        current = new_list
                        continue
                    }
                    // The next key is invalid
                    if (isRemove) return null
                    throw illegalArg("Invalid key in path: '$next_key' at position ${i+1}")
                }
                current = value
                continue
            }
            if (isRemove) return null
            throw illegalArg("Invalid key in path: '$key' at position $i")
        }
        val key = path[pathLast]
        if (key is String) {
            if (current !is PlatformMap) {
                if (isRemove) return null
                throw illegalArg("Invalid value at key '$key', expected object")
            }
            val oldValue = map_get(current, key)
            map_set(current, key, value)
            return oldValue
        }
        if (key is Number) {
            if (current !is PlatformList) {
                if (isRemove) return null
                throw illegalArg("Invalid value at key '$key', expected array")
            }
            val index = key.toInt()
            val oldValue = array_get(current, index)
            array_set(current, index, value)
            return oldValue
        }
        if (isRemove) return null
        throw illegalArg("Invalid key in path: '$key' at position $pathLast")
    }

    /**
     * Get the property from the given path.
     *
     * @param path the JSON path to query.
     * @return the value at the path or `null`, if the path does not exist or the value is actually `null`.
     * @since 3.0
     */
    fun getPath(vararg path: Any): Any? {
        var current: Any? = this.platformObject()
        for (key in path) {
            if (key is String) {
                if (current !is PlatformMap) return null
                current = map_get(current, key)
                continue
            }
            if (key is Number) {
                if (current !is PlatformList) return null
                val index = key.toInt()
                current = array_get(current, index)
                continue
            }
            return null
        }
        if (current is PlatformMap) return Base.proxy(current, PAnyMap::class)
        if (current is PlatformList) return Base.proxy(current, PAnyArray::class)
        return current
    }

    /**
     * Set the property at the given path. Creates the path, if it does not exist yet. Throws an [RuntimeException] if the path exists, but is of wrong type, for example an array is expected, but an object found or vice versa.
     *
     * @param value the value to set; if [BaseCompanion.UNDEFINED], the value will be removed.
     * @param path the JSON path to mutate.
     * @return the previous value.
     * @since 3.0
     * @throws NakshaException with [NakshaError.ILLEGAL_ARGUMENT] if the path is invalid and the value is not [BaseCompanion.UNDEFINED].
     */
    fun setPath(value: Any?, vararg path: Any): Any? {
        val isRemove = value == UNDEFINED
        val pathEnd = path.size
        if (pathEnd == 0 && isRemove) return UNDEFINED
        val pathLast = pathEnd - 1
        var current: Any = this.platformObject()
        for (i in 0 until pathLast) {
            val key = path[i]
            if (key is String) {
                if (current !is PlatformMap) {
                    if (isRemove) return UNDEFINED
                    throw illegalArg("Invalid value at key '$key', expected object")
                }
                // --- current is PlatformMap ---
                val value = map_get(current, key)
                if (value == null) {
                    // The key does not exist, check if we should create a map or list.
                    val next_key = path[i + 1]
                    if (next_key is String) {
                        val new_map = Base.newMap()
                        map_set(current, key, new_map)
                        current = new_map
                        continue
                    }
                    if (next_key is Number) {
                        val new_list = Base.newList()
                        map_set(current, key, new_list)
                        current = new_list
                        continue
                    }
                    // The next key is invalid
                    if (isRemove) return UNDEFINED
                    throw illegalArg("Invalid key in path: '$next_key' at position ${i+1}")
                }
                current = value
                continue
            }
            if (key is Number) {
                if (current !is PlatformList) {
                    if (isRemove) return UNDEFINED
                    throw illegalArg("Invalid value at key '$key', expected array")
                }
                // --- current is PlatformList ---
                val index: Int = key.toInt()
                val value = array_get(current, index)
                if (value == null) {
                    // The index does not exist, check if we should create a map or list.
                    val next_key = path[i + 1]
                    if (next_key is String) {
                        val new_map = Base.newMap()
                        array_set(current, index, new_map)
                        current = new_map
                        continue
                    }
                    if (next_key is Number) {
                        val new_list = Base.newList()
                        array_set(current, index, new_list)
                        current = new_list
                        continue
                    }
                    // The next key is invalid
                    if (isRemove) return UNDEFINED
                    throw illegalArg("Invalid key in path: '$next_key' at position ${i+1}")
                }
                current = value
                continue
            }
            if (isRemove) return UNDEFINED
            throw illegalArg("Invalid key in path: '$key' at position $i")
        }
        val key = path[pathLast]
        if (key is String) {
            if (current !is PlatformMap) {
                if (isRemove) return UNDEFINED
                throw illegalArg("Invalid value at key '$key', expected object")
            }
            val oldValue = map_get(current, key)
            map_set(current, key, value)
            return oldValue
        }
        if (key is Number) {
            if (current !is PlatformList) {
                if (isRemove) return UNDEFINED
                throw illegalArg("Invalid value at key '$key', expected array")
            }
            val index = key.toInt()
            val oldValue = array_get(current, index)
            array_set(current, index, value)
            return oldValue
        }
        if (isRemove) return UNDEFINED
        throw illegalArg("Invalid key in path: '$key' at position $pathLast")
    }
}