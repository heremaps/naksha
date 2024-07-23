@file:Suppress("OPT_IN_USAGE")

package naksha.model.response

import naksha.model.request.ResultSet
import kotlin.js.JsExport
import kotlin.jvm.JvmField

/**
 * Success response, means all operations succeeded, and it's safe to commit transaction.
 * @property resultSet the result-set as returned by the storage.
 */
@JsExport
open class SuccessResponse(@JvmField val resultSet: ResultSet) : Response() {
    override fun size(): Int = resultSet.size()
}
