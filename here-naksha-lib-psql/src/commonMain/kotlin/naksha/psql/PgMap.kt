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
    @JvmField val storage: PgStorage,

    /**
     * The HEAD state of the map.
     * @since 3.0.0
     */
    @JvmField val nakshaMap: NakshaMap,

    /**
     * The OID of the collection-number sequence.
     * @since 3.0.0
     */
    colNumberSequenceOid: Int,

    /**
     * The map-id.
     */
    @JvmField val id: String = nakshaMap.id,

    /**
     * The map-number.
     */
    @JvmField val number: Int = nakshaMap.number ?: throw NakshaException(NakshaError.ILLEGAL_ARGUMENT, "map number missing"),
) {

    /**
     * The OID of the collection-number sequence.
     * @since 3.0.0
     */
    var colNumberSequenceOid: Int = colNumberSequenceOid
        protected set

    /**
     * The collections' collection.
     * @since 3.0.0
     */
    @Suppress("LeakingThis")
    @JvmField val collections: PgCollection = PgCollection(this, NakshaCollection()
        .withMapId(id)
        .withId(COLLECTIONS_COL)
        .withNumber(COLLECTIONS_COL_NUMBER)
    )

    /**
     * The map-identifier quoted optionally in double quotes.
     * @since 3.0.0
     */
    @JvmField
    val quotedId = quoteIdent(id)
}
