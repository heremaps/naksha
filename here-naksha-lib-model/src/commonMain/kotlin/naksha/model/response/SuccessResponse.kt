@file:Suppress("OPT_IN_USAGE")

package naksha.model.response

import naksha.model.request.ResultRow
import kotlin.js.JsExport
import kotlin.jvm.JvmField

/**
 * Success response, means all operations succeeded, and it's safe to commit transaction.
 * @property rows the rows fetched this time.
 * @property handle a handle (available only if requested) to fetch more (next) rows from DB.
 */
@JsExport
open class SuccessResponse(@JvmField var rows: MutableList<ResultRow>, @JvmField var handle: String? = null) : Response() {
    override fun size(): Int = rows.size
}
