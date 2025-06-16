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
 * @since 3.0
 * @see IQuery
 * @see ISpatialQuery
 * @see SpOr
 */
@JsExport
class SpOr() : ListProxy<ISpatialQuery>(ISpatialQuery_TYPE), ISpatialQuery {

    /**
     * Create a logical AND combination of the given queries.
     * @param queries the queries to combine.
     */
    @JsName("of")
    constructor(vararg queries: ISpatialQuery) : this() {
        addAll(queries)
    }

    companion object SpOr_C {
        /**
         * The [PlatformType] of [SpOr].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(SpOr::class).withPackageName(PACKAGE_NAME)
    }
}