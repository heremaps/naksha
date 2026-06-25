@file:Suppress("OPT_IN_USAGE")

package naksha.psql

import naksha.base.PlatformUtil
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
        try {
            // This can be time-consuming, unless the connection is already open, try not to open it before we do this!
            prepareWrite(pgWrites)

            // Order by:
            // - catalog-number ASC
            // - collection-number ASC
            // - partition-number ASC
            // - PURGE, DELETE, CREATE, UPSERT, UPDATE
            // - feature-number ASC.
            //
            // The ordering is very important, because otherwise there can be deadlocks in the database at row-level locking!
            pgWrites.sortWith { a, b ->
                val catalogDiff = a.catalog.catalogNumber - b.catalog.catalogNumber
                if (catalogDiff != 0) return@sortWith catalogDiff
                val collectionDiff = a.collection.collectionNumber - b.collection.collectionNumber
                if (collectionDiff != 0) return@sortWith collectionDiff
                val partitionDiff = a.partitionNumber - b.partitionNumber
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
            if (useSavepoint) conn.execute("SAVEPOINT \"$savepointId\"").close()
            var start = 1
            var startTupleNumber: TupleNumber = pgWrites.first().tupleNumber ?: throw illegalState("PgWrite[0] without tuple-number")
            val LAST = pgWrites.size - 1
            for (i in 1.. LAST) {
                val pgWrite = pgWrites[i]
                val tupleNumber = pgWrite.tupleNumber ?: throw illegalState("PgWrite[$i] without tuple-number")
                if (start == i) {
                    startTupleNumber = tupleNumber
                    continue
                }
                if (i == LAST ||
                    startTupleNumber.catalogNumber != tupleNumber.catalogNumber ||
                    startTupleNumber.collectionNumber != tupleNumber.collectionNumber)
                {
                    // Either `i` is a different catalog/collection, then write what we have.
                    // Or we are at the last write, then write as well what we have.
                    // Note: We need to split by collection, because every collection has its own columns!
                    executeWrite(pgWrites, start, i)
                    // Continue from where we ended, if this was LAST.
                    start = i
                    startTupleNumber = tupleNumber
                }
            }
            // If everything worked out as expected, we can drop the savepoint, if there is any.
            if (useSavepoint) conn.execute("RELEASE SAVEPOINT \"$savepointId\"").close()
        } catch (t: Throwable) {
            if (conn != null && useSavepoint) conn.execute("ROLLBACK TO SAVEPOINT \"$savepointId\"").close()
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
    private fun prepareWrite(pgWrites: ArrayList<PgWrite>) {
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
            }

            // If this operation modifies a collection.
            if (pgWrite.isCollectionModification) {
                var targetCollection = pgCatalog.getPgCollectionById(null, pgWrite.id) ?: pgCatalog.getPgCollectionById(conn, pgWrite.id)

                val nakshaCollection: NakshaCollection?
                if (op == WriteOp.CREATE || op == WriteOp.UPSERT || op == WriteOp.UPDATE) {
                    val feature = pgWrite.feature ?: throw illegalArg("The write #${pgWrite.i} is $op, but the feature is null")
                    nakshaCollection = feature as? NakshaCollection ?: feature.proxy(NakshaCollection::class)
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
            }

            when (op) {
                WriteOp.CREATE -> {
                    val f = pgWrite.feature ?: throw illegalArg("The feature #${pgWrite.i} is null")
                    // In a CREATE case, UNDEFINED means the same as `null`
                    val tuple = Tuple.encodeFeature(f, pgCollection.head, Action.CREATE, session, null)
                    pgWrite.tuple = tuple
                    pgWrite.tupleNumber = tuple.tupleNumber
                }
                WriteOp.UPSERT -> {
                    // Note: We first try an INSERT, then, when that fails, we do an on-conflict UPDATE!
                    val f = pgWrite.feature ?: throw illegalArg("The feature #${pgWrite.i} is null")
                    val tuple = Tuple.encodeFeature(f, pgCollection.head, Action.CREATE, session, null)
                    pgWrite.tuple = tuple
                    pgWrite.tupleNumber = tuple.tupleNumber
                }
                WriteOp.UPDATE -> {
                    val f = pgWrite.feature ?: throw illegalArg("The feature #${pgWrite.i} is null")
                    val tuple = Tuple.encodeFeature(f, pgCollection.head, Action.UPDATE, session, null)
                    pgWrite.tuple = tuple
                    pgWrite.tupleNumber = tuple.tupleNumber
                }
                WriteOp.DELETE, WriteOp.PURGE -> {
                    // TODO: For DELETE we want to support new states, so providing a feature!
                    if (pgWrite.isTransactionModification) throw forbidden("Transactions must not be deleted or purged")
                }
                else -> {
                    throw illegalArg("Unknown write operation: $op")
                }
            }
        }
    }

    private fun MutableMap<PgCollection, MutableList<PgWrite>>.getOrCreate(collection: PgCollection): MutableList<PgWrite> {
        var list = this[collection]
        if (list == null) {
            list = ArrayList()
            this[collection] = list
        }
        return list
    }

    /**
     * Execute all writes between `start` _(inclusive)_ and `end` _(exclusive)_.
     * @param pgWrites the list of ordered writes.
     * @param start the index in the list of the first element to execute.
     * @param end the index in the list of the first element **NOT** to execute _(excluded)_.
     */
    private fun executeWrite(pgWrites: ArrayList<PgWrite>, start: Int, end: Int) {
        if (start == end) return
        // We expect that all writes go into the same catalog and collection.
        val first = pgWrites[start]
        val pgCatalog = first.catalog
        val pgCollection = first.collection
        var s = start
        var e = start

        // -------------------------------- DELETE ------------------------------------------------------------------------
        while (e < end) {
            val pgWrite = pgWrites[e]
            check(pgWrite.catalog.catalogNumber != pgCatalog.catalogNumber)
            check(pgWrite.collection.collectionNumber != pgCollection.collectionNumber)
            if (pgWrite.op != WriteOp.DELETE) break
            e++
        }
        if (e > s) {
            val tupleWriter = PgWriterDelete(this, pgCollection, pgWrites, s, e, purge = false)
            tupleWriter.execute(conn)
            s = e
        }

        // -------------------------------- PURGE ------------------------------------------------------------------------
        while (e < end) {
            val pgWrite = pgWrites[e]
            check(pgWrite.catalog.catalogNumber != pgCatalog.catalogNumber)
            check(pgWrite.collection.collectionNumber != pgCollection.collectionNumber)
            if (pgWrite.op != WriteOp.PURGE) break
            e++
        }
        //
        if (e > s) {
            val tupleWriter = PgWriterDelete(this, pgCollection, pgWrites, s, e, purge = false)
            tupleWriter.execute(conn)
            e = s
        }

        // -------------------------------- CREATE ------------------------------------------------------------------------
        while (e < end) {
            val pgWrite = pgWrites[e]
            check(pgWrite.catalog.catalogNumber != pgCatalog.catalogNumber)
            check(pgWrite.collection.collectionNumber != pgCollection.collectionNumber)
            if (pgWrite.op != WriteOp.CREATE) break
            e++
        }
        if (e > s) {
            val tupleWriter = PgWriterInsert(this, pgCollection, partition, inserts)
            tupleWriter.execute(conn)
            e = s
        }

        // -------------------------------- UPSERT ------------------------------------------------------------------------
        while (e < end) {
            val pgWrite = pgWrites[e]
            check(pgWrite.catalog.catalogNumber != pgCatalog.catalogNumber)
            check(pgWrite.collection.collectionNumber != pgCollection.collectionNumber)
            if (pgWrite.op != WriteOp.UPSERT) break
            e++
        }
        if (e > s) {
            val tupleWriter = PgWriterUpsert(this, pgCollection, partition, upserts)
            tupleWriter.execute(conn)
            e = s
        }

        // -------------------------------- UPDATE ------------------------------------------------------------------------
        while (e < end) {
            val pgWrite = pgWrites[e]
            check(pgWrite.catalog.catalogNumber != pgCatalog.catalogNumber)
            check(pgWrite.collection.collectionNumber != pgCollection.collectionNumber)
            if (pgWrite.op != WriteOp.UPDATE) break
            e++
        }
        if (e > s) {
            val tupleWriter = PgWriterUpdate(this, pgCollection, partition, updates)
            tupleWriter.execute(conn)
            e = s
        }

        if (e != s) throw illegalState("We missed some writes beyond $s in the ordered write-operation list")
    }

    /**
     * Invoked when a [NakshaMap][naksha.model.objects.NakshaCatalog] should be physically created.
     * @param catalog the catalog that should be physically created.
     * @since 3.0
     */
    protected open fun createPgCatalog(catalog: PgCatalog) {
        storage.adminCatalog.createPgCatalog(conn, catalog)
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