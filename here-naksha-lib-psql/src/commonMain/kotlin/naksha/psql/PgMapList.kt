@file:OptIn(ExperimentalJsExport::class)

package naksha.psql

import naksha.base.ListProxy
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * A list of [maps][PgCatalog].
 * @since 3.0.0
 */
@JsExport
class PgMapList : ListProxy<PgCatalog>(PgCatalog::class) {
    /**
     * Add all given maps
     */
    fun withAll(maps: List<PgCatalog?>): PgMapList {
        addAll(maps)
        return this
    }
}
