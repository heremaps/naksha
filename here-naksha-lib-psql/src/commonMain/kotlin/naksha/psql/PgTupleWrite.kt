package naksha.psql

import naksha.model.NakshaTx
import naksha.model.Tuple
import naksha.model.illegalState
import naksha.model.objects.NakshaCollection
import naksha.model.objects.NakshaFeature
import naksha.model.objects.NakshaMap
import naksha.model.request.Write

/**
 * Pure data class to enrich a write operation with additional information, used by [PgTupleWriter].
 * @since 3.0
 * @see [PgTupleWriter]
 */
internal data class PgTupleWrite(val original: Write, val i: Int) {
    /**
     * The map into which to write.
     *
     * - If a map is modified, this is [Naksha.ADMIN_MAP][naksha.model.Naksha.ADMIN_MAP], [pgMap] and [nakshaMap] will be set.
     * - If a collection is modified, this is the map in which [Naksha.COLLECTIONS_COL][naksha.model.Naksha.COLLECTIONS_COL] is located, [pgCollection] and [nakshaCollection] will be set.
     * @since 3.0
     */
    lateinit var map: PgMap

    /**
     * The collection into which to write.
     *
     * - If a map is modified, this is [Naksha.MAPS_COL][naksha.model.Naksha.MAPS_COL], [pgMap] and [nakshaMap] will be set.
     * - If a collection is modified, this is [Naksha.COLLECTIONS_COL][naksha.model.Naksha.COLLECTIONS_COL], [pgCollection] and [nakshaCollection] will be set.
     * @since 3.0
     */
    lateinit var collection: PgCollection

    /**
     * The [Tuple] representation of the modified feature.
     * @since 3.0
     */
    lateinit var tuple: Tuple

    /**
     * If the feature is a map, the [PgMap] representation.
     * @since 3.0
     */
    var pgMap: PgMap? = null

    /**
     * If this modifies a map, the feature cast to [NakshaMap].
     * @since 3.0
     */
    var nakshaMap: NakshaMap? = null

    /**
     * If the feature is a collection, the [PgCollection] representation.
     * @since 3.0
     */
    var pgCollection: PgCollection? = null

    /**
     * If this modifies a collection, the feature cast to [NakshaCollection].
     * @since 3.0
     */
    var nakshaCollection: NakshaCollection? = null

    /**
     * Returns the target feature as correct type, so either [nakshaMap], [nakshaCollection] or [`original.feature`][Write.feature].
     *
     * @return the target feature.
     * @since 3.0
     */
    val feature: NakshaFeature
        get() = nakshaMap ?: nakshaCollection ?: original.feature ?: throw illegalState("Invalid write #$i, missing feature (null)")
}