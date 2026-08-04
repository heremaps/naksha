package naksha.base

/**
 * JS actual of [BaseMap]. In JavaScript this class truly extends the native `Map`,
 * so `jsMap instanceof Map` evaluates to `true`.
 *
 * The generated JavaScript is equivalent to:
 * ```javascript
 * class JsMap extends Map { constructor() { super(); } }
 * ```
 * @since 3.0
 */
@JsExport
actual class BaseMap actual constructor() : BaseObject(), IMutableMap {

    /** Do only access using [map] ! */
    private var _map: dynamic = null

    /**
     * The underlying JavaScript `Map`.
     * @since 3.0
     */
    var map: dynamic
        get() {
            var map = _map
            if (map == null) {
                map = js("new Map()")
                _map = map
            }
            return map
        }
        set(value) {
            if (!js("value instanceof Map").unsafeCast<Boolean>()) {
                throw illegalArg("The 'value' must be a JavaScript Map instance")
            }
            _map = value
        }

    actual override fun <R> forEach(initialValue: R?, action: (key: Literal, value: Any?, result: R?) -> R?): R? {

    }

    actual val size: Int
        get() = _map?.size.unsafeCast<Int?>() ?: 0

    actual operator fun get(key: Any): Any? {
        val m = _map ?: return null
        return unbox(m.get(key).unsafeCast<Any?>())
    }

    actual operator fun set(key: Any, value: Any?) {
        map.set(key, box(value))
    }

    actual fun containsKey(key: Any): Boolean {
        val m = _map ?: return false
        return m.has(key).unsafeCast<Boolean>()
    }

    actual fun delete(key: Any): Boolean {
        val m = _map ?: return false
        return m.delete(key).unsafeCast<Boolean>()
    }

    actual fun remove(key: Any): Any? {
        val m = _map ?: return null
        val value = unbox(m.get(key).unsafeCast<Any?>())
        m.delete(key)
        return value
    }

    actual fun putIfAbsent(key: Any, value: Any?): Any? {
        val m = map
        if (m.has(key).unsafeCast<Boolean>()) return unbox(m.get(key).unsafeCast<Any?>())
        m.set(key, box(value))
        return null
    }

    actual fun compareAndSet(key: Any, expected: Any?, newValue: Any?): Boolean {
        val m = map
        // absent key and null value are both treated as null
        val current = unbox(if (m.has(key).unsafeCast<Boolean>()) m.get(key).unsafeCast<Any?>() else null)
        val matches = if (expected == null) current == null else current == expected
        if (!matches) return false
        m.set(key, box(newValue))
        return true
    }

    actual fun removeIf(key: Any, expectedValue: Any): Any? {
        val m = _map ?: return null  // absent — success
        if (!m.has(key).unsafeCast<Boolean>()) return null  // absent — success
        val current = unbox(m.get(key).unsafeCast<Any?>())
        if (current == expectedValue) { m.delete(key); return null }  // match — success
        return current  // mismatch — return current value
    }

    actual fun replace(key: Any, expectedValue: Any, newValue: Any?): Boolean {
        val m = _map ?: return false
        if (!m.has(key).unsafeCast<Boolean>()) return false
        if (unbox(m.get(key).unsafeCast<Any?>()) != expectedValue) return false
        m.set(key, box(newValue))
        return true
    }

    actual fun deleteIf(key: Any, expectedValue: Any): Boolean {
        val m = _map ?: return false
        if (!m.has(key).unsafeCast<Boolean>()) return false
        if (unbox(m.get(key).unsafeCast<Any?>()) != expectedValue) return false
        m.delete(key)
        return true
    }

    actual fun clear() {
        _map?.clear()
    }
}