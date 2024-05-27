package com.here.naksha.lib.base.response

import com.here.naksha.lib.naksha.request.ResultRow
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * Success response, means all operations succeeded, and it's safe to commit transaction.
 */
@OptIn(ExperimentalJsExport::class)
@JsExport
class SuccessResponse(
    /**
     * A handler (available if requested) to fetch more (next) rows from DB.
     */
    val handle: String? = null,
    val rows: Array<ResultRow>
) : Response(SUCCESS_TYPE) {
    override fun size(): Int = rows.size
}