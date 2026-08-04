@file:Suppress("OPT_IN_USAGE")

package naksha.psql

import naksha.base.Action
import naksha.base.Id
import naksha.base.Base.BaseCompanion.FAL
import naksha.base.BaseUtil
import naksha.base.collectionExists
import naksha.base.collectionNotFound
import naksha.base.forbidden
import naksha.base.illegalArg
import naksha.base.illegalState
import naksha.base.catalogExists
import naksha.base.mapNotFound
import naksha.base.unsupportedOp
import naksha.model.*
import naksha.model.objects.NakshaCollection
import naksha.model.objects.NakshaCatalog
import naksha.model.objects.NakshaTx
import naksha.model.request.*
import kotlin.js.JsExport
import kotlin.jvm.JvmField

/**
 * A helper to write tuples into collections.
 *
 * This class is stateful and should be sticky until `commit` or `rollback`. It will remember which maps and collections have been created within the current transaction. After `commit`, the corresponding new objects should be visible, and the cache should automatically fetch them, but until this point, they are only visible within the current transaction!
 * @since 3.0
 * @see [PgWrite]
 */
@JsExport
open class PgWriter internal constructor(
    /**
     * The session to which the writer is bound.
     * @since 3.0
     */
    session: PgSession,

    /**
     * If the writer should use save-point's.
     * @since 3.0
     */
    @JvmField
    val useSavepoint: Boolean
) : PgReaderWriterBase(session) {
    /**
     * The storage to operate on.
     * @since 3.0
     */
    val storage: PgStorage = session.storage

    /**
     * The `id` of the database into which to write.
     * @since 3.0
     */
    val databaseId: Id
        get() = session.options.databaseId

    /**
     * The database connection to use for modifications.
     * @since 3.0
     */
    val conn: PgConnection
        get() = session.useConnection()

    /**
     * The transaction to update with what was done.
     * @since 3.0
     */
    val tx = session.useTx()

    /**
     * The Naksha transaction.
     * @since 3.0
     */
    val transaction: NakshaTx
        get() = tx.nakshaTx

    /**
     * Performs the given writes.
     * @param request the write request to perform.
     * @return the response.
     */
    fun execute(request: WriteRequest) : Response {
        val tupleNumberList = executeWrites(request.writes.mapNotNull { it }.toMutableList())
        val rs = TupleNumberResultSet(request, storage, session, tupleNumberList)
        return SuccessResponse().withResultSet(rs)
    }

    /**
     * Performs the given writes.
     * @param writes the writes to perform.
     * @return the tuple-numbers of the
     */
    private fun executeWrites(writes: MutableList<Write>) : TupleNumberList {
        if (writes.isEmpty()) return TupleNumberList()
        // Add the input-index.
        val pgWrites = ArrayList<PgWrite>(writes.size)
        for (i in 0 ..< writes.size) pgWrites.add(PgWrite(writes[i], i))
        val savepointId = BaseUtil.randomAtoZ()
        var conn: PgConnection? = null
        var partitionSnapshot: Map<PgCollection, Set<Int>>? = null
        try {
            DEBUG_printConnection("PgWrite -> before prepareWrite", session.useConnection())
            // This can be time-consuming, unless the connection is already open, try not to open it before we do this!
            prepareWrite(pgWrites)
            DEBUG_printConnection("PgWrite -> after prepareWrite", session.useConnection())

            // Order by:
            // - catalog-number ASC
            // - collection-number ASC
            // - partition-number ASC
            // - PURGE, DELETE, CREATE, UPSERT, UPDATE
            // - feature-number ASC.
            //
            // The ordering is very important, because otherwise there can be deadlocks in the database at row-level locking!
            pgWrites.sortWith { a, b ->
                val catalogDiff = (a.catalog.id.number - b.catalog.id.number).toInt()
                if (catalogDiff != 0) return@sortWith catalogDiff
                val collectionDiff = (a.collection.id.number - b.collection.id.number).toInt()
                if (collectionDiff != 0) return@sortWith collectionDiff
                val partitionDiff = a.partition - b.partition
                if (partitionDiff != 0) return@sortWith partitionDiff
                val opDiff = a.op.order - b.op.order
                if (opDiff != 0) return@sortWith opDiff
                val featureNumberDiff = a.id.number - b.id.number
                if (featureNumberDiff < 0) return@sortWith -1
                if (featureNumberDiff > 0) return@sortWith 1
                0
            }

            // Perform the writes, if any error happens, we will roll back the session to where it was before we started.
            // Note: We must not close the connection, therefore no `session.useConnection().use {}`!
            conn = this.conn
            if (useSavepoint) {
                conn.execute("SAVEPOINT \"$savepointId\"").close()
                partitionSnapshot = session.snapshotPreparedPartitions()
            }
            var start = 0
            for (i in 1..pgWrites.size) {
                if (i == pgWrites.size ||
                    pgWrites[i].catalog.id != pgWrites[start].catalog.id ||
                    pgWrites[i].collection.id != pgWrites[start].collection.id ||
                    pgWrites[i].partition != pgWrites[start].partition)
                {
                    executeWrite(pgWrites, start, i)
                    start = i
                }
            }
            // If everything worked out as expected, we can drop the savepoint, if there is any.
            if (useSavepoint) conn.execute("RELEASE SAVEPOINT \"$savepointId\"").close()
        } catch (t: Throwable) {
            if (conn != null && useSavepoint) {
                conn.execute("ROLLBACK TO SAVEPOINT \"$savepointId\"").close()
                if (partitionSnapshot != null) session.restorePreparedPartitions(partitionSnapshot)
            }
            throw PgExceptionMapper.map(t)
        }

        // Reorder results to match input.
        val tupleNumbers = TupleNumberList()
        tupleNumbers.setCapacity(writes.size)
        val transaction = tx.nakshaTx
        var featuresModified = 0
        val cache = Naksha.cache
        for (i in 0 until pgWrites.size) {
            val write = pgWrites[i]
            val tupleNumber = write.tupleNumber
            tupleNumbers[write.i] = tupleNumber
            val tuple = write.tuple
            if (write.isFeatureModification) {
                val catalog = write.catalog
                val col = write.collection
                val txCol = transaction.useCatalog(catalog.id).useCollection(col.id)
                if (tupleNumber != null) {
                    txCol.add(tupleNumber, col.partitions)
                }
                featuresModified += 1
            } else if (write.isCatalogModification) {
                val catalog = write.asPgCatalog
                if (catalog != null) transaction.useCatalog(catalog.id, write.action)
            } else if (write.isCollectionModification) {
                val catalog = write.catalog
                val col = write.asPgCollection
                if (col != null) {
                    transaction.useCatalog(catalog.id).useCollection(col.id, write.action)
                    catalog.invalidateCollection(col)
                }
            }
            if (tuple != null) cache.put(tuple)
        }
        transaction.featuresModified += featuresModified
        return tupleNumbers
    }

    // Ensure that the needed physical schema and tables are created.
    // Ensure that the PgTupleWrite data class is ready for action.
    // After this has run, only the `tuple` is missing!
    private fun prepareWrite(pgWrites: ArrayList<PgWrite>) {
        for (pgWrite in pgWrites) {
            // The index in the original write instructions, for debugging purpose only!
            val i = pgWrite.i

            // Note: Technically, all operations should work without `id`,
            //       because the primary key is anyway only the feature-number!
            val featureId = pgWrite.id
            val op = pgWrite.op
            val pgAdminCatalog = storage.adminCatalog

            // Detect tbe catalog into which to write.
            val catalogId = pgWrite.originalWrite.catalogId
            val pgCatalog = pgAdminCatalog.getPgCatalogByNumber(conn, catalogId.intValue)
                ?: throw mapNotFound("${FAL}Failed write #$i, write refers to not existing catalog '$catalogId'")
            pgWrite.catalog = pgCatalog

            // Detect the collection into which to write.
            val collectionId = pgWrite.originalWrite.collectionId
            val pgCollection = pgCatalog.getPgCollectionByNumber(conn, collectionId.intValue)
                ?: throw collectionNotFound("${FAL}Failed write #$i, write refers to not existing collection '$collectionId'")
            pgWrite.collection = pgCollection

            // If this operation modifies a catalog, produces: asCatalog.
            if (pgWrite.isCatalogModification) {
                var targetCatalog = pgAdminCatalog.getPgCatalogByNumber(conn, pgWrite.id.intValue)

                val nakshaCatalog: NakshaCatalog?
                if (op == WriteOp.CREATE || op == WriteOp.UPSERT || op == WriteOp.UPDATE) {
                    val feature = pgWrite.feature ?: throw illegalArg("${FAL}Failed write #$i, op is $op, but the feature is null")
                    nakshaCatalog = feature as? NakshaCatalog ?: feature.proxy(NakshaCatalog::class)
                    nakshaCatalog.databaseId = this.databaseId
                    if (targetCatalog == null) {
                        if (op == WriteOp.UPDATE) {
                            throw mapNotFound("${FAL}Failed write #$i, UPDATE failed, because the catalog '$featureId' does not exist")
                        }
                        targetCatalog = PgCatalog(storage, nakshaCatalog)
                        createPgCatalog(targetCatalog)
                    } else if (op == WriteOp.CREATE) {
                        throw catalogExists("${FAL}Failed write #$i, because the catalog '$featureId' does exist already")
                    }
                } else if (op == WriteOp.DELETE || op == WriteOp.PURGE) {
                    if (targetCatalog != null) {
                        deletePgCatalog(targetCatalog)
                    }
                    nakshaCatalog = null
                } else {
                    throw unsupportedOp("${FAL}Failed write #$i, unsupported operation: '$op'")
                }
                pgWrite.asPgCatalog = targetCatalog
                pgWrite.asNakshaCatalog = nakshaCatalog
            }

            // If this operation modifies a collection: asCollection.
            if (pgWrite.isCollectionModification) {
                var targetCollection = pgCatalog.getPgCollectionByNumber(conn, pgWrite.id.intValue)

                val nakshaCollection: NakshaCollection?
                if (op == WriteOp.CREATE || op == WriteOp.UPSERT || op == WriteOp.UPDATE) {
                    val feature = pgWrite.feature ?: throw illegalArg("${FAL}The write #$i is $op, but the feature is null")
                    nakshaCollection = feature as? NakshaCollection ?: feature.proxy(NakshaCollection::class)
                    if (nakshaCollection.partitions > 1000) {
                        throw illegalArg("${FAL}Failed write #$i, partition-count expected 2 .. 1000, found : ${nakshaCollection.partitions}")
                    }
                    if (targetCollection == null) {
                        if (op == WriteOp.UPDATE) {
                            throw collectionNotFound(
                                "${FAL}Failed write #$i, UPDATE failed, because the collection '$featureId' does not exist in map '$catalogId'"
                            )
                        }
                        targetCollection = PgCollection(pgCatalog, nakshaCollection)
                        createPgCollection(targetCollection)
                    } else if (op == WriteOp.CREATE) {
                        throw collectionExists(
                            "${FAL}Failed write #$i, because the collection '$featureId' does exist already in map '$catalogId'"
                        )
                    } else {
                        // UPSERT or UPDATE on an existing collection: Ensure that no invalid changes are asked for.
                        targetCollection.verifyNewHeadState(nakshaCollection)
                    }
                } else if (op == WriteOp.DELETE || op == WriteOp.PURGE) {
                    if (targetCollection != null) {
                        deletePgCollection(targetCollection)
                    }
                    nakshaCollection = null
                } else {
                    throw unsupportedOp("${FAL}Failed write #$i, unsupported operation: '$op'")
                }
                pgWrite.asPgCollection = targetCollection
                pgWrite.asNakshaCollection = nakshaCollection
            }

            // If this operation modifies a collection: asTransaction.
            if (pgWrite.isTransactionModification) {
                val feature = pgWrite.feature ?: throw illegalArg("${FAL}Failed write #$i, modification of a transaction requires a transaction feature")
                pgWrite.asTransaction = feature as? NakshaTx ?: feature.proxy(NakshaTx::class)
            }

            if (op == WriteOp.DELETE || op == WriteOp.PURGE) {
                // Deletes carry no tuple-number; PgWriterDelete resolves the existing HEAD by feature-number.
                if (pgWrite.isTransactionModification) throw forbidden("${FAL}Failed write #$i, transactions must not be deleted or purged")
            } else {
                val collection = pgCollection.head
                val obj = pgWrite.`object`
                    ?: throw illegalState("${FAL}Failed write #$i, missing object in write instruction for operation $op")
                var upsert = false
                val action = when (op) {
                    WriteOp.CREATE -> Action.CREATE
                    WriteOp.UPDATE -> Action.UPDATE
                    WriteOp.UPSERT -> {
                        upsert = true
                        Action.CREATE
                    }
                    else -> throw unsupportedOp("${FAL}Failed write #$i, unsupported operation: $op")
                }
                val tuple: Tuple = Tuple.encodeObject(obj, session, collection, null, action, pgWrite.atomic, upsert)
                pgWrite.tuple = tuple
                pgWrite.tupleNumber = tuple.tupleNumber
            }
        }
    }

    /**
     * Execute all writes between `start` _(inclusive)_ and `end` _(exclusive)_.
     * @param pgWrites the list of ordered writes.
     * @param start the index in the list of the first element to execute.
     * @param end the index in the list of the first element **NOT** to execute _(excluded)_.
     */
    private fun executeWrite(pgWrites: ArrayList<PgWrite>, start: Int, end: Int) {
        if (start == end) return
        // All writes in [start, end) share one catalog and collection (the caller grouped them) and are
        // ordered by op; dispatch each contiguous op-block to its writer.
        val pgCollection = pgWrites[start].collection
        pgCollection.prepareWrite(conn, tx.version.number, session)
        var s = start
        var e = start

        // -------------------------------- DELETE ------------------------------------------------------------------------
        while (e < end && pgWrites[e].op == WriteOp.DELETE) e++
        if (e > s) {
            PgWriterDelete(this, pgCollection, pgWrites, s, e, purge = false).execute(conn)
            s = e
        }

        // -------------------------------- PURGE ------------------------------------------------------------------------
        while (e < end && pgWrites[e].op == WriteOp.PURGE) e++
        if (e > s) {
            PgWriterDelete(this, pgCollection, pgWrites, s, e, purge = true).execute(conn)
            s = e
        }

        // -------------------------------- CREATE ------------------------------------------------------------------------
        while (e < end && pgWrites[e].op == WriteOp.CREATE) e++
        if (e > s) {
            PgWriterInsert(this, pgCollection, pgWrites, s, e).execute(conn)
            s = e
        }

        // -------------------------------- UPSERT ------------------------------------------------------------------------
        while (e < end && pgWrites[e].op == WriteOp.UPSERT) e++
        if (e > s) {
            PgWriterUpsert(this, pgCollection, pgWrites, s, e).execute(conn)
            s = e
        }

        // -------------------------------- UPDATE ------------------------------------------------------------------------
        while (e < end && pgWrites[e].op == WriteOp.UPDATE) e++
        if (e > s) {
            PgWriterUpdate(this, pgCollection, pgWrites, s, e).execute(conn)
            s = e
        }

        if (e != end) throw illegalState("We missed some writes beyond $s in the ordered write-operation list")
    }

    /**
     * Invoked when a [NakshaMap][naksha.model.objects.NakshaCatalog] should be physically created.
     * @param catalog the catalog that should be physically created.
     * @since 3.0
     */
    protected open fun createPgCatalog(catalog: PgCatalog) {
        storage.adminCatalog.createPgCatalog(conn, catalog)
        seedHistoryPartitions(catalog.collections)
    }

    private fun seedHistoryPartitions(collection: PgCollection) {
        if (!collection.storeHistory) return
        val partitionNumber = collection.historyPartitionNumberOf(tx.version.number)
        collection.ensureHistoryPartition(conn, partitionNumber, session)
        collection.ensureHistoryPartition(conn, partitionNumber + 1, session)
    }

    /**
     * Invoked when a [NakshaMap][naksha.model.objects.NakshaCatalog] was created.
     * @param catalog the map that was just created.
     * @since 3.0
     */
    protected open fun deletePgCatalog(catalog: PgCatalog) {
        storage.adminCatalog.deletePgCatalog(conn, catalog)
    }

    /**
     * Invoked when a [NakshaCollection][naksha.model.objects.NakshaCollection] should be physically created.
     * @param collection the collection that should be physically created.
     * @since 3.0
     */
    protected open fun createPgCollection(collection: PgCollection) {
        collection.catalog.createPgCollection(conn, collection)
        seedHistoryPartitions(collection)
    }

    /**
     * Invoked when a [NakshaCollection][naksha.model.objects.NakshaCollection] should be physically created.
     * @param collection the collection that should be physically created.
     * @since 3.0
     */
    protected open fun deletePgCollection(collection: PgCollection) {
        collection.catalog.deletePgCollection(conn, collection)
    }
}