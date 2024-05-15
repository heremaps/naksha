package com.here.naksha.lib.base

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * Error response, means at least one operation failed. Transaction should be rolled back.
 */
@OptIn(ExperimentalJsExport::class)
@JsExport
class NakErrorResponse(val error: String, val message: String, val id: String? = null) : NakResponse() {
}