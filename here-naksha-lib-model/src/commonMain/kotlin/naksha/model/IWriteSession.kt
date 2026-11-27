@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.base.Version
import naksha.base.unsupportedOp
import naksha.model.objects.NakshaTx
import naksha.model.request.ReadRequest
import naksha.model.request.Request
import naksha.model.request.Response
import naksha.model.request.WriteRequest
import kotlin.js.JsExport

/**
 * A write session.
 * @since 3.0
 */
@JsExport
interface IWriteSession: IReadSession {
    /**
     * Returns the [MemberProcessorMap] for this session.
     *
     * Use the map to register, remove, or inspect [IMemberProcessor] instances for individual member processing. Processors are invoked in the order in which they were added.
     * @return the member processor map.
     * @since 3.0
     */
    val processors: MemberProcessorMap

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
     * Returns the current transaction, if none is yet started, start a new one.
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

    /**
     * The version extracted from [useTransaction].
     *
     * Reading the member causes the transaction to be started; this requires a request to the physical storage. Ones the transaction has started, the same version is returned for every invocation.
     * @since 3.0
     */
    val version: Version
        get() = useTransaction().version

    /**
     * Execute the given [WriteRequest].
     *
     * After the first write request has been executed, the underlying storage connection is fixed and all reads have to use the same connection to stick to the isolated visibility until [commit] or [rollback] is invoked. This potentially reduces read speed, therefore all reads should have been finished before calling this method.
     * @param request the request to execute.
     * @return the response.
     * @since 2.0.7
     */
    fun executeWrite(request: WriteRequest): Response

    @Deprecated("Please use executeRead or executeWrite", level = DeprecationLevel.WARNING)
    override fun execute(request: Request): Response {
        if (request is ReadRequest) return this.executeRead(request)
        if (request is WriteRequest) return this.executeWrite(request)
        throw unsupportedOp("Unsupported request type: ${request::class.simpleName}")
    }
}