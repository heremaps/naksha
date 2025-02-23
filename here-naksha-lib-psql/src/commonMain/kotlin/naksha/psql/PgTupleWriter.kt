@file:Suppress("OPT_IN_USAGE")

package naksha.psql

import naksha.model.*
import naksha.model.objects.NakshaCollection
import naksha.model.objects.NakshaMap
import naksha.model.request.Write
import naksha.model.request.WriteOp
import kotlin.js.JsExport

/**
 * A helper to write tuples into collections. The class can be extended and the
 * @since 3.0
 * @see [PgTupleWrite]
 */
@JsExport
open class PgTupleWriter internal constructor(val session: PgSession) {
    /**
     * The storage to operate on.
     * @since 3.0
     */
    val storage: PgStorage = session.storage

    /**
     * The database connection to use for modifications.
     * @since 3.0
     */
    val conn: PgConnection = session.useConnection()

    /**
     * The transaction to update with what was done.
     * @since 3.0
     */
    val tx = session.useTx()

    /**
     * Performs the given writes.
     * @param writes the writes to perform.
     * @return the tuple-numbers of the
     */
    fun execute(writes: MutableList<Write>) : TupleNumberList {
        // Add the input-index.
        val targetWrites = ArrayList<PgTupleWrite>(writes.size)
        for (i in 0 ..< writes.size) targetWrites.add(PgTupleWrite(writes[i], i))

        // We sort the writes so that admin-map, map's collection, collection's collection are first.
        // The rest is ordered by map-id, collection-id, feature-id, operation (INSERT, UPSERT, UPDATE, DELETE, PURGE).
        // This guarantees that we first created the maps, then the collections, and finally perform the rest.
        // The ordering is important to prevent deadlocks between different clients.
        targetWrites.sortWith { a, b -> Write.sortCompare(a.original, b.original) }

        // Perform the writes in sorted order.
        prepareWrite(targetWrites)
        executeWrite(targetWrites)

        // Reorder results to match input.
        val tupleNumbers = TupleNumberList()
        tupleNumbers.setCapacity(writes.size)
        val tupleList = ArrayList<Tuple>(writes.size)
        for (write in targetWrites) {
            tupleNumbers[write.i] = write.tuple.tupleNumber
            tupleList.add(write.tuple)
        }
        // We do not put tuples into cache, before we are sure everything was successful!
        // Adding all together into the cache reduces the effort to iterate above all caches multiple times.
        Naksha.cache.store(tupleList)
        return tupleNumbers
    }

    // Ensure that the needed physical schema and tables are created.
    // Ensure that the PgTupleWrite data class is ready for action.
    // After this has run, only the `tuple` is missing!
    private fun prepareWrite(writes: ArrayList<PgTupleWrite>) {
        for (write in writes) {
            val featureId = write.original.featureId
            val mapId = write.original.mapId
            val map = storage.adminMap.getPgMapById(conn, mapId) ?:
                throw mapNotFound("The write #${write.i} refers to not existing map '$mapId'")
            write.map = map

            val colId = write.original.collectionId
            val collection = storage.adminMap.getPgCollectionById(conn, map, colId) ?:
                throw collectionNotFound("The write #${write.i} refers to not existing collection '$colId'")
            write.collection = collection

            // If this operation modifies a map.
            if (write.original.isMapModification()) {
                val op = write.original.op
                val feature = write.original.feature ?: throw illegalArg("The feature #${write.i} is null")
                val nakshaMap = if (feature is NakshaMap) feature else feature.proxy(NakshaMap::class)
                nakshaMap.storageId = storage.id

                var targetMap = storage.adminMap.getPgMapById(conn, featureId)
                if (op == WriteOp.CREATE || op == WriteOp.UPSERT) {
                    if (targetMap == null) {
                        targetMap = PgMap(storage, nakshaMap)
                        createPgMap(targetMap)
                    } else if (op == WriteOp.CREATE) {
                        throw mapExists("The write #${write.i} failed, because the map '$featureId' does exist already")
                    }
                } else if (op == WriteOp.DELETE || op == WriteOp.PURGE) {
                    if (targetMap != null) deletePgMap(targetMap)
                } else {
                    throw illegalState("The write #${write.i} refers to an unsupported operation: '$op'")
                }
                write.pgMap = targetMap
                write.nakshaMap = nakshaMap
            }

            // If this operation modifies a collection.
            if (write.original.isCollectionModification()) {
                val op = write.original.op
                val feature = write.original.feature ?: throw illegalArg("The feature #${write.i} is null")
                val nakshaCollection = if (feature is NakshaCollection) feature else feature.proxy(NakshaCollection::class)

                var targetCollection = storage.adminMap.getPgCollectionById(conn, map, featureId)
                if (op == WriteOp.CREATE || op == WriteOp.UPSERT) {
                    if (targetCollection == null) {
                        targetCollection = PgCollection(map, nakshaCollection)
                        createPgCollection(targetCollection)
                    } else if (op == WriteOp.CREATE) {
                        throw mapExists("The write #${write.i} failed, because the collection '$featureId' does exist already in map $mapId")
                    }
                } else if (op == WriteOp.DELETE || op == WriteOp.PURGE) {
                    if (targetCollection != null) deletePgCollection(targetCollection)
                } else {
                    throw illegalState("The write #${write.i} refers to an unsupported operation: '$op'")
                }
                write.pgCollection = targetCollection
                write.nakshaCollection = nakshaCollection
            }
        }
    }

