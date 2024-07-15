@file:Suppress("OPT_IN_USAGE")

package naksha.model.response

import naksha.model.request.ResultRow
import kotlin.js.JsExport
import kotlin.jvm.JvmField

/**
 * Success response, means all operations succeeded, and it's safe to commit transaction.
 */
@JsExport
class SuccessResponse(
    /**
     * A handler (available if requested) to fetch more (next) rows from DB.
     */
    @JvmField
    val handle: String? = null,
    /**
     * The result rows.
     */
    @JvmField
    val rows: List<ResultRow>
) : Response() {
    override fun size(): Int = rows.size
}