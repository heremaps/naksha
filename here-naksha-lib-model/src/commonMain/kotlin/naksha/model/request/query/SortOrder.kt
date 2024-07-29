@file:Suppress("OPT_IN_USAGE")

package naksha.model.request.query

import naksha.base.JsEnum
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField
import kotlin.reflect.KClass

/**
 * The sort order.
 */
@JsExport
class SortOrder : JsEnum() {
    @Suppress("NON_EXPORTABLE_TYPE")
    override fun namespace(): KClass<out JsEnum> = SortOrder::class

    override fun initClass() {
    }

    companion object SortOrderCompanion {
        /**
         * Any sort order is okay, as long as it is deterministic, allows to use the natural order of the index.
         *
         * The storage will rewrite this into whatever fits best.
         */
        @JvmField
        @JsStatic
        val ANY = def(SortOrder::class, "any")

        /**
         * Sort ascending.
         */
        @JvmField
        @JsStatic
        val ASCENDING = def(SortOrder::class, "asc")

        /**
         * Sort descending.
         */
        @JvmField
        @JsStatic
        val DESCENDING = def(SortOrder::class, "desc")
    }
}