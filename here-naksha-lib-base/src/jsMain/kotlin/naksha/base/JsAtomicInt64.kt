package naksha.base

/**
 * An atomic int 64 implementation for JavaScript.
 */
@JsExport
class JsAtomicInt64 internal constructor(private var _value: Int64): AtomicInt64 {
    override fun get(): Int64 = _value

    override fun set(value: Int64) {
        _value = value
    }

    override fun compareAndSet(expect: Int64, update: Int64): Boolean {
        if (_value == expect) {
            _value = update
            return true
        }
        return false
    }

    override fun getAndAdd(value: Int64): Int64 {
        val old = _value
        _value += value
        return old
    }

    override fun addAndGet(value: Int64): Int64 {
        _value += value
        return _value
    }
}