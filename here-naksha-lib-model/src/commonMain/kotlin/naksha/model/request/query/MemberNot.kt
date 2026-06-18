@file:Suppress("OPT_IN_USAGE")

package naksha.model.request.query

import naksha.base.AnyObject
import naksha.base.NotNullProperty
import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * Negates the query.
 */
@JsExport
class MemberNot() : AnyObject(), IMemberQuery {

    /**
     * Create a negation of the given query.
     * @param query the query to negate.
     */
    @JsName("of")
    constructor(query: IMemberQuery) : this() {
        this.query = query
    }

    companion object SpNot_C {
        private val QUERY = NotNullProperty<MemberNot, IMemberQuery>(IMemberQuery::class)
    }

    /**
     * The query to logically negate.
     */
    var query by QUERY
}