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
 * @see IPropertyQuery
 * @see POr
 */
@JsExport
class POr() : ListProxy<IPropertyQuery>(IPropertyQuery_TYPE), IPropertyQuery {

    /**
     * Create a logical OR combination of the given queries.
     * @param queries the queries to combine.
     */
    @JsName("POrOf")
    constructor(vararg queries: IPropertyQuery) : this() {
        addAll(queries)
    }

    companion object POr_C {
        /**
         * The [PlatformType] of [POr].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(POr::class).withPackageName(PACKAGE_NAME)
    }

    override fun toString(): String {
        //TODO will not work like expected key=val1,val2, rather currently it is key1=val1,key2=val2
        return joinToString(",")
    }
}