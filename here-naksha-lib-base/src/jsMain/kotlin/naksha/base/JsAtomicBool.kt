@file:Suppress("OPT_IN_USAGE")

package naksha.base

/**
 * An atomic boolean implementation for JavaScript.
 */
@JsExport
class JsAtomicBool internal constructor(private var _value: Boolean) : AtomicBool {
    override fun get(): Boolean = _value

    override fun set(value: Boolean) {
        _value = value
    }

    override fun compareAndSet(expect: Boolean, update: Boolean): Boolean {
        if (_value == expect) {
            _value = update
            return true
        }
        return false
    }
}