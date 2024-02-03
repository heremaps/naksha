package com.here.naksha.lib.jbon

@Suppress("UnsafeCastFromDynamic")
class JsMap : IMap {
    override fun isMap(any: Any?): Boolean {
        return js("any !== undefined && any !== null && !Array.isArray(any)");
    }

    override fun newMap(): Any {
        return js("{}")
    }

    override fun size(map: Any): Int {
        require(isMap(map))
        return js("Object.getOwnPropertyNames(map).length")
    }

    override fun containsKey(map: Any, key: String): Boolean {
        require(isMap(map))
        return js("Object.hasOwn(map, key)")
    }

    override fun get(map: Any, key: String): Any? {
        require(isMap(map))
        return js("Object.hasOwn(map, key) && map[key] || null")
    }

    override fun put(map: Any, key: String, value: Any?): Any? {
        require(isMap(map))
        val old = get(map, key)
        js("map[key]=value;")
        return old
    }

    override fun remove(map: Any, key: String): Any? {
        require(isMap(map))
        val old = get(map, key)
        js("Object.hasOwn(map, key) && delete map[key]")
        return old
    }

    override fun clear(map: Any) {
        require(isMap(map))
        val keys = keys(map)
        for (key in keys) {
            remove(map, key)
        }
    }

    override fun keys(map: Any): Array<String> {
        require(isMap(map))
        return js("Object.keys(map)")
    }
}