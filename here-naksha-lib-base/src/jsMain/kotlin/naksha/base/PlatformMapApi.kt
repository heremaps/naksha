package naksha.base

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual class PlatformMapApi {
    // https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Map
    @OptIn(ExperimentalJsStatic::class)
    actual companion object {
        private val EMPTY_MAP = js("new Map()")

        @JsStatic
        actual fun map_get(map: PlatformMap?, key: Any?): Any? {
            if (map === null || map === undefined) return null
            val m = map.asDynamic()
            return m.get(key)
        }

        @JsStatic
        actual fun map_set(map: PlatformMap?, key: Any?, value: Any?): Any? {
            require(map !== null && map !== undefined) { "map_set: Map must be a object, but is $map" }
            val m = map.asDynamic()
            val old = m.get(key)
            m.set(key, value)
            return old
        }

        @JsStatic
        actual fun map_remove(map: PlatformMap?, key: Any?): Any? {
            require(map !== null && map !== undefined) { "map_remove: Map must be a object, but is $map" }
            val m = map.asDynamic()
            val old = m.get(key)
            m.delete(key)
            return old
        }

        actual fun map_clear(map: PlatformMap?) {
            require(map !== null && map !== undefined) { "map_clear: Map must be a object, but is $map" }
            map.asDynamic().clear()
        }

        actual fun map_size(map: PlatformMap?): Int {
            if (map === null || map === undefined) return 0
            return map.asDynamic().size.unsafeCast<Int>()
        }

        actual fun map_contains_key(map: PlatformMap?, key: Any?): Boolean {
            if (map === null || map === undefined) return false
            return map.asDynamic().has(key).unsafeCast<Boolean>()
        }

        actual fun map_contains_value(map: PlatformMap?, value: Any?): Boolean {
            if (map === null || map === undefined || map.asDynamic().size.unsafeCast<Int>() == 0) return false
            val it = map_value_iterator(map)
            var entry = it.next()
            while (!entry.done) {
                if (value == entry.value) return true
                entry = it.next()
            }
            return false
        }

        actual fun map_iterator(map: PlatformMap?): PlatformIterator<PlatformList> {
            if (map === null || map === undefined) EMPTY_MAP.entries().unsafeCast<PlatformIterator<PlatformList>>()
            return map.asDynamic().entries().unsafeCast<PlatformIterator<PlatformList>>()
        }

        actual fun map_key_iterator(map: PlatformMap?): PlatformIterator<Any> {
            if (map === null || map === undefined) EMPTY_MAP.keys().unsafeCast<PlatformIterator<Any>>()
            return map.asDynamic().keys().unsafeCast<PlatformIterator<Any>>()
        }

        actual fun map_value_iterator(map: PlatformMap?): PlatformIterator<Any?> {
            if (map === null || map === undefined) EMPTY_MAP.values().unsafeCast<PlatformIterator<Any?>>()
            return map.asDynamic().values().unsafeCast<PlatformIterator<Any?>>()
        }
    }
}