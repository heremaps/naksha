@file:Suppress("OPT_IN_USAGE")

package naksha.model.request.query

import naksha.base.AnyObject
import naksha.base.NotNullProperty
import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * Negates the spatial query.
 */
@JsExport
class SpNot() : AnyObject(), ISpatialQuery {

    /**
     * Create a negation of the given spatial query.
     * @param query the query to negate.
     */
    @JsName("of")
    constructor(query: ISpatialQuery) : this() {
        this.query = query
    }

    companion object SpNot_C {
        private val QUERY = NotNullProperty<SpNot, ISpatialQuery>(ISpatialQuery::class)
    }

    /**
     * The query to logically negate.
     */
    var query by QUERY
}