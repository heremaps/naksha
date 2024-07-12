package naksha.base

import kotlin.js.JsExport

/**
 * A reentrant lock, to be used like:
 * ```kotlin
 * val lock = Platform.newLock()
 * ...
 * lock.acquire().use {
 *   // Do something with the lock.
 * }
 * if (lock.tryLock()) lock.use {
 *   // Do something with the lock.
 * }
 * ```
 */
@Suppress("OPT_IN_USAGE")
@JsExport
interface PlatformLock : AutoCloseable {
    /**
     * Wait for the lock and then enter it.
     * @return this.
     */
    fun acquire(): PlatformLock

    /**
     * Tries to acquire the lock.
     * @param waitMillis the time to wait in milliseconds; if _null_, then not waiting at all, so either it is instantly available or
     * locking will fail.
     * @return this, if the lock was acquired; _null_ if locking failed due to timeout.
     */
    fun tryAcquire(waitMillis: Int64? = null): Boolean

    /**
     * Release the lock.
     */
    override fun close()
}