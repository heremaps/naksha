@file:Suppress("OPT_IN_USAGE")

package naksha.model.request.query

import naksha.base.AnyObject
import naksha.base.NotNullProperty
import naksha.base.Platform.Platform_C.forKClass
import naksha.base.PlatformType
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * Negates the query.
 * @since 3.0
 * @see IQuery
 * @see ITagQuery
 * @see TagQuery
 */
@JsExport
class TagNot() : AnyObject(), ITagQuery {

    /**
     * Create a negation of the given query.
     * @param query the query to negate.
     */
    @JsName("of")
    constructor(query: ITagQuery) : this() {
        this.query = query
    }

    companion object TagNot_C {
        /**
         * The [PlatformType] of [TagNot].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(TagNot::class).withPackageName(PACKAGE_NAME)

        private val QUERY = NotNullProperty<TagNot, ITagQuery>(ITagQuery_TYPE)
    }

    /**
     * The query to logically negate.
     */
    var query by QUERY
}