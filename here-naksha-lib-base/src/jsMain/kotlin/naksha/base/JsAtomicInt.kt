@file:Suppress("OPT_IN_USAGE")

package naksha.base

/**
 * An atomic integer implementation for JavaScript.
 */
@JsExport
class JsAtomicInt internal constructor(private var _value: Int) : AtomicInt {
    override fun get(): Int = _value

    override fun set(value: Int) {
        _value = value
    }

    override fun compareAndSet(expect: Int, update: Int): Boolean {
        if (_value == expect) {
            _value = update
            return true
        }
        return false
    }

    override fun getAndAdd(value: Int): Int {
        val old = _value
        _value += value
        return old
    }

    override fun addAndGet(value: Int): Int {
        _value += value
        return _value
    }
}