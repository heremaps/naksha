package naksha.psql

import naksha.model.Tuple
import naksha.model.TupleNumber
import naksha.model.Version
import naksha.model.objects.NakshaCollection
import naksha.model.objects.NakshaFeature
import naksha.model.objects.NakshaMap
import naksha.model.request.Write
import naksha.model.request.WriteOp

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
     * The `uid` (unique transaction local identifier) of the tombstone state, if this is a [DELETE][WriteOp.DELETE] or [PURGE][WriteOp.PURGE].
     * @since 3.0
     */
    var final_uid: Int? = null

    /**
     * The write operation to perform.
     * @since 3.0
     */
    val op: WriteOp
        get() = original.op

    /**
     * The identifier of the feature to modify.
     * @since 3.0
     */
    val id: String
        get() = original.id

    /**
     * If the operation is atomic, the version in which the _HEAD_ is expected to be; otherwise `null`.
     * @since 3.0
     */
    val version: Version?
        get() = if (original.atomic && op != WriteOp.CREATE && op != WriteOp.UPSERT) original.version else null

    /**
     * The [Tuple] representation of the modified feature.
     *
     * For [DELETE][naksha.model.request.WriteOp.DELETE] and [PURGE][naksha.model.request.WriteOp.PURGE] we do not have a [tuple][Tuple], or a [feature].
     * @since 3.0
     */
    var tuple: Tuple? = null

    val isMapModification: Boolean = original.isMapModification()
    val isCollectionModification: Boolean = original.isCollectionModification()
    val isFeatureModification: Boolean = original.isFeatureModification()

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
     * Returns the target feature as correct type, so either [nakshaMap], [nakshaCollection], [`original.feature`][Write.feature] or `null`, if no feature is available, for [DELETE][WriteOp.DELETE] and [PURGE][WriteOp.PURGE].
     *
     * @return the target feature.
     * @since 3.0
     */
    val feature: NakshaFeature?
        get() = nakshaMap ?: nakshaCollection ?: original.feature

    /**
     * If the operation was performed, this will be the [TupleNumber] of the new state.
     *
     * For [DELETE][WriteOp.DELETE] and [PURGE][WriteOp.PURGE] this will be `null`, if the feature did not exist, and no atomic delete was request, otherwise it will be the [TupleNumber] of the tombstone state; deleting a feature does actually produce a new tombstone state.
     * @since 3.0
     */
    var tupleNumber: TupleNumber? = null
}