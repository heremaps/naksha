@file:OptIn(ExperimentalJsExport::class)

package naksha.psql

import naksha.base.ListProxy
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * A list of [collections][PgCollection].
 * @since 3.0.0
 */
@JsExport
class PgCollectionList : ListProxy<PgCollection>(PgCollection::class) {
    /**
     * Add all given collections.
     */
    fun withAll(maps: List<PgCollection?>): PgCollectionList {
        addAll(maps)
        return this
    }
}
