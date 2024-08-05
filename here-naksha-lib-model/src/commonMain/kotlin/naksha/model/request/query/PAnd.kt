@file:Suppress("OPT_IN_USAGE")

package naksha.model.request.query

import naksha.base.ListProxy
import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * Logically AND combine.
 */
@JsExport
class PAnd() : ListProxy<IPropertyQuery>(IPropertyQuery::class), IPropertyQuery {

    /**
     * Create a logical AND combination of the given queries.
     * @param queries the queries to combine.
     */
    @JsName("of")
    constructor(vararg queries: IPropertyQuery) : this() {
        addAll(queries)
    }
}