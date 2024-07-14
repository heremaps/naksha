@file:Suppress("OPT_IN_USAGE")

package naksha.base

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
@JsExport
actual class PlatformMapApi {
    // https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Map
    actual companion object PlatformMapApiCompanion {
        private val EMPTY_MAP = js("new Map()")

        @JsStatic
        actual fun map_get(map: PlatformMap?, key: Any?): Any? {
            if (map == null) return null
            val m = map.asDynamic()
            return m.get(key)
        }

        @JsStatic
        actual fun map_set(map: PlatformMap?, key: Any?, value: Any?): Any? {
            require(map != null) { "map_set: Map must be a object, but is $map" }
            val m = map.asDynamic()
            val old = m.get(key)
            m.set(key, value)
            return old
        }

        @JsStatic
        actual fun map_remove(map: PlatformMap?, key: Any?): Any? {
            require(map != null) { "map_remove: Map must be a object, but is $map" }
            val m = map.asDynamic()
            val old = m.get(key)
            m.delete(key)
            return old
        }

        @JsStatic
        actual fun map_clear(map: PlatformMap?) {
            require(map != null) { "map_clear: Map must be a object, but is $map" }
            map.asDynamic().clear()
        }

        @JsStatic
        actual fun map_size(map: PlatformMap?): Int {
            if (map == null) return 0
            return map.asDynamic().size.unsafeCast<Int>()
        }

        @JsStatic
        actual fun map_contains_key(map: PlatformMap?, key: Any?): Boolean {
            if (map == null) return false
            return map.asDynamic().has(key).unsafeCast<Boolean>()
        }

        @JsStatic
        actual fun map_contains_value(map: PlatformMap?, value: Any?): Boolean {
            if (map == null || map.asDynamic().size.unsafeCast<Int>() == 0) return false
            val it = map_value_iterator(map)
            var entry = it.next()
            while (!entry.done) {
                if (value == entry.value) return true
                entry = it.next()
            }
            return false
        }

        @JsStatic
        actual fun map_iterator(map: PlatformMap?): PlatformIterator<PlatformList> {
            if (map == null) EMPTY_MAP.entries().unsafeCast<PlatformIterator<PlatformList>>()
            return map.asDynamic().entries().unsafeCast<PlatformIterator<PlatformList>>()
        }

        @JsStatic
        actual fun map_key_iterator(map: PlatformMap?): PlatformIterator<Any> {
            if (map == null) EMPTY_MAP.keys().unsafeCast<PlatformIterator<Any>>()
            return map.asDynamic().keys().unsafeCast<PlatformIterator<Any>>()
        }

        @JsStatic
        actual fun map_value_iterator(map: PlatformMap?): PlatformIterator<Any?> {
            if (map == null) EMPTY_MAP.values().unsafeCast<PlatformIterator<Any?>>()
            return map.asDynamic().values().unsafeCast<PlatformIterator<Any?>>()
        }
    }
}