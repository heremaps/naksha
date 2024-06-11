package naksha.model.response

import naksha.model.response.NakshaError
import naksha.model.response.Response
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * Error response, means at least one operation failed. Transaction should be rolled back.
 */
@OptIn(ExperimentalJsExport::class)
@JsExport
open class ErrorResponse(
    val reason: NakshaError
) : Response(ERROR_TYPE) {
    override fun size(): Int = 0
}