@file:Suppress("OPT_IN_USAGE")

package naksha.model.request.query

import naksha.base.ListProxy
import naksha.base.Platform.Platform_C.forKClass
import naksha.base.PlatformType
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * Logically AND combine.
 * @since 3.0
 * @see IQuery
 * @see ITagQuery
 * @see TagQuery
 */
@JsExport
class TagAnd() : ListProxy<ITagQuery>(ITagQuery_TYPE), ITagQuery {

    /**
     * Create a logical AND combination of the given queries.
     * @param queries the queries to combine.
     */
    @JsName("of")
    constructor(vararg queries: ITagQuery) : this() {
        addAll(queries)
    }

    companion object TagAnd_C {
        /**
         * The [PlatformType] of [TagAnd].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(TagAnd::class).withPackageName(PACKAGE_NAME)
    }
}