@file:Suppress("OPT_IN_USAGE")

package naksha.model.request.query

import naksha.base.ListProxy
import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * Logically OR combine.
 */
@JsExport
class SpOr() : ListProxy<ISpatialQuery>(ISpatialQuery::class), ISpatialQuery {

    /**
     * Create a logical AND combination of the given queries.
     * @param queries the queries to combine.
     */
    @JsName("of")
    constructor(vararg queries: ISpatialQuery) : this() {
        addAll(queries)
    }

    /**
     * Create a logical AND combination of the given queries.
     * @param queries the queries to combine.
     */
    @JsName("ofArray")
    constructor(queries: Array<ISpatialQuery>) : this() {
        addAll(queries)
    }
}