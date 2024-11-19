@file:Suppress("OPT_IN_USAGE")

package naksha.model.request.query

import naksha.base.ListProxy
import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * Logically OR combine.
 */
@JsExport
class POr() : ListProxy<IPropertyQuery>(IPropertyQuery::class), IPropertyQuery {

    /**
     * Create a logical OR combination of the given queries.
     * @param queries the queries to combine.
     */
    @JsName("of")
    constructor(vararg queries: IPropertyQuery) : this() {
        addAll(queries)
    }

    override fun toString(): String {
        //TODO will not work like expected key=val1,val2, rather currently it is key1=val1,key2=val2
        return joinToString(",")
    }
}