package com.here.naksha.lib.jbon

@Suppress("UnsafeCastFromDynamic")
class JsMapApi : IMapApi {
    companion object {
        private val smt = JsMap()
        fun isMap(any: Any?): Boolean =
                js("any !== undefined && any !== null && typeof any === 'object' && !Array.isArray(any)")

        fun toMap(any: Any?): IMap {
            if (any is JsMap) return any
            if (isMap(any)) throw IllegalArgumentException("require object")
            val t = this.smt
            // TODO: Use setPrototype(any, Object.getPrototypeOf(t))
            return js("Object.assign(any,Object.getPrototypeOf(t))")
        }
    }

    override fun isMap(any: Any?): Boolean = Companion.isMap(any)

    override fun asMap(any: Any?): IMap = Companion.toMap(any)

    override fun newMap(): IMap {
        return js("{}")
    }

    override fun size(map: IMap): Int {
        require(isMap(map))
        return js("Object.getOwnPropertyNames(map).length")
    }

    override fun containsKey(map: IMap, key: String): Boolean {
        require(isMap(map))
        return js("Object.hasOwn(map, key)")
    }

    override fun get(map: IMap, key: String): Any? {
        require(isMap(map))
        return js("Object.hasOwn(map, key) && map[key] || null")
    }

    override fun put(map: IMap, key: String, value: Any?): Any? {
        require(isMap(map))
        val old = get(map, key)
        js("map[key]=value;")
        return old
    }

    override fun remove(map: IMap, key: String): Any? {
        require(isMap(map))
        val old = get(map, key)
        js("Object.hasOwn(map, key) && delete map[key]")
        return old
    }

    override fun clear(map: IMap) {
        require(isMap(map))
        val keys = keys(map)
        for (key in keys) {
            remove(map, key)
        }
    }

    override fun keys(map: IMap): Array<String> {
        require(isMap(map))
        return js("Object.keys(map)")
    }

    override fun iterator(map: IMap): IMapIterator {
        TODO("Not yet implemented")
    }
}