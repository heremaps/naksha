package naksha.psql

import naksha.base.Action
import naksha.base.Id
import naksha.base.TupleNumber
import naksha.base.illegalState
import naksha.model.*
import naksha.model.objects.NakshaCollection
import naksha.model.objects.NakshaFeature
import naksha.model.objects.NakshaCatalog
import naksha.model.objects.NakshaTx
import naksha.model.request.Write
import naksha.model.request.WriteOp

/**
 * A wrapper around `original` [Write] instructions provided by the client. Used to enrich the `original` write operation with additional information needed by [PgWriter].
 * @property original the original write instructions as provided by the client.
 * @property i the index in the input data _(needed to order the results)_.
 * @since 3.0
 * @see [Write]
 * @see [PgWriter]
 */
internal data class PgWrite(val original: Write, val i: Int) {

    /**
     * The catalog into which to write _(as session local copy)_.
     *
     * - If a catalog is modified, this is the admin-catalog _(`naksha~admin`)_, [asNakshaCatalog] will be set.
     * - If a collection is modified, this is the catalog in which the collection is located, [asNakshaCollection] will be set.
     * - If a feature is modified, this is the catalog in which the feature is stored.
     *
     * This property is set by [PgWriter.prepareWrite].
     * @since 3.0
     */
    lateinit var dbCatalog: PgCatalog

    /**
     * The collection into which to write _(as session local copy)_.
     *
     * - If a catalog is modified, this is the admin-catalog _(`naksha~admin`)_, [asNakshaCatalog] will be set.
     * - If a collection is modified, this is the admin-collection _(`naksha~collections`)_, [asNakshaCollection] will be set.
     * - If a feature is modified, this is the collection in which the feature is stored.
     *
     * This property is set by [PgWriter.prepareWrite].
     * @since 3.0
     */
    lateinit var dbCollection: PgCollection

    /**
     * The write operation to perform, [WriteOp.CREATE], [WriteOp.UPDATE], [WriteOp.UPSERT], [WriteOp.DELETE], or [WriteOp.PURGE]. Actually just reads `original.op`.
     * @since 3.0
     */
    val op: WriteOp
        get() = original.op

    /**
     * After the feature has been persisted, this shows the final [naksha.base.Action] that has been performed, initially the value is guessed.
     * @since 3.0
     */
    var action: Action = when (original.op) {
        WriteOp.CREATE, WriteOp.UPSERT -> Action.CREATE
        WriteOp.UPDATE -> Action.UPDATE
        WriteOp.DELETE, WriteOp.PURGE -> Action.DELETE
        else -> Action.VERSION
    }

    /**
     * The identifier of the feature to modify.
     * @since 3.0
     */
    val id: Id = original.id

    /**
     * The partition-number for this feature, the same as [id.partitionNumber][Id.partitionNumber].
     *
     * Value is between `0` and `255` _(inclusive)_.
     * @since 3.0
     */
    val partitionNumber: Int = id.partitionNumber

    /**
     * Simply returns [original.version][Write.version].
     * @since 3.0
     */
    val requireVersion: Long?
        get() = original.version

    /**
     * The [Tuple] representation of the modified feature.
     *
     * For [DELETE][naksha.model.request.WriteOp.DELETE] and [PURGE][naksha.model.request.WriteOp.PURGE] we do not have a [tuple][Tuple], or a [feature].
     *
     * This property is set by [PgWriter.prepareWrite].
     * @since 3.0
     */
    var tuple: Tuple? = null

    /** Same as [original.isCatalogModification][Write.isCatalogModification]. */
    val isCatalogModification: Boolean
        get() = original.isCatalogModification()
    /** Same as [original.isCollectionModification][Write.isCollectionModification]. */
    val isCollectionModification: Boolean
        get() = original.isCollectionModification()
    /** Same as [original.isBookModification][Write.isBookModification]. */
    val isBookModification: Boolean
        get() = original.isBookModification()
    /** Same as [original.isTransactionModification][Write.isTransactionModification]. */
    val isTransactionModification: Boolean
        get() = original.isTransactionModification()
    /** Same as [original.isFeatureModification][Write.isFeatureModification]. */
    val isFeatureModification: Boolean
        get() = original.isFeatureModification()

    /**
     * The new state as feature of correct type.
     *
     * This property is set by [PgWriter.prepareWrite] as will be [NakshaCatalog], [NakshaCollection], [NakshaFeature], or `null` for `PURGE`. For `DELETE` the feature can be given and it can be `null`, depending on the client wanting a specific tombstone state.
     * @since 3.0
     */
    var feature: NakshaFeature? = null

    /** Just [feature] cast into [NakshaCatalog] */
    val asCatalog: NakshaCatalog
        get() = feature as? NakshaCatalog ?: throw illegalState("Require feature to be a catalog")

    /** Just [feature] cast into [NakshaCatalog] */
    val asCollection: NakshaCollection
        get() = feature as? NakshaCollection ?: throw illegalState("Require feature to be a collection")

    /** Just [feature] cast into [NakshaTx] */
    val asTx: NakshaTx
        get() = feature as? NakshaTx ?: throw illegalState("Require feature to be a transaction")

    /**
     * If the operation was performed, this will be the [TupleNumber] of the new state.
     *
     * For [DELETE][WriteOp.DELETE] this can be `null`, if the feature did not exist. [PURGE][WriteOp.PURGE] does not produce a new state, therefore this will always be `null` when a purged was requested.
     *
     * This property is set by [PgWriter.executeWrite].
     * @since 3.0
     */
    var tupleNumber: TupleNumber? = null

    /**
     * If this modified a catalog, the [PgCatalog] wrapper around the [tuple].
     *
     * This property is set by [PgWriter.executeWrite].
     * @since 3.0
     */
    var pgCatalog: PgCatalog? = null

    /**
     * If this modified a collection, the [PgCollection] wrapper around the [tuple].
     *
     * This property is set by [PgWriter.executeWrite].
     * @since 3.0
     */
    var pgCollection: PgCollection? = null
}
