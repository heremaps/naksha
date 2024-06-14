@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.model.response.Response
import naksha.model.request.Request
import naksha.model.request.ResultRow
import kotlin.js.JsExport

@JsExport
interface IReadSession: ISession {

    fun execute(request: Request): Response

    fun executeParallel(request: Request): Response

    fun getFeatureById(id: String): ResultRow?

    fun getFeaturesByIds(ids: List<String>): Map<String, String>
}