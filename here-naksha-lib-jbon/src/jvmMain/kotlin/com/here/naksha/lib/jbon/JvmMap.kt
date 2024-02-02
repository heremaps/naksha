package com.here.naksha.lib.jbon

class JvmMap : INativeMap {
    override fun isMap(any: Any?): Boolean {
        return any is MutableMap<*, *>
    }

    override fun newMap(): Any {
        return HashMap<String, Any?>()
    }

    override fun size(map: Any): Int {
        require(map is Map<*, *>)
        return map.size
    }

    override fun containsKey(map: Any, key: String): Boolean {
        require(map is Map<*, *>)
        return map.containsKey(key)
    }

    override fun get(map: Any, key: String): Any? {
        require(map is Map<*, *>)
        return map[key]
    }

    @Suppress("UNCHECKED_CAST")
    override fun put(map: Any, key: String, value: Any?): Any? {
        require(map is MutableMap<*,*>)
        val casted = map as MutableMap<String, Any?>
        val old = casted[key]
        casted[key] = value
        return old
    }

    @Suppress("UNCHECKED_CAST")
    override fun remove(map: Any, key: String): Any? {
        require(map is MutableMap<*,*>)
        val casted = map as MutableMap<String, Any?>
        val old = casted[key]
        casted.remove(key)
        return old
    }

    @Suppress("UNCHECKED_CAST")
    override fun clear(map: Any) {
        require(map is MutableMap<*,*>)
        val casted = map as MutableMap<String, Any?>
        casted.clear()
    }

    @Suppress("UNCHECKED_CAST")
    override fun keys(map: Any): Array<String> {
        require(map is Map<*,*>)
        val casted = map as Map<String, Any?>
        return casted.keys.toTypedArray()
    }
}