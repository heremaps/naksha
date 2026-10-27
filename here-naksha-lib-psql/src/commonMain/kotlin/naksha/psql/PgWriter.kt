@file:Suppress("OPT_IN_USAGE")

package naksha.psql

import naksha.base.Action
import naksha.base.Id
import naksha.base.PlatformUtil
import naksha.base.collectionNotFound
import naksha.base.forbidden
import naksha.base.illegalArg
import naksha.base.illegalState
import naksha.base.catalogExists
import naksha.base.catalogNotFound
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
     * The Naksha transaction.
     * @since 3.0
     */
    val transaction: NakshaTx
        get() = session.useTransaction()

    /**
     * Performs the given writes.
     * @param writes the writes to perform.
     * @return the response.
     */
    fun execute(writes: WriteList) : Response {
        val featureTupleList = executeWrites(writes.mapNotNull { it }.toMutableList())
        return SuccessResponse().withFeatureTupleList(featureTupleList)
    }

    /**
     * Performs the given writes.
     *
     * The returned [FeatureTupleList] carries the written tuples by hard reference (in input order), so
     * the response keeps them reachable for as long as it is used, instead of relying on the soft-referenced
     * cache retaining them.
     * @param writes the writes to perform.
     * @return the written feature-tuples, in input order (writes without a tuple-number are skipped).
     */
    private fun executeWrites(writes: MutableList<Write>) : FeatureTupleList {
        if (writes.isEmpty()) return FeatureTupleList()
        // Add the input-index as preparation for sorting by db, catalog, collection, partition.
        val pgWrites = ArrayList<PgWrite>(writes.size)
        for (i in 0 until writes.size) {
            pgWrites.add(PgWrite(writes[i], i))
        }
        val savepointId = PlatformUtil.randomString()
        session.useConn(false) { conn ->
            try {
                prepareWrite(conn, pgWrites)

                // The ordering is very important, because otherwise there can be deadlocks in the database at row-level locking!
                pgWrites.sortWith { a, b -> a.id.compareTo(b.id) }

                // Perform the writes, if any error happens, we will roll back the session to where it was before we started.
                // Note: We must not close the connection, therefore no `session.useConnection().use {}`!
                if (useSavepoint) {
                    conn.execute("SAVEPOINT \"$savepointId\"").close()
                }
                var start = 0
                for (i in 1..pgWrites.size) {
                    if (i == pgWrites.size ||
                        pgWrites[i].dbCatalog.id != pgWrites[start].dbCatalog.id ||
                        pgWrites[i].dbCollection.id != pgWrites[start].dbCollection.id
                    ) {
                        executeWrite(conn, pgWrites, start, i)
                        start = i
                    }
                }
                // We can drop the savepoint, if there is any.
                if (useSavepoint) conn.execute("RELEASE SAVEPOINT \"$savepointId\"").close()
            } catch (t: Throwable) {
                if (useSavepoint) {
                    conn.execute("ROLLBACK TO SAVEPOINT \"$savepointId\"").close()
                }
                throw PgExceptionMapper.map(t)
            }
        }

        // Reorder results to match input.
        val tupleList = ArrayList<Tuple>(writes.size)
        val transaction = this.transaction
        var featuresModified = 0
        for (pgWrite in pgWrites) {
            val tupleNumber = pgWrite.tupleNumber
            val tuple = pgWrite.tuple
            if (pgWrite.isCatalogModification) {
                transaction.useCatalog(pgWrite.id, pgWrite.action)
            } else if (pgWrite.isCollectionModification) {
                transaction.useCatalog(pgWrite.dbCatalog.id).useCollection(pgWrite.id, pgWrite.action)
            } else {
                val catalog = pgWrite.dbCatalog
                val collection = pgWrite.dbCollection
                val txCol = transaction.useCatalog(catalog.id).useCollection(collection.id)
                if (tupleNumber != null) {
                    txCol.add(tupleNumber, collection.partitions)
                }
                featuresModified += 1
            }
            if (tuple != null) tupleList.add(tuple)
        }
        transaction.featuresModified += featuresModified
        // We do not put tuples into cache, before we are sure everything was successful!
        // Adding all together into the cache reduces the effort to iterate above all caches multiple times.
        Naksha.cache.store(tupleList)

        val featureTuples = FeatureTupleList()
        featureTuples.setCapacity(writes.size)
        for (pgWrite in pgWrites) {
            val tupleNumber = pgWrite.tupleNumber
            if (tupleNumber != null) {
                val tuple = pgWrite.tuple
                featureTuples[pgWrite.i] = if (tuple != null) FeatureTuple(tupleNumber, tuple) else FeatureTuple(tupleNumber)
            }
        }
        return featureTuples
    }

    /**
     * Creates or deletes schemas and tables. Ensure that the [PgWrite] data class has all properties correctly set; throws a [naksha.base.NakshaException] when any error is encountered. After this method returns, the operation is ready to be executed.
     * @param conn the connection to use to read/write.
     * @param pgWrites the writes to process.
     * @since 3.0
    */
    private fun prepareWrite(conn: PgConnection, pgWrites: ArrayList<PgWrite>) {
        for (pgWrite in pgWrites) {
            // Detect tbe catalog into which to write (can be `naksha~admin`).
            val catalogId: Id = pgWrite.original.catalogId ?: throw illegalArg("The given write does not have a catalog-id")
            val catalogNumber = catalogId.intValue
            val dbCatalog: PgCatalog = session.getPgCatalog(catalogNumber, conn)
                ?: throw catalogNotFound("The write #${pgWrite.i} refers to not existing catalog '$catalogId'")
            pgWrite.dbCatalog = dbCatalog

            // Detect the collection into which to write.
            val collectionId: Id = pgWrite.original.collectionId ?: throw illegalArg("The given write does not have a collection-id")
            val collectionNumber = collectionId.intValue
            val dbCollection: PgCollection = session.getPgCollection(catalogNumber, collectionNumber, conn)
                ?: throw collectionNotFound("The write #${pgWrite.i} refers to not existing collection '$collectionId'")
            pgWrite.dbCollection = dbCollection

            // Ensure the feature is of the correct type.
            val id = pgWrite.id
            val feature = pgWrite.feature
            if (feature != null) {
                if (pgWrite.isCatalogModification) {
                    val catalog = feature as? NakshaCatalog ?: feature.proxy(NakshaCatalog::class)
                    catalog.databaseId = storage.database.id.text
                    pgWrite.feature = catalog
                } else if (pgWrite.isCollectionModification) {
                    val collection = feature as? NakshaCollection ?: feature.proxy(NakshaCollection::class)
                    collection.databaseId = storage.database.id.text
                    if (collection.partitions !in 1..256) {
                        throw illegalArg("Invalid partition-count for collection '$id', expect 1 .. 256, found: ${collection.partitions}")
                    }
                    pgWrite.feature = collection
                } else if (pgWrite.isTransactionModification) {
                    val transaction = feature as? NakshaTx ?: feature.proxy(NakshaTx::class)
                    pgWrite.feature = transaction
                }
            }

            // Even when catalogs or collection are modified, they still have a normal features as metadata.
            // Therefore, everything that is done must follow the normal feature modification steps.
            when (val op = pgWrite.op) {
                WriteOp.CREATE -> {
                    val f: NakshaFeature = pgWrite.feature ?: throw illegalArg("The feature #${pgWrite.i} is null")
                    val tuple = Tuple.encodeFeature(f, dbCollection.naksha, session, action = Action.CREATE)
                    pgWrite.tuple = tuple
                    pgWrite.tupleNumber = tuple.tupleNumber
                    if (f is NakshaCatalog) {
                        val pgCatalog = PgCatalog(tuple)
                        if (storage.database.catalog_get_or_create_schema(conn, name = pgCatalog.name) < 0) {
                            throw catalogExists("Creation of catalog '$id' failed, it exists already")
                        }
                        pgWrite.pgCatalog = pgCatalog
                        session.cachePgCatalog(pgCatalog) // This allows following write instructions to succeed.
                    } else if (f is NakshaCollection) {
                        val pgCollection = PgCollection(catalogId, tuple)
                        storage.database.collection_create_table_if_not_exists(
                            conn = conn,
                            schema = pgWrite.dbCatalog.name,
                            table = id.text,
                            columns = pgCollection.columns,
                            indices = pgCollection.indices,
                            shift = pgCollection.shift,
                            partitions = pgCollection.partitions,
                        )
                        pgWrite.pgCollection = pgCollection
                        session.cachePgCollection(pgCollection) // This allows following write instructions to succeed.
                    } // else — No special database operations needed upfront.
                }
                WriteOp.UPDATE -> {
                    val f: NakshaFeature = pgWrite.feature ?: throw illegalArg("The feature #${pgWrite.i} is null")
                    val tuple = Tuple.encodeFeature(f, dbCollection.naksha, session, action = Action.UPDATE, atomic = pgWrite.requireVersion != null)
                    pgWrite.tuple = tuple
                    pgWrite.tupleNumber = tuple.tupleNumber
                    // No special database operations needed upfront.
                }
                WriteOp.UPSERT -> {
                    // Note:
                    // - We can't encode an UPSERT, therefore, we start encoding a CREATE,
                    //   and when that fails, the code will automatically switch to UPDATE.
                    //   This switch is done in the SQL code send to the database, we then
                    //   check the result.
                    //
                    // To stay downward compatible, we therefore remove (as a hack) the UUID, so we ensure that we get a CREATE.
                    val tnMember = pgWrite.dbCollection.naksha.validatedMembers.useMember(StandardMembers.Tn)
                    val feature = pgWrite.feature ?: throw illegalArg("The feature #${pgWrite.i} is null")
                    val f = Tuple.shallowCopy(feature, arrayOf(tnMember.path))
                    tnMember.delete(f)
                    val tuple = Tuple.encodeFeature(f, dbCollection.naksha, session, action = Action.CREATE)
                    pgWrite.tuple = tuple
                    pgWrite.tupleNumber = tuple.tupleNumber
                    if (f is NakshaCatalog) {
                        val pgCatalog = PgCatalog(tuple)
                        storage.database.catalog_get_or_create_schema(conn, name = pgCatalog.name)
                        pgWrite.pgCatalog = pgCatalog
                        session.cachePgCatalog(pgCatalog) // This allows following write instructions to succeed.
                    } else if (f is NakshaCollection) {
                        val pgCollection = PgCollection(catalogId, tuple)
                        storage.database.collection_create_table_if_not_exists(
                            conn = conn,
                            schema = pgWrite.dbCatalog.name,
                            table = id.text,
                            columns = pgCollection.columns,
                            indices = pgCollection.indices,
                            shift = pgCollection.shift,
                            partitions = pgCollection.partitions,
                        )
                        pgWrite.pgCollection = pgCollection
                        session.cachePgCollection(pgCollection) // This allows following write instructions to succeed.
                    } // else — No special database operations needed upfront.
                }
                // TODO: Fix that DELETE uses feature and can produce a valid new state!
                // TODO: Fix that we can make empty tombstones, so new tombstone rows with no data!
                WriteOp.DELETE, WriteOp.PURGE -> {
                    // Deletes carry no tuple-number; PgWriterDelete resolves the existing HEAD by feature-number.
                    if (pgWrite.isTransactionModification) throw forbidden("Transactions must not be deleted or purged")
                }
                else -> {
                    throw illegalArg("Unknown write operation: $op")
                }
            }
        }
    }

    /**
     * Execute all writes between `start` _(inclusive)_ and `end` _(exclusive)_.
     * @param conn the connection to use for modifications.
     * @param pgWrites the list of ordered writes.
     * @param start the index in the list of the first element to execute.
     * @param end the index in the list of the first element **NOT** to execute _(excluded)_.
     */
    private fun executeWrite(conn: PgConnection, pgWrites: ArrayList<PgWrite>, start: Int, end: Int) {
        if (start == end) return
        // All writes in [start, end) share one catalog and collection (the caller grouped them) and are
        // ordered by op; dispatch each contiguous op-block to its writer.
        val pgCollection = pgWrites[start].dbCollection
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

}
