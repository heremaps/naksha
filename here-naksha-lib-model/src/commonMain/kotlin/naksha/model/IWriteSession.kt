@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.base.AtomicInt
import naksha.model.objects.NakshaTx
import kotlin.js.JsExport

/**
 * A write-request.
 */
@JsExport
interface IWriteSession: IReadSession {

    /**
     * The atomic `uid` counter (unique identifier within a transaction).
     *
     * This value is reset to `0` after every [commit] or [rollback].
     * @since 3.0.0
     */
    val uid: AtomicInt

    /**
     * Acquire a storage lock, that is automatically released when the session is [closed][close].
     *
     * @param lockId the unique identifier for the lock.
     * @since 3.0.0
     */
    @v30_experimental
    fun acquireSessionLock(lockId: String): ILock

    /**
     * Acquire a storage lock, that is automatically released when the transaction is [committed][commit], [rolled back][rollback], or when the session is [closed][close].
     *
     * @param lockId the unique identifier for the lock.
     * @since 3.0.0
     */
    @v30_experimental
    fun acquireTransactionLock(lockId: String): ILock

    /**
     * Commit all pending changes in the current transaction. Returns the underlying connection back into the connection pool.
     * @since 2.0.7
     */
    fun commit()

    /**
     * Rollback (revert) all pending changes in the current transaction. Returns the underlying connection back into the connection pool.
     * @since 2.0.7
     */
    fun rollback()

    /**
     * Returns the current transaction, if none is yet started, start a new one. Starting a transaction, requires to allocate a sticky connection, and therefore disables parallel reading.
     * @return the transaction.
     * @since 3.0.0
     */
    fun useTransaction(): NakshaTx

    /**
     * Returns the current transaction, if any is available.
     * @return the current transaction, if any is available; _null_ otherwise.
     * @since 3.0.0
     */
    fun getTransaction(): NakshaTx?
}