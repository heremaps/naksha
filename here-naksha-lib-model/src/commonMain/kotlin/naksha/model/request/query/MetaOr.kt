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
 * Logically OR combine.
 * @see IQuery
 * @see IMetaQuery
 * @see MetaOr
 */
@JsExport
class MetaOr() : ListProxy<IMetaQuery>(IMetaQuery_TYPE), IMetaQuery {

    /**
     * Create a logical AND combination of the given queries.
     * @param queries the queries to combine.
     */
    @JsName("MetaOrOf")
    constructor(vararg queries: IMetaQuery) : this() {
        addAll(queries)
    }

    companion object MetaOr_C {
        /**
         * The [PlatformType] of [MetaOr].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(MetaOr::class).withPackageName(PACKAGE_NAME)
    }
}