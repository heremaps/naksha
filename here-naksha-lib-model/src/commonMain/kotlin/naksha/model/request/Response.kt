@file:Suppress("OPT_IN_USAGE")

package naksha.model.request

import naksha.base.AnyObject
import kotlin.js.JsExport

/**
 * A response to a [Request].
 *
 * If this response is for a write request with [WriteRequest.noResults] being _true_, the client signals that it is not interested in the result (except for either being success or failure), and the database should not generate result rows. This improves write throughput, because no data must be returned (often it simplifies the write itself, e.g. when deleting rows, they do not need to be read from the database).
 */
@JsExport
open class Response : AnyObject() {

    /**
     * The amount of results being part of the response.
     */
    open fun resultSize(): Int = 0
}