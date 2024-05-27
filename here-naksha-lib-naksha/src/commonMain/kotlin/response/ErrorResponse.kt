package com.here.naksha.lib.base.response

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * Error response, means at least one operation failed. Transaction should be rolled back.
 */
@OptIn(ExperimentalJsExport::class)
@JsExport
class ErrorResponse(
    val reason: NakshaError
) : Response(ERROR_TYPE) {
    override fun size(): Int = 0
}