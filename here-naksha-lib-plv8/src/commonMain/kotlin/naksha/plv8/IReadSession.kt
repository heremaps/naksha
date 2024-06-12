@file:Suppress("OPT_IN_USAGE")

package naksha.plv8

import naksha.model.NakshaContext
import naksha.model.response.Response
import naksha.model.request.Request
import naksha.model.request.ResultRow
import kotlin.js.JsExport

@JsExport
abstract class IReadSession(
    context: NakshaContext,
    stmtTimeout: Int,
    lockTimeout: Int
) : ISession(context, stmtTimeout, lockTimeout) {

    abstract fun execute(request: Request): Response

    abstract fun executeParallel(request: Request): Response

    abstract fun getFeatureById(id: String): ResultRow?

    abstract fun getFeaturesByIds(ids: List<String>): Map<String, String>
}