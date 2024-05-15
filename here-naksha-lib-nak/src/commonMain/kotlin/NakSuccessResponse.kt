package com.here.naksha.lib.base

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.jvm.JvmStatic

/**
 * Success response, means all operations succeeded, and it's safe to commit transaction.
 */
@OptIn(ExperimentalJsExport::class)
@JsExport
class NakSuccessResponse(
        val handle: String? = null,
        val rows: Array<NakReadRow>
): NakResponse()