package naksha.base

import naksha.base.Platform.PlatformCompanion.longToInt64
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.locks.ReentrantLock

class JvmLock : ReentrantLock(), PlatformLock {
    companion object {
        private val MIN_WAIT = longToInt64(1)
    }

    override fun acquire(): PlatformLock {
        super.lock()
        return this
    }

    override fun tryAcquire(waitMillis: Int64?): Boolean
        = if (waitMillis == null || waitMillis.toLong() < 1L ) super.tryLock() else super.tryLock(waitMillis.toLong(), MILLISECONDS)

    override fun close() {
        super.unlock()
    }
}