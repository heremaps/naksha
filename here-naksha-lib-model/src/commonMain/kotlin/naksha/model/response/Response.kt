package naksha.model.response

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * If this response is for a write request with noResults being true, the client signals that it is not interested in the result (except for either being success or failure), and the database should not generate a result rows. This improves write throughput, because no data must be returned (often it simplifies the write itself, e.g. when deleting rows, they do not need to be read from the database).
 */
@OptIn(ExperimentalJsExport::class)
@JsExport
abstract class Response(
    val type: String
) {
    companion object {
        const val ERROR_TYPE = "ERROR"
        const val SUCCESS_TYPE = "SUCCESS"
        const val COUNT_TYPE = "COUNT"
        const val STORAGE_STATS_TYPE = "STORAGE_STATS"
        const val STATISTICS_TYPE = "STATISTICS"
        const val HISTORY_STATS_TYPE = "HISTORY_STATS"
        const val HEALTH_STATUS_TYPE = "HEALTH_STATUS"
        const val CONNECTOR_STATUS_TYPE = "CONNECTOR_STATUS"
        const val SPACE_STATUS_TYPE = "SPACE_STATUS"
        const val MODIFIED_EVENT_TYPE = "MODIFIED_EVENT"
        const val MODIFIED_RESPONSE_TYPE = "MODIFIED_RESPONSE"
        const val NOT_MODIFIED_TYPE = "NOT_MODIFIED"
        const val XYZ_COLLECTION_TYPE = "XYZ_COLLECTION"
        const val CHANGE_SET_TYPE = "CHANGE_SET"
        const val CHANGE_SET_COLLECTION_TYPE = "CHANGE_SET_COLLECTION"
        const val BINARY_TYPE = "BINARY"
    }
}