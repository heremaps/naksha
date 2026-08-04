@file:Suppress("OPT_IN_USAGE")

package naksha.model.request.query

import naksha.base.PTypedArray
import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * Logically AND combine.
 */
@JsExport
class SpAnd() : PTypedArray<ISpatialQuery>(ISpatialQuery::class), ISpatialQuery {

    /**
     * Create a logical AND combination of the given queries.
     * @param queries the queries to combine.
     */
    @JsName("of")
    constructor(vararg queries: ISpatialQuery) : this() {
        addAll(queries)
    }
}