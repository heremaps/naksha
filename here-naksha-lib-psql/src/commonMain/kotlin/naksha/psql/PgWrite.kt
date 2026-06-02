package naksha.psql

import naksha.model.*
import naksha.model.objects.NakshaCollection
import naksha.model.objects.NakshaFeature
import naksha.model.objects.NakshaMap
import naksha.model.request.Write
import naksha.model.request.WriteOp

/**
 * Pure data class to enrich a write operation with additional information, used by [PgWriter].
 * @since 3.0
 * @see [Write]
 * @see [PgWriter]
 */
internal data class PgWrite(val original: Write, val i: Int) {

    /**
     * The map into which to write.
     *
     * - If a map is modified, this is [Naksha.ADMIN_MAP][naksha.model.Naksha.ADMIN_MAP], [asPgMap] and [asNakshaMap] will be set.
     * - If a collection is modified, this is the map in which [Naksha.COLLECTIONS_COL][naksha.model.Naksha.COLLECTIONS_COL] is located, [asPgCollection] and [asNakshaCollection] will be set.
     * @since 3.0
     */
    lateinit var map: PgMap

    /**
     * The collection into which to write.
     *
     * - If a map is modified, this is [Naksha.CATALOGS_COL][naksha.model.Naksha.CATALOGS_COL], [asPgMap] and [asNakshaMap] will be set.
     * - If a collection is modified, this is [Naksha.COLLECTIONS_COL][naksha.model.Naksha.COLLECTIONS_COL], [asPgCollection] and [asNakshaCollection] will be set.
     * @since 3.0
     */
    lateinit var collection: PgCollection

    /**
     * The write operation to perform, [WriteOp.CREATE], [WriteOp.UPDATE], [WriteOp.UPSERT], [WriteOp.DELETE], or [WriteOp.PURGE].
     * @since 3.0
     */
    val op: WriteOp
        get() = original.op

    /**
     * After the feature has been persisted, this shows the final [Action] that has been performed, initially the value is guessed.
     * @since 3.0
     */
    var action: Action = when (original.op) {
        WriteOp.CREATE, WriteOp.UPSERT -> Action.CREATED
        WriteOp.UPDATE -> Action.UPDATED
        WriteOp.DELETE, WriteOp.PURGE -> Action.DELETED
        else -> Action.VERSION
    }

    /**
     * The identifier of the feature to modify.
     * @since 3.0
     */
    val id: String
        get() = original.id

    /**
     * The feature-number (`fn`) derived from [id].
     *
     * - Named features (`id` is a non-numeric string): `fn < 0` — lower 16 bits of the MD5 hash with the sign bit set.
     * - Numeric features (`id` is a valid 63-bit unsigned decimal): `fn >= 0` — `Long.parseLong(id)`.
     *
     * This is the authoritative routing key for physical partition assignment.
     * @since 3.0
     */
    val featureNumber: naksha.base.Int64 = Naksha.featureNumber(id)

    /**
     * The partition-number for this feature, derived from the lower 16 bits of [featureNumber].
     *
     * Always computed from [featureNumber] (i.e. from `fn`), never directly from the [id] string.
     * Value is between `0` and `65535` (inclusive).
     * @since 3.0
     * @see [partition]
     */
    val partitionNumber: Int = Naksha.partitionNumber(featureNumber)

    /**
     * The partition-index, being `-1` if the collection does not have any performance-partitions, otherwise a value between `0` and `collection.partitions` _(exclusive)_. Must not be called unless [collection] has been initialized _(as it is a `lateinit` variable)_.
     *
     * Routing is always by [featureNumber] (`fn`). Both named and numeric features are routed identically
     * via the lower 16 bits of `fn`.
     * @since 3.0
     * @see [partitionNumber]
     */
    val partition: Int
        get() = if (collection.partitions > 1) partitionNumber % collection.partitions else -1

    /**
     * The attachment from the [Write] instruction, can be [Write.UNDEFINED]. If the attachment is [Write.UNDEFINED], an existing attachment should be retained.
     * @since 3.0
     */
    val attachment: ByteArray?
        get() = original.attachment

    /**
     * If the operation is atomic, the version in which the _HEAD_ is expected to be; otherwise `null`.
     * @since 3.0
     */
    val version: Version?
        get() = if (original.atomic && op != WriteOp.CREATE && op != WriteOp.UPSERT)
            original.version ?: original.tupleNumber?.version
        else
            null

    /**
     * The [Tuple] representation of the modified feature.
     *
     * For [DELETE][naksha.model.request.WriteOp.DELETE] and [PURGE][naksha.model.request.WriteOp.PURGE] we do not have a [tuple][Tuple], or a [feature].
     * @since 3.0
     */
    var tuple: Tuple? = null

    val isMapModification: Boolean
        get() = original.isMapModification()
    val isCollectionModification: Boolean
        get() = original.isCollectionModification()
    val isTransactionModification: Boolean
        get() = Naksha.ADMIN_MAP == map.id && Naksha.TRANSACTIONS_COL == collection.id
    // This variant differs from write.isFeatureModification, because it includes dictionaries, which are just features for us!
    val isFeatureModification: Boolean
        get() = !isTransactionModification && !isMapModification && !isCollectionModification

    /**
     * If the feature is a map, the [PgMap] representation.
     * @since 3.0
     */
    var asPgMap: PgMap? = null

    /**
     * If this modifies a map, the feature cast to [NakshaMap].
     * @since 3.0
     */
    var asNakshaMap: NakshaMap? = null

    /**
     * If the feature is a collection, the [PgCollection] representation.
     * @since 3.0
     */
    var asPgCollection: PgCollection? = null

    /**
     * If this modifies a collection, the feature cast to [NakshaCollection].
     * @since 3.0
     */
    var asNakshaCollection: NakshaCollection? = null

    /**
     * Returns the target feature as correct type, so either [asNakshaMap], [asNakshaCollection], [`original.feature`][Write.feature] or `null`, if no feature is available, for [DELETE][WriteOp.DELETE] and [PURGE][WriteOp.PURGE].
     *
     * @return the target feature.
     * @since 3.0
     */
    val feature: NakshaFeature?
        get() = asNakshaMap ?: asNakshaCollection ?: original.feature

    /**
     * If the operation was performed, this will be the [TupleNumber] of the new state.
     *
     * For [DELETE][WriteOp.DELETE] and [PURGE][WriteOp.PURGE] this will be `null`, if the feature did not exist, and no atomic delete was request, otherwise it will be the [TupleNumber] of the tombstone state; deleting a feature does actually produce a new tombstone state.
     * @since 3.0
     */
    var tupleNumber: TupleNumber? = null
}