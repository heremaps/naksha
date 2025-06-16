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
 * @see IPropertyQuery
 * @see PAnd
 */
@JsExport
class PAnd() : ListProxy<IPropertyQuery>(IPropertyQuery_TYPE), IPropertyQuery {

    /**
     * Create a logical AND combination of the given queries.
     * @param queries the queries to combine.
     */
    @JsName("of")
    constructor(vararg queries: IPropertyQuery) : this() {
        addAll(queries)
    }

    companion object PAnd_C {
        /**
         * The [PlatformType] of [PAnd].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(PAnd::class).withPackageName(PACKAGE_NAME)
    }

    override fun toString(): String {
        return joinToString(" & ")
    }
}