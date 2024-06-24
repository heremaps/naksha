@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.model.request.ReadRequest
import naksha.model.request.ResultRow
import naksha.model.response.Response
import kotlin.js.JsExport

/**
 * A storage session that can only read. Each session is backed by a single storage connection with a single transaction.
 * @since 2.0.7
 */
@JsExport
interface IReadSession: ISession {

    fun execute(request: ReadRequest): Response

    fun executeParallel(request: ReadRequest): Response

    fun getFeatureById(id: String): ResultRow?

    fun getFeaturesByIds(ids: List<String>): Map<String, String>

    /**
     * Returns the Naksha context bound to this read-connection.
     *
     * @return the Naksha context bound to this read-connection.
     * @since 2.0.7
     */
    fun getNakshaContext(): NakshaContext
}