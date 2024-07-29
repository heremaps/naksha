@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.model.request.ReadRequest
import naksha.model.request.Request
import naksha.model.request.WriteRequest
import naksha.model.request.Response
import kotlin.js.JsExport

/**
 * When a session is opened, it is bound to the context in which the session shall operate. The read session will acquire a connection from a connection pools when read is called, and release the connections instantly after the read is done. The write session will acquire a connection, when the first read or write operation is done, and stick with it until `commit`, `rollback` or [close] invoked. The dictionary manager will grab an idle read or read/write connection on demand, and release it to the connection pool as soon as possible.
 */
@JsExport
interface ISession : AutoCloseable {
    /**
     * The socket timeout in milliseconds.
     * @since 3.0.0
     */
    var socketTimeout: Int

    /**
     * The statement timeout in milliseconds.
     * @since 3.0.0
     */
    var stmtTimeout: Int

    /**
     * The lock timeout in milliseconds.
     * @since 3.0.0
     */
    var lockTimeout: Int

    /**
     * The map the session currently operates on.
     *
     * - If changing the map, may throw [NakshaError.UNSUPPORTED_OPERATION], if changing the map is not supported.
     * @since 3.0.0
     */
    var map: String

    /**
     * Execute the given [Request].
     *
     * The read-only session will only be able to execute [ReadRequest]'s and throw an [NakshaError.UNSUPPORTED_OPERATION], when a [WriteRequest] is provided.
     * @param request the request to execute.
     * @return the response.
     * @since 2.0.7
     */
    fun execute(request: Request<*>): Response

    /**
     * Execute the given [Request] in parallel, if possible, otherwise fallback to a normal [execute].
     *
     * **Warning**: **There is a minor risk to create a broken state in the storage!** This depends on the exact implementation, but it needs to be an accepted risk, when using `executeParallel`. Please read the extended documentation for the concrete implementation how it will handle parallel executions.
     *
     * For example in `lib-psql`, even after all requests have been executed successfully, committing may fail partially, for example when only one connection aborts or the server crashes in the middle of the operation, while having committed already some connections, with others not yet to be done.
     * @since 3.0.0
     */
    fun executeParallel(request: Request<*>): Response

    /**
     * Tests if the session is closed.
     * @return _true_ if the session is closed.
     * @since 3.0.0
     */
    fun isClosed(): Boolean

    /**
     * Closing a session will roll back the underlying connection, and then return it to their connection pool. After closing a session
     * any further methods invocation will raise an [IllegalStateException].
     * @since 2.0.7
     */
    override fun close()
}
