package com.here.naksha.lib.jbon

@Suppress("UNCHECKED_CAST")
class JvmMapApi : IMapApi {
    override fun isMap(any: Any?): Boolean = any is IMap

    override fun asMap(any: Any?): IMap = if (isMap(any)) {
        any as IMap
    } else {
        any?.let { JvmEnv.get().convert(it, JvmMap::class.java) } as IMap
    }

    override fun newMap(): IMap = JvmMap()

    private fun read(map: IMap): Map<String, Any?> {
        require(map is Map<*, *>)
        return map as Map<String, Any?>
    }

    private fun modify(map: IMap): MutableMap<String, Any?> {
        require(map is MutableMap<*, *>)
        return map as MutableMap<String, Any?>
    }

    override fun size(map: IMap): Int = read(map).size
    override fun containsKey(map: IMap, key: String): Boolean = read(map).containsKey(key)
    override fun get(map: IMap, key: String): Any? = read(map)[key]
    override fun put(map: IMap, key: String, value: Any?): Any? {
        val m = modify(map)
        val old = m[key]
        m[key] = value
        return old
    }

    override fun remove(map: IMap, key: String): Any? = modify(map).remove(key)
    override fun clear(map: IMap) = modify(map).clear()
    override fun keys(map: IMap): Array<String> = read(map).keys.toTypedArray()
    override fun iterator(map: IMap): IMapIterator = JvmMapIterator(read(map))
    override fun overrideBy(map1: IMap, map2: IMap): IMap {
        val merged = newMap()
        (map1 as JvmMap + map2 as JvmMap).iterator().forEach { merged.put(it.key, it.value) }
        return merged
    }
}