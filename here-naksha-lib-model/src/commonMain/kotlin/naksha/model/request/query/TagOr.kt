@file:Suppress("OPT_IN_USAGE")

package naksha.model.request.query

import naksha.base.PTypedArray
import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * Logically OR combine.
 */
@JsExport
class TagOr() : PTypedArray<ITagQuery>(ITagQuery::class), ITagQuery {

    /**
     * Create a logical OR combination of the given queries.
     * @param queries the queries to combine.
     */
    @JsName("of")
    constructor(vararg queries: ITagQuery) : this() {
        addAll(queries)
    }
}