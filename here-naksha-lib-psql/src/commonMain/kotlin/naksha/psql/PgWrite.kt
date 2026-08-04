package naksha.psql

import naksha.base.Action
import naksha.base.PAnyMap
import naksha.base.Id
import naksha.base.TupleNumber
import naksha.model.*
import naksha.model.objects.NakshaCollection
import naksha.model.objects.NakshaFeature
import naksha.model.objects.NakshaCatalog
import naksha.model.objects.NakshaTx
import naksha.model.request.Write
import naksha.model.request.WriteOp

/**
 * Pure data class to enrich a write operation with additional information, used by [PgWriter].
 * @property originalWrite the [Write] instruction as provided by the client.
 * @property i the index in the origin write instructions as provided by the client; as instructions are re-ordered after preparation this is important to restore order after successful execution to assign results back.
 * @since 3.0
 * @see [Write]
 * @see [PgWriter]
 */
internal data class PgWrite(val originalWrite: Write, val i: Int) {

    /**
     * The catalog into which to write.
     *
     * - If a catalog is modified, this is [ADMIN_CATALOG_ID][naksha.model.Naksha.ADMIN_CATALOG_ID]; [asPgCatalog] and [asNakshaCatalog] will be set.
     * - If a collection is modified, this is the catalog in which [COLLECTIONS_COL_ID][naksha.model.Naksha.COLLECTIONS_COL_ID] is located; [asPgCollection] and [asNakshaCollection] will be set.
     * @since 3.0
     */
    lateinit var catalog: PgCatalog

    /**
     * The collection into which to write.
     *
     * - If a catalog is modified, this is [CATALOGS_COL_ID][naksha.model.Naksha.CATALOGS_COL_ID]; [asPgCatalog] and [asNakshaCatalog] will be set.
     * - If a collection is modified, this is [COLLECTIONS_COL_ID][naksha.model.Naksha.COLLECTIONS_COL_ID]; [asPgCollection] and [asNakshaCollection] will be set.
     * @since 3.0
     */
    lateinit var collection: PgCollection

    /**
     * The write operation to perform, [WriteOp.CREATE], [WriteOp.UPDATE], [WriteOp.UPSERT], [WriteOp.DELETE], or [WriteOp.PURGE].
     * @since 3.0
     */
    val op: WriteOp
        get() = originalWrite.op

    /**
     * After the feature has been persisted, this shows the final [naksha.base.Action] that has been performed, initially the value is guessed.
     * @since 3.0
     */
    var action: Action = when (originalWrite.op) {
        WriteOp.CREATE, WriteOp.UPSERT -> Action.CREATE
        WriteOp.UPDATE -> Action.UPDATE
        WriteOp.DELETE, WriteOp.PURGE -> Action.DELETE
        else -> Action.VERSION
    }

    /**
     * If the operation should be performed atomic.
     * @since 3.0
     */
    var atomic: Boolean = originalWrite.atomic

    /**
     * The identifier of the feature to modify, effectively only reading [originalWrite]->`id`.
     * @since 3.0
     */
    val id: Id
        get() = originalWrite.id

    /**
     * The partition-number for this feature, derived from the lower 16 bits of [featureNumber].
     *
     * Always computed from [featureNumber] (i.e. from `fn`), never directly from the [id] string.
     * Value is between `0` and `65535` (inclusive).
     * @since 3.0
     * @see [partition]
     */
    val partitionNumber: Int
        get() = id.partitionNumber

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
     * If the operation is atomic, the version in which the _HEAD_ is expected to be; otherwise `null`.
     *
     * The method prefers the value read form [tuple] _([Tuple.previousTupleNumber])_, because it is more reliable as potential `origin` is already solved. In other words, the `uuid` of a given feature can come from a foreign storage, therefore it may not be
     * @since 3.0
     */
    val version: Long?
        get() = if (atomic) tuple?.previousTupleNumber?.version ?: originalWrite.version else null

    /**
     * The [Tuple] representation of the modified feature.
     *
     * For [DELETE][naksha.model.request.WriteOp.DELETE] and [PURGE][naksha.model.request.WriteOp.PURGE] we do not have a [tuple][Tuple], or a [feature].
     * @since 3.0
     */
    var tuple: Tuple? = null

    /** If this instruction modifies a catalog. */
    val isCatalogModification: Boolean
        get() = originalWrite.isCatalogModification()
    /** If this instruction modifies a collection. */
    val isCollectionModification: Boolean
        get() = originalWrite.isCollectionModification()
    /** If this instruction modifies a transaction. */
    val isTransactionModification: Boolean
        get() = Id.ADMIN_CATALOG_ID == catalog.id && Id.TRANSACTIONS_COL_ID == collection.id
    /** If this instruction modifies a global book. */
    val isBookModification: Boolean
        get() = Id.ADMIN_CATALOG_ID == catalog.id && Id.BOOKS_COL_ID == collection.id
    /** If this instruction modifies a normal feature. */
    val isFeatureModification: Boolean
        get() = !isCatalogModification && !isCollectionModification && !isTransactionModification && !isBookModification

    /**
     * If the feature is a catalog, the [PgCatalog] representation.
     * @since 3.0
     */
    var asPgCatalog: PgCatalog? = null

    /**
     * If this modifies a catalog, the feature cast to [NakshaCatalog].
     * @since 3.0
     */
    var asNakshaCatalog: NakshaCatalog? = null

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
     * If this modifies a transaction, the feature cast to [NakshaTx].
     * @since 3.0
     */
    var asTransaction: NakshaTx? = null

    /**
     * The object from [originalWrite].
     */
    val `object`: PAnyMap?
        get() = originalWrite.`object`

    /**
     * Returns the target feature as correct type, so either [asNakshaCatalog], [asNakshaCollection], [`original.feature`][Write.feature] or `null`, if no feature is available, for [DELETE][WriteOp.DELETE] and [PURGE][WriteOp.PURGE].
     *
     * @return the target feature.
     * @since 3.0
     */
    val feature: NakshaFeature?
        get() = asNakshaCatalog ?: asNakshaCollection ?: originalWrite.feature

    /**
     * If the operation was performed, this will be the [naksha.base.TupleNumber] of the new state.
     *
     * For [DELETE][WriteOp.DELETE] and [PURGE][WriteOp.PURGE] this will be `null`, if the feature did not exist, and no atomic delete was request, otherwise it will be the [naksha.base.TupleNumber] of the tombstone state; deleting a feature does actually produce a new tombstone state.
     * @since 3.0
     */
    var tupleNumber: TupleNumber? = null
}