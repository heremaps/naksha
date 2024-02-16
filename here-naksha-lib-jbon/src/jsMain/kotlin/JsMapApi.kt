@file:OptIn(ExperimentalJsExport::class)

package com.here.naksha.lib.jbon

@Suppress("UnsafeCastFromDynamic", "unused", "NON_EXPORTABLE_TYPE")
@JsExport
class JsMapApi : IMapApi {
    val mapTemplate = JsMap()

    override fun isMap(any: Any?): Boolean {
        return js("any !== undefined && any !== null && typeof any === 'object' && !Array.isArray(any)")
    }

    override fun asMap(any: Any?): IMap {
        if (any is JsMap) return any
        if (!isMap(any)) throw IllegalArgumentException("require object")
        return js("Object.setPrototypeOf(any,Object.getPrototypeOf(this.mapTemplate))")
    }

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
        return JsMapInterator(map, keys(map))
    }
}