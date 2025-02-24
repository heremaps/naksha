@file:Suppress("OPT_IN_USAGE")

package naksha.psql

import naksha.model.Naksha.NakshaCompanion.COLLECTIONS_COL
import naksha.model.Naksha.NakshaCompanion.COLLECTIONS_COL_NUMBER
import naksha.model.NakshaError
import naksha.model.NakshaException
import naksha.model.objects.NakshaCollection
import naksha.model.objects.NakshaMap
import naksha.psql.PgUtil.PgUtilCompanion.quoteIdent
import kotlin.js.JsExport
import kotlin.jvm.JvmField

/**
 * A map stores collections.
 */
@JsExport
open class PgMap internal constructor(
    /**
     * The reference to the storage.
     * @since 3.0.0
     */
    open val storage: PgStorage,

    /**
     * The HEAD state of the map.
     * @since 3.0.0
     */
    val nakshaMap: NakshaMap,

    /**
     * The map-id.
     * @since 3.0
     */
    val id: String = nakshaMap.id,

    /**
     * The map-number.
     * @since 3.0
     */
    val number: Int = nakshaMap.number
) {

    private var _collections: PgCollection? = null

    /**
     * The collection's collection of the map _(`naksha~collections` aka `0`)_.
     * @since 3.0
     */
    val collections: PgCollection
        get() {
            var c = _collections
            if (c == null) {
                c = PgCollection(this, NakshaCollection()
                    .withMapId(id)
                    .withId(COLLECTIONS_COL)
                    .withNumber(COLLECTIONS_COL_NUMBER))
                _collections = c
            }
            return c
        }

    /**
     * The map-identifier quoted optionally in double quotes.
     * @since 3.0
     */
    @JvmField
    val quotedId = quoteIdent(id)
}
