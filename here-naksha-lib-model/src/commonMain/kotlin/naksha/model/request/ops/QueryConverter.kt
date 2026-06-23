@file:Suppress("OPT_IN_USAGE")

package naksha.model.request.ops

import naksha.model.request.RequestQuery
import kotlin.js.JsExport

@JsExport
class QueryConverter private constructor() {
    companion object PgQueryConverter_C {
        fun convert(query: RequestQuery): Op? {
            // TODO: Implement a convertion.
            return null
        }
    }
}