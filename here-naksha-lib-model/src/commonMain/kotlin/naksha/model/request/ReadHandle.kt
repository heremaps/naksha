@file:Suppress("OPT_IN_USAGE")

package naksha.model.request

import kotlin.js.JsExport

/**
 * A special query to read more results from a handle. Requires a handle returned by any previously executed [ReadRequest]. This request may optionally ask for another handle, by setting the [returnHandle] property to _true_. If the [limit] is not explicitly set, the same limit as encoded in the handle is used, which simplifies pagination, when the page size should be stable.
 * @property handle the handle to read from.
 */
@JsExport
class ReadHandle(var handle: String) : ReadRequest<ReadHandle>() {
    override fun copyTo(copy: ReadHandle): ReadHandle {
        super.copyTo(copy)
        copy.handle = handle
        return copy
    }
}
