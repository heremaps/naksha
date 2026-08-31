package naksha.base

/**
 * An atomic int 64 implementation for JavaScript.
 */
@JsExport
class JsAtomicInt64 internal constructor(private var _value: Long): AtomicInt64 {
    override fun get(): Long = _value

    override fun set(value: Long) {
        _value = value
    }

    override fun compareAndSet(expect: Long, update: Long): Boolean {
        if (_value == expect) {
            _value = update
            return true
        }
        return false
    }

    override fun getAndAdd(value: Long): Long {
        val old = _value
        _value += value
        return old
    }

    override fun addAndGet(value: Long): Long {
        _value += value
        return _value
    }
}
