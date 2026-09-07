package naksha.base

import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.locks.ReentrantLock

class JvmLock : ReentrantLock(), PlatformLock {
    companion object {
        private const val MIN_WAIT = 1L
    }

    override fun acquire(): PlatformLock {
        super.lock()
        return this
    }

    override fun tryAcquire(waitMillis: Long?): Boolean
        = if (waitMillis == null || waitMillis < MIN_WAIT) super.tryLock() else super.tryLock(waitMillis, MILLISECONDS)

    override fun close() {
        super.unlock()
    }
}
