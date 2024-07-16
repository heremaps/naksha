package naksha.model.response

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.jvm.JvmStatic

/**
 * If this response is for a write request with noResults being true, the client signals that it is not interested in the result (except for either being success or failure), and the database should not generate a result rows. This improves write throughput, because no data must be returned (often it simplifies the write itself, e.g. when deleting rows, they do not need to be read from the database).
 */
@OptIn(ExperimentalJsExport::class)
@JsExport
abstract class Response {
    /**
     * The response size, will be zero for empty result-sets and for [ErrorResponse].
     */
    abstract fun size(): Int
}