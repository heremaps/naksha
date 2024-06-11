@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.model.response.Response
import com.here.naksha.lib.naksha.request.Request
import com.here.naksha.lib.naksha.request.ResultRow
import kotlin.js.JsExport

// FIXME TODO move it to proper library

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