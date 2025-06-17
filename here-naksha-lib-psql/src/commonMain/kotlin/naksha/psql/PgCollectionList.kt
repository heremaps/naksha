@file:Suppress("OPT_IN_USAGE")

package naksha.psql

import naksha.base.ListProxy
import naksha.base.Platform.Platform_C.forKClass
import naksha.base.PlatformType
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * A list of [collections][PgCollection].
 * @since 3.0.0
 */
@JsExport
class PgCollectionList : ListProxy<PgCollection>(PgCollection.TYPE) {
    companion object PgCollectionList_C {
        /**
         * The [PlatformType] of [PgCollectionList].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(PgCollectionList::class).withPackageName(PACKAGE_NAME)
    }

    /**
     * Add all given collections.
     */
    fun withAll(maps: List<PgCollection?>): PgCollectionList {
        addAll(maps)
        return this
    }
}
