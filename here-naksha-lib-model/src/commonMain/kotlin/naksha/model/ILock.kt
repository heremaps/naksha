@file:Suppress("OPT_IN_USAGE")

package naksha.model

import kotlin.js.JsExport

/**
 * A lock that is kept alive as long as the application lives. The lock is released when [close] is called, or when the JVM is shutdown, or crashes, or when all references to the lock instance are garbage collected (which will trigger an emergency lock release, causing an error log entry).
 *
 * This is an application level lock, not bound to a single thread, it works across instances.
 */
@JsExport
@v30_experimental
interface ILock: AutoCloseable {
    /**
     * The storage that provided the lock.
     * @since 3.0
     */
    val storage: IStorage

    /**
     * Tests if the lock is closed.
     * @return _true_ if the lock is closed (has been released).
     * @since 3.0
     */
    fun isClosed(): Boolean
}