    private fun MutableMap<PgCollection, MutableList<PgTupleWrite>>.getOrCreate(collection: PgCollection): MutableList<PgTupleWrite> {
        var list = this[collection]
        if (list == null) {
            list = ArrayList()
            this[collection] = list
        }
        return list
    }

    private fun executeWrite(writes: ArrayList<PgTupleWrite>) {
        // We group the features into collections into which we should write.
        val inserts = mutableMapOf<PgCollection, MutableList<PgTupleWrite>>()
        val upserts = mutableMapOf<PgCollection, MutableList<PgTupleWrite>>()
        val updates = mutableMapOf<PgCollection, MutableList<PgTupleWrite>>()
        val deletes = mutableMapOf<PgCollection, MutableList<PgTupleWrite>>()
        val purges = mutableMapOf<PgCollection, MutableList<PgTupleWrite>>()
        for (write in writes) {
            when (val op = write.original.op) {
                WriteOp.CREATE -> {
                    write.tuple = tx.created(write.map.nakshaMap, write.collection.nakshaCollection, write.feature)
                    inserts.getOrCreate(write.collection).add(write)
                }
                WriteOp.UPSERT -> {
                    // Note: We first try an INSERT, then, when that fails, we do an on-conflict UPDATE!
                    write.tuple = tx.created(write.map.nakshaMap, write.collection.nakshaCollection, write.feature)
                    upserts.getOrCreate(write.collection).add(write)
                }
                WriteOp.UPDATE -> {
                    write.tuple = tx.updated(write.map.nakshaMap, write.collection.nakshaCollection, write.feature)
                    updates.getOrCreate(write.collection).add(write)
                }
                WriteOp.DELETE -> {
                    write.tuple = tx.deleted(write.map.nakshaMap, write.collection.nakshaCollection, write.feature)
                    deletes.getOrCreate(write.collection).add(write)
                }
                WriteOp.PURGE -> {
                    // Note: purge and delete are the same operation, except that a purge is not copied into deleted table!
                    write.tuple = tx.deleted(write.map.nakshaMap, write.collection.nakshaCollection, write.feature)
                    purges.getOrCreate(write.collection).add(write)
                }
                else -> {
                    throw illegalArg("Unknown write operation: $op")
                }
            }
        }

        // INSERT
        for (mapEntry in inserts) {
            val collection = mapEntry.key
            val tgWrites = mapEntry.value
            val tupleWriter = PgTupleWriterInsert(session, collection, tgWrites)
            tupleWriter.execute(conn)
        }

        // UPSERT
        // UPDATE
        // DELETE
        // PURGE
    }

    /**
     * Invoked when a [NakshaMap][naksha.model.objects.NakshaMap] should be physically created.
     * @param map the map that should be physically created.
     * @since 3.0
     */
    protected open fun createPgMap(map: PgMap) {
        storage.adminMap.createPgMap(conn, map)
    }

    /**
     * Invoked when a [NakshaMap][naksha.model.objects.NakshaMap] was created.
     * @param map the map that was just created.
     * @since 3.0
     */
    protected open fun deletePgMap(map: PgMap) {
        storage.adminMap.deletePgMap(conn, map)
    }

    /**
     * Invoked when a [NakshaCollection][naksha.model.objects.NakshaCollection] should be physically created.
     * @param collection the collection that should be physically created.
     * @since 3.0
     */
    protected open fun createPgCollection(collection: PgCollection) {
        storage.adminMap.createPgCollection(conn, collection)
    }

    /**
     * Invoked when a [NakshaCollection][naksha.model.objects.NakshaCollection] should be physically created.
     * @param collection the collection that should be physically created.
     * @since 3.0
     */
    protected open fun deletePgCollection(collection: PgCollection) {
        storage.adminMap.deletePgCollection(conn, collection)
    }
}

/*


SELECT * FROM UNNEST(
    ARRAY[1, 2, 3] AS col1,
    ARRAY['a', 'b', 'c'] AS col2,
    ARRAY[10.1, 20.2, 30.3] AS col3
) AS t(col1, col2, col3);


UPSERT

WITH new_row AS (
    VALUES
        ($1::int, $2::text, $3::text),   -- First row from client
        ($4::int, $5::text, $6::text)    -- Second row from client
)
WITH head_row AS (
    SELECT * FROM ${head_table}
    WHERE id = new_row.id
    FOR UPDATE NOWAIT
),
deleted_from_head AS (
    DELETE FROM ${head_table}
    WHERE tn IN (SELECT tn FROM head_row)
    RETURNING id
),
history_row AS (
    INSERT INTO ${history_table}
    (id, tn, ...)
    SELECT next_txn=${txn}, h.*
    FROM head_row h
    RETURNING tn
),
INSERT INTO ${head_table}
(id, tn, ...)
SELECT n.*, d.id as prev_tn
FROM new_row n
LEFT JOIN deleted_from_head d ON n.id = d.id
RETURNING tn



 */