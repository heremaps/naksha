@file:Suppress("OPT_IN_USAGE")

package naksha.psql

import naksha.base.Action
import naksha.base.PlatformUtil
import naksha.base.collectionExists
import naksha.base.collectionNotFound
import naksha.base.forbidden
import naksha.base.illegalArg
import naksha.base.illegalState
import naksha.base.internalError
import naksha.base.mapExists
import naksha.base.mapNotFound
import naksha.model.*
import naksha.model.objects.NakshaCollection
import naksha.model.objects.NakshaCatalog
import naksha.model.objects.NakshaFeature
import naksha.model.objects.NakshaTx
import naksha.model.objects.StandardMembers
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
     * @param writes the writes to perform.
     * @return the response.
     */
    fun execute(writes: WriteList) : Response {
        val tupleNumberList = executeWrites(writes.mapNotNull { it }.toMutableList())
        return SuccessResponse().withTupleNumberList(tupleNumberList)
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
        val savepointId = PlatformUtil.randomString()
        var conn: PgConnection? = null
        var partitionSnapshot: Map<PgCollection, Set<Int>>? = null
        try {
            // This can be time-consuming, unless the connection is already open, try not to open it before we do this!
            val updateCache = prepareWrite(pgWrites)

            // Order by:
            // - catalog-number ASC
            // - collection-number ASC
            // - partition-number ASC
            // - DELETE, PURGE, CREATE, UPSERT, UPDATE
            // - feature-number ASC.
            //
            // The ordering is very important, because otherwise there can be deadlocks in the database at row-level locking!
            pgWrites.sortWith { a, b ->
                val catalogDiff = a.catalog.catalogNumber - b.catalog.catalogNumber
                if (catalogDiff != 0) return@sortWith catalogDiff
                val collectionDiff = a.collection.collectionNumber - b.collection.collectionNumber
                if (collectionDiff != 0) return@sortWith collectionDiff
                val partitionDiff = a.partition - b.partition
                if (partitionDiff != 0) return@sortWith partitionDiff
                val opDiff = a.op.order - b.op.order
                if (opDiff != 0) return@sortWith opDiff
                val featureNumberDiff = a.featureNumber - b.featureNumber
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
                    pgWrites[i].catalog.catalogNumber != pgWrites[start].catalog.catalogNumber ||
                    pgWrites[i].collection.collectionNumber != pgWrites[start].collection.collectionNumber ||
                    pgWrites[i].partition != pgWrites[start].partition)
                {
                    executeWrite(pgWrites, start, i)
                    start = i
                }
            }
            // If everything worked out as expected
            if (updateCache != null) {
                for (pgWrite in pgWrites) {
                    val id = pgWrite.id
                    val catalogId = pgWrite.catalog.id
                    if (pgWrite.isCollectionModification) {
                        val pgCatalog = session.getPgCatalogById(pgWrite.catalog.id)
                            ?: throw internalError("The collection '$id' was modified, but the parent catalog '$catalogId' does not exist")
                        pgCatalog.invalidateCollection(pgWrite.featureNumber.toInt())
                    } else if (pgWrite.isCatalogModification) {
                        val pgAdminCatalog = session.getPgCatalogById(pgWrite.catalog.id) as? PgAdminCatalog
                            ?: throw internalError("The catalog '$id' was modified, but the parent catalog is not the admin-catalog")
                        pgAdminCatalog.invalidateCatalog(pgWrite.featureNumber.toInt())
                    } else {
                        throw internalError("A cache update for '${pgWrite.id}' was reported, but does not match catalog or collection")
                    }
                }
            }

            // We can drop the savepoint, if there is any.
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
        val tupleList = ArrayList<Tuple>(writes.size)
        val transaction = tx.nakshaTx
        var featuresModified = 0
        for (write in pgWrites) {
            val tupleNumber = write.tupleNumber
            tupleNumbers[write.i] = tupleNumber
            val tuple = write.tuple
            if (write.isFeatureModification) {
                val map = write.catalog
                val col = write.collection
                val txCol = transaction.useCatalog(map.id, map.catalogNumber).useCollection(col.id, col.collectionNumber)
                if (tupleNumber != null) {
                    txCol.add(tupleNumber, col.partitions)
                }
                featuresModified += 1
            } else if (write.isCatalogModification) {
                val map = write.asPgCatalog
                if (map != null) transaction.useCatalog(map.id, map.catalogNumber, write.action)
            } else if (write.isCollectionModification) {
                val map = write.catalog
                val col = write.asPgCollection
                if (col != null) {
                    transaction.useCatalog(map.id, map.catalogNumber).useCollection(col.id, col.collectionNumber, write.action)
                    map.invalidateCollection(col)
                }
            }
            if (tuple != null) tupleList.add(tuple)
        }
        transaction.featuresModified += featuresModified
        // We do not put tuples into cache, before we are sure everything was successful!
        // Adding all together into the cache reduces the effort to iterate above all caches multiple times.
        Naksha.cache.store(tupleList)
        return tupleNumbers
    }

    // Ensure that the needed physical schema and tables are created.
    // Ensure that the PgTupleWrite data class is ready for action.
    // After this has run, only the `tuple` is missing!
    private fun prepareWrite(pgWrites: ArrayList<PgWrite>): ArrayList<PgWrite>? {
        var updateCaches: ArrayList<PgWrite>? = null
        for (pgWrite in pgWrites) {
            val featureId = pgWrite.id
            val op = pgWrite.op

            // Detect tbe map into which to write.
            val catalogId = pgWrite.original.catalogId ?: throw illegalArg("The given write does not have a catalog-id")
            val pgCatalog = storage.adminCatalog.getPgCatalogById(conn, catalogId)
                ?: throw mapNotFound("The write #${pgWrite.i} refers to not existing map '$catalogId'")
            pgWrite.catalog = pgCatalog

            // Detect the collection into which to write.
            val collectionId = pgWrite.original.collectionId ?: throw illegalArg("The given write does not have a collection-id")
            val pgCollection = pgCatalog.getPgCollectionById(conn, collectionId)
                ?: throw collectionNotFound("The write #${pgWrite.i} refers to not existing collection '$collectionId'")
            pgWrite.collection = pgCollection

            // If this operation modifies a catalog.
            if (pgWrite.isCatalogModification) {
                var targetCatalog = storage.adminCatalog.getPgCatalogById(null, pgWrite.id)
                    ?: storage.adminCatalog.getPgCatalogById(conn, pgWrite.id)
                if (targetCatalog != null && targetCatalog.head.tupleNumber?.isDeleted == true) {
                    // The catalog is in deleted state, so it does not really exist.
                    targetCatalog = null
                }

                val nakshaMap: NakshaCatalog?
                if (op == WriteOp.CREATE || op == WriteOp.UPSERT || op == WriteOp.UPDATE) {
                    val feature = pgWrite.feature ?: throw illegalArg("The write #${pgWrite.i} is $op, but the feature is null")
                    nakshaMap = feature as? NakshaCatalog ?: feature.proxy(NakshaCatalog::class)
                    nakshaMap.databaseId = storage.id
                    if (targetCatalog == null) {
                        if (op == WriteOp.UPDATE) {
                            throw mapNotFound("The UPDATE (write #${pgWrite.i}) failed, because the map '$featureId' does not exist")
                        }
                        targetCatalog = PgCatalog(storage, nakshaMap)
                        createPgCatalog(targetCatalog)
                    } else if (op == WriteOp.CREATE) {
                        throw mapExists("The write #${pgWrite.i} failed, because the map '$featureId' does exist already")
                    }
                } else if (op == WriteOp.DELETE || op == WriteOp.PURGE) {
                    if (targetCatalog != null) {
                        deletePgMap(targetCatalog)
                    }
                    nakshaMap = null
                } else {
                    throw illegalState("The write #${pgWrite.i} refers to an unsupported operation: '$op'")
                }
                pgWrite.asPgCatalog = targetCatalog
                pgWrite.asNakshaMap = nakshaMap
                if (updateCaches == null) updateCaches = ArrayList()
                updateCaches.add(pgWrite)
            }

            // If this operation modifies a collection.
            if (pgWrite.isCollectionModification) {
                var targetCollection = pgCatalog.getPgCollectionById(null, pgWrite.id) ?: pgCatalog.getPgCollectionById(conn, pgWrite.id)
                if (targetCollection != null && targetCollection.head.tupleNumber?.isDeleted == true) {
                    // The collection is in deleted state, so it does not really exist.
                    targetCollection = null
                }

                val nakshaCollection: NakshaCollection?
                if (op == WriteOp.CREATE || op == WriteOp.UPSERT || op == WriteOp.UPDATE) {
                    val feature = pgWrite.feature ?: throw illegalArg("The write #${pgWrite.i} is $op, but the feature is null")
                    nakshaCollection = feature as? NakshaCollection ?: feature.proxy(NakshaCollection::class)
                    if (nakshaCollection.partitions > 1000) {
                        throw illegalArg("Invalid partition-count, expect 2 .. 1000, found : ${nakshaCollection.partitions}")
                    }
                    if (targetCollection == null) {
                        if (op == WriteOp.UPDATE) {
                            throw collectionNotFound(
                                "The UPDATE (write #${pgWrite.i}) failed, because the collection '$featureId' does not exist in map '$catalogId'"
                            )
                        }
                        targetCollection = PgCollection(pgCatalog, nakshaCollection)
                        createPgCollection(targetCollection)
                    } else if (op == WriteOp.CREATE) {
                        throw collectionExists(
                            "The write #${pgWrite.i} failed, because the collection '$featureId' does exist already in map '$catalogId'"
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
                    throw illegalState("The write #${pgWrite.i} refers to an unsupported operation: '$op'")
                }
                pgWrite.asPgCollection = targetCollection
                pgWrite.asNakshaCollection = nakshaCollection
                if (updateCaches == null) updateCaches = ArrayList()
                updateCaches.add(pgWrite)
            }
            when (op) {
                WriteOp.CREATE -> {
                    //TODO fix this hack (cloning feature) at the source i.e. Tuple.encodeFeature(), the intention is not to mutate the input
                    val original: NakshaFeature = pgWrite.feature ?: throw illegalArg("The feature #${pgWrite.i} is null")
                    val f = original.copy<NakshaFeature>(true)
                    val tuple = Tuple.encodeFeature(f, pgCollection.head, session, null, Action.CREATE, pgWrite.atomic)
                    pgWrite.tuple = tuple
                    pgWrite.tupleNumber = tuple.tupleNumber
                }
                WriteOp.UPDATE -> {
                    //TODO fix this hack (cloning feature) at the source i.e. Tuple.encodeFeature(), the intention is not to mutate the input
                    val original: NakshaFeature = pgWrite.feature ?: throw illegalArg("The feature #${pgWrite.i} is null")
                    val f = original.copy<NakshaFeature>(true)
                    val tuple = Tuple.encodeFeature(f, pgCollection.head, session, null, Action.UPDATE, pgWrite.atomic)
                    pgWrite.tuple = tuple
                    pgWrite.tupleNumber = tuple.tupleNumber
                }
                WriteOp.UPSERT -> {
                    // Note:
                    // - If the client has not provided a UUID or a UUID of a foreign feature, this results in CREATE.
                    // - If the client provide a UUID, this becomes an atomic UPDATE.
                    //
                    // To stay downward compatible, we therefore remove (as a hack) the UUID, so we ensure that we get a CREATE.
                    // TODO: Remove this hack and remove UPSERT completely from storage.
                    //TODO fix this hack (cloning feature) at the source i.e. Tuple.encodeFeature(), the intention is not to mutate the input
                    val original: NakshaFeature = pgWrite.feature ?: throw illegalArg("The feature #${pgWrite.i} is null")
                    val f = original.copy<NakshaFeature>(true)
                    val nakshaCollection = pgWrite.collection.head
                    val uuidMember = nakshaCollection.useMember(StandardMembers.Tn)
                    uuidMember.delete(f)
                    val tuple = Tuple.encodeFeature(f, pgCollection.head, session, null, Action.CREATE, null)
                    pgWrite.tuple = tuple
                    pgWrite.tupleNumber = tuple.tupleNumber
                }
                WriteOp.DELETE, WriteOp.PURGE -> {
                    // Deletes carry no tuple-number; PgWriterDelete resolves the existing HEAD by feature-number.
                    if (pgWrite.isTransactionModification) throw forbidden("Transactions must not be deleted or purged")
                }
                else -> {
                    throw illegalArg("Unknown write operation: $op")
                }
            }
        }
        return updateCaches
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
        val year = collection.historyPartitionNumberOf(tx.version.number)
        collection.ensureHistoryPartition(conn, year, session)
        collection.ensureHistoryPartition(conn, year + 1, session)
    }

    /**
     * Invoked when a [NakshaMap][naksha.model.objects.NakshaCatalog] was created.
     * @param map the map that was just created.
     * @since 3.0
     */
    protected open fun deletePgMap(map: PgCatalog) {
        storage.adminCatalog.deletePgCatalog(conn, map)
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