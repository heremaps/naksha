package naksha.base

import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.TimeUnit.NANOSECONDS
import java.util.concurrent.locks.ReentrantLock

actual open class Lock actual constructor(fair: Boolean): AutoCloseable {
    private val reentrantLock = ReentrantLock(fair)

    actual fun lock(): Lock {
        reentrantLock.lock()
        return this
    }

    actual fun tryLock(waitMillis: Int, waitNanos: Int): Boolean {
        return if (waitMillis <= 0 && waitNanos <= 0)
               reentrantLock.tryLock()
          else reentrantLock.tryLock(MILLISECONDS.toNanos(waitMillis.toLong()) + waitNanos, NANOSECONDS)
    }

    actual fun unlock() {
        reentrantLock.unlock()
    }

    actual override fun close() {
        unlock()
    }

    actual val holdCount: Int
        get() = reentrantLock.holdCount

    actual val isHeldByCurrentThread: Boolean
        get() = reentrantLock.isHeldByCurrentThread

    actual val isLocked: Boolean
        get() = reentrantLock.isLocked
}