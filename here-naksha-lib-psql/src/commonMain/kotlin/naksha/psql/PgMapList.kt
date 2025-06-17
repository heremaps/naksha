@file:Suppress("OPT_IN_USAGE")

package naksha.psql

import naksha.base.ListProxy
import naksha.base.Platform.Platform_C.forKClass
import naksha.base.PlatformType
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * A list of [maps][PgMap].
 * @since 3.0.0
 */
@JsExport
class PgMapList : ListProxy<PgMap>(PgMap.TYPE) {
    companion object PgMapList_C {
        /**
         * The [PlatformType] of [PgMapList].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(PgMapList::class).withPackageName(PACKAGE_NAME)
    }

    /**
     * Add all given maps
     */
    fun withAll(maps: List<PgMap?>): PgMapList {
        addAll(maps)
        return this
    }
}
