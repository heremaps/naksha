package naksha.model.response

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.jvm.JvmField

/**
 * Error response, means at least one operation failed. Transaction should be rolled back.
 */
@OptIn(ExperimentalJsExport::class)
@JsExport
open class ErrorResponse(
    val reason: NakshaError
) : Response(ERROR_TYPE) {
    fun getErrorMessage(): String = reason.message
//    fun withError(error: XyzError?): ErrorResponse {
//        setError(error)
//        return this
//    }
}