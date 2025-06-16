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
 * @see IMetaQuery
 * @see MetaAnd
 */
@JsExport
class MetaAnd() : ListProxy<IMetaQuery>(IMetaQuery_TYPE), IMetaQuery {

    /**
     * Create a logical AND combination of the given queries.
     * @param queries the queries to combine.
     */
    @JsName("MetaAndOf")
    constructor(vararg queries: IMetaQuery) : this() {
        addAll(queries)
    }

    companion object MetaAnd_C {
        /**
         * The [PlatformType] of [MetaAnd].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(MetaAnd::class).withPackageName(PACKAGE_NAME)
    }
}