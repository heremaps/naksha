package naksha.base

// JavaScript is single threaded, therefore a reentrant lock is useless, we simply make a dummy implementation.
actual open class Lock actual constructor(fair: Boolean): AutoCloseable {
    private var _holdCount: Int = 0

    actual fun lock(): Lock {
        _holdCount++
        return this
    }

    actual fun tryLock(waitMillis: Int, waitNanos: Int): Boolean {
        _holdCount++
        return true
    }

    actual fun unlock() {
        if (_holdCount == 0) throw illegalState("The lock is not hold")
        _holdCount--
    }

    actual override fun close() {
        unlock()
    }

    actual val holdCount: Int
        get() = _holdCount

    actual val isHeldByCurrentThread: Boolean
        get() = _holdCount > 0

    actual val isLocked: Boolean
        get() = _holdCount > 0
}