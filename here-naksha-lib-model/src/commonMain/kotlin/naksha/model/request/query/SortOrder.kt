@file:Suppress("OPT_IN_USAGE")

package naksha.model.request.query

import naksha.base.JsEnum
import naksha.base.Platform.Platform_C.forKClass
import naksha.base.PlatformType
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField
import kotlin.reflect.KClass

/**
 * The sort order.
 */
@JsExport
class SortOrder : JsEnum() {
    companion object SortOrder_C {
        /**
         * The [PlatformType] of [SortOrder].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(SortOrder::class).withPackageName(PACKAGE_NAME)

        /**
         * Any sort order is okay, as long as it is deterministic, allows to use the natural order of the index.
         *
         * The storage will rewrite this into whatever fits best.
         */
        @JvmField
        @JsStatic
        val ANY = def(TYPE, "")

        /**
         * Sort ascending.
         */
        @JvmField
        @JsStatic
        val ASCENDING = def(TYPE, "ASC")

        /**
         * Sort descending.
         */
        @JvmField
        @JsStatic
        val DESCENDING = def(TYPE, "DESC")
    }

    override fun namespace() = TYPE
    override fun initClass() {}

}