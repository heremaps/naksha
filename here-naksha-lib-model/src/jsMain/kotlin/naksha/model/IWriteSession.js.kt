package naksha.model

import naksha.model.objects.NakshaTransaction

/**
 * A write-request.
 */
@OptIn(ExperimentalJsExport::class)
@JsExport
actual interface IWriteSession : IReadSession {
    /**
     * Acquire a storage lock, that is automatically released when the session is [closed][close].
     *
     * @param lockId the unique identifier for the lock.
     * @since 3.0.0
     */
    @v30_experimental
    actual fun acquireSessionLock(lockId: String): ILock

    /**
     * Acquire a storage lock, that is automatically released when the transaction is [committed][commit], [rolled back][rollback], or when the session is [closed][close].
     *
     * @param lockId the unique identifier for the lock.
     * @since 3.0.0
     */
    @v30_experimental
    actual fun acquireTransactionLock(lockId: String): ILock
    /**
     * Commit all pending changes in the current transaction. Returns the underlying connection back into the connection pool.
     * @since 2.0.7
     */
    actual fun commit()

    /**
     * Rollback (revert) all pending changes in the current transaction. Returns the underlying connection back into the connection pool.
     * @since 2.0.7
     */
    actual fun rollback()

    /**
     * Returns the current transaction, if none is yet started, start a new one. Starting a transaction, requires to allocate a sticky connection, and therefore disables parallel reading.
     * @return the transaction.
     * @since 3.0.0
     */
    actual fun useTransaction(): NakshaTransaction

    /**
     * Returns the current transaction, if any is available.
     * @return the current transaction, if any is available; _null_ otherwise.
     * @since 3.0.0
     */
    actual fun getTransaction(): NakshaTransaction?

}