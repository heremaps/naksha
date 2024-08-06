package naksha.psql.executors

import naksha.base.Int64
import naksha.model.*
import naksha.model.Naksha.NakshaCompanion.VIRT_COLLECTIONS
import naksha.model.NakshaError.NakshaErrorCompanion.COLLECTION_NOT_FOUND
import naksha.model.NakshaError.NakshaErrorCompanion.MAP_NOT_FOUND
import naksha.model.NakshaError.NakshaErrorCompanion.UNSUPPORTED_OPERATION
import naksha.model.objects.NakshaCollection
import naksha.model.request.*
import naksha.psql.*
import kotlin.jvm.JvmField

// TODO: We need to fix NakshaBulkLoaderPlan to make this faster again !
//       Maybe it can just extend the simple writer, and add bulk loading capabilities?
//       It could reuse some of the code, especially about result-set building, because
//       it would not have to return the written data, actually, it can be highly optimised
//       to only do writing as fast as possible, because we only need the row-ids.
//       It should store what it has into the NakshaCache, but when its not available,
//       forget about it, the result-set will load it on demand (separation of concerns)
//       The point is: When the client does not need the results, best performance is available!

/**
 * A simple basic implementation of a writer.
 *
 * It does ignore optional settings within the [WriteRequest], it actually can be used for any simple list of write instructions, especially as well internally. Its performance is sub-optional, but fine for small amount of writes. It acts as a reference implementation that can be extended.
 */
class PgWriter(
    /**
     * The session to which this writer is linked.
     */
    session: PgSession,

    /**
     * The write instructions to [execute].
     */
    @JvmField val request: WriteRequest,

    /**
     * The connection to use.
     */
    @JvmField val conn: PgConnection = session.usePgConnection()
) : RowUpdater(session) {

    /**
     * The writes to act upon.
     */
    private val writes = request.writes

    /**
     * All writes that create, update or delete collections.
     */
    private val collectionWrites: WriteExtList

    /**
     * The write operations ordered by:
     * - map-id
     * - collection-id
     * - operation (CREATE, UPSERT, UPDATE, DELETE, PURGE, UNKNOWN)
     * - partition-number
     * - feature-id
     *
     * This is very important to prevent deadlocks in the database, all code that modifies features must use the same ordering!
     */
    private val orderedWrites: WriteExtList

    /**
     * The storage.
     */
    val storage: PgStorage = session.storage

    /**
     * The version of which this writer is part.
     */
    val version: Version
        get() = session.version()

    /**
     * Returns the transaction number.
     */
    fun txn(): Int64 = version.txn

    /**
     * Returns a new `uid` for this write-operation.
     */
    fun newUid(): Int = session.uid.getAndAdd(1)

    init {
        // Split into collection modifications, and feature modifications.
        val collectionWrites = WriteExtList()
        val orderedWrites = WriteExtList()
        orderedWrites.setCapacity(writes.getCapacity()) // Ensure we do not have to resize array-list too often in Java!

        val writes = this.writes
        var i = 0
        while (i < writes.size) {
            val write = writes[i]
            val writeExt = write?.proxy(WriteExt::class)
            writeExt?.i = i
            // TODO: Add pre-processing here, if we need any.
            if (writeExt?.collectionId == VIRT_COLLECTIONS) collectionWrites.add(writeExt) else orderedWrites.add(writeExt)
            i++
        }
        orderedWrites.sortedWith(Write::sortCompare)

        this.orderedWrites = orderedWrites
        this.collectionWrites = collectionWrites
    }

    // We keep the references to prevent garbage collection
    private val tuples = TupleList()
    private val tupleNumbers = TupleNumberList()

    fun execute(): Response {
        val tuples = this.tuples
        val tupleNumbers = this.tupleNumbers
        val tupleCache = NakshaCache.tupleCache(storage.id)

        // First, process collections, no performance need here for now.
        val collectionWrites = this.collectionWrites
        for (write in collectionWrites) {
            if (write == null) continue
            val tuple = when (write.op) {
                WriteOp.CREATE -> createCollection(mapOf(write), write)
                WriteOp.UPSERT -> upsertCollection(mapOf(write), write)
                WriteOp.UPDATE -> updateCollection(mapOf(write), write)
                WriteOp.DELETE -> deleteCollection(mapOf(write), write)
                WriteOp.PURGE -> purgeCollection(mapOf(write), write)
                else -> throw NakshaException(UNSUPPORTED_OPERATION, "Unknown write-operation: '${write.op}'")
            }
            tuples[write.i] = tuple
            tupleNumbers[write.i] = tuple.tupleNumber
            tupleCache.store(tuple)
        }

        // Now, perform all feature operations.
        // TODO: Fix NakshaBulkLoaderPlan, this is the "slow" path!
        val orderedWrites = this.orderedWrites
        for (write in orderedWrites) {
            if (write == null) continue
            val tuple = when (write.op) {
                WriteOp.CREATE -> createFeature(collectionOf(write), write)
                WriteOp.UPSERT -> upsertFeature(collectionOf(write), write)
                WriteOp.UPDATE -> updateFeature(collectionOf(write), write)
                WriteOp.DELETE -> deleteFeature(collectionOf(write), write)
                WriteOp.PURGE -> purgeFeature(collectionOf(write), write)
                else -> throw NakshaException(UNSUPPORTED_OPERATION, "Unknown write-operation: '${write.op}'")
            }
            tuples[write.i] = tuple
            tupleNumbers[write.i] = tuple.tupleNumber
            tupleCache.store(tuple)
        }

        // If everything was done perfectly, fine.
        val tnba = TupleNumberByteArray(storage, tupleNumbers.toByteArray())
        return SuccessResponse(
            PgResultSet(
                storage, tnba,
                incomplete = false,
                validTill = tnba.size,
                offset = 0,
                limit = tnba.size,
                orderBy = null,
                filters = request.resultFilters
            )
        )
    }

    private fun mapOf(write: WriteExt): PgMap {
        val mapId = write.mapId
        if (mapId !in storage) throw NakshaException(MAP_NOT_FOUND, "No such map: '$mapId'")
        val map = storage[mapId]
        if (!map.exists(conn)) throw NakshaException(MAP_NOT_FOUND, "No such map: '$mapId'")
        return map
    }

    private fun collectionOf(write: WriteExt): PgCollection {
        val map = mapOf(write)
        val collectionId = write.collectionId
        val collection = map[collectionId]
        if (!collection.exists(conn)) throw NakshaException(COLLECTION_NOT_FOUND, "No such collection: $collectionId")
        return collection
    }

    internal fun createCollection(map: PgMap, write: WriteExt): Tuple {
        // Note: write.collectionId is always naksha~collections!
        val c = write.feature?.proxy(NakshaCollection::class)
        val collectionId = write.featureId
        TODO("Finish me")
    }

    internal fun updateCollection(map: PgMap, write: WriteExt): Tuple {
        TODO("Implement me")
    }

    internal fun upsertCollection(map: PgMap, write: WriteExt): Tuple {
        TODO("Implement me")
    }

    internal fun deleteCollection(map: PgMap, write: WriteExt): Tuple {
        TODO("Implement me")
    }

    internal fun purgeCollection(map: PgMap, write: WriteExt): Tuple {
        TODO("Implement me")
    }

    internal fun createFeature(collection: PgCollection, write: WriteExt): Tuple {
        TODO("Implement me")
    }

    internal fun updateFeature(collection: PgCollection, write: WriteExt): Tuple {
        TODO("Implement me")
    }

    internal fun upsertFeature(collection: PgCollection, write: WriteExt): Tuple {
        TODO("Implement me")
    }

    internal fun deleteFeature(collection: PgCollection, write: WriteExt): Tuple {
        TODO("Implement me")
    }

    internal fun purgeFeature(collection: PgCollection, write: WriteExt): Tuple {
        TODO("Implement me")
    }
}