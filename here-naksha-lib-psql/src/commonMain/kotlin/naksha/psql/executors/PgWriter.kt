package naksha.psql.executors

import naksha.base.Int64
import naksha.model.*
import naksha.model.Naksha.NakshaCompanion.VIRT_COLLECTIONS
import naksha.model.Naksha.NakshaCompanion.partitionNumber
import naksha.model.NakshaError.NakshaErrorCompanion.COLLECTION_NOT_FOUND
import naksha.model.NakshaError.NakshaErrorCompanion.ILLEGAL_ARGUMENT
import naksha.model.NakshaError.NakshaErrorCompanion.MAP_NOT_FOUND
import naksha.model.NakshaError.NakshaErrorCompanion.UNSUPPORTED_OPERATION
import naksha.model.objects.NakshaCollection
import naksha.model.objects.NakshaFeature
import naksha.model.request.*
import naksha.psql.*
import naksha.psql.executors.write.*
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
    @JvmField val session: PgSession,

    /**
     * The write instructions to [execute].
     */
    @JvmField val request: WriteRequest,

    /**
     * The write executor implementation: instant execution or bulk execution, etc.
     */
    @JvmField val writeExecutor: WriteExecutor,
) {

    /**
     * The connection to use.
     */
    val conn: PgConnection = session.usePgConnection()

    /**
     * The writes to act upon.
     */
    private val writes = request.writes

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
     * Returns the [flags][Flags] to use, when encoding new rows.
     */
    fun flags(collection: NakshaCollection): Flags =
        collection.defaultFlags ?: session.storage.defaultFlags

    /**
     * Returns the encoding to store for the [feature-type][Metadata.type].
     * @param collection the collection in which to store the feature.
     * @param feature the feature.
     * @return the type to store in [Metadata].
     */
    fun featureType(collection: NakshaCollection, feature: NakshaFeature): String? {
        val type =
            feature.momType ?: feature.momType ?: feature.properties.featureType ?: feature.type
        return if (type == collection.defaultType) null else type
    }

    /**
     * Returns the transaction number.
     */
    fun txn(): Int64 = version.txn

    /**
     * Returns the update-time to be set.
     */
    fun updateTime(): Int64 = session.versionTime()

    /**
     * Returns a new `uid` for a new tuple.
     */
    fun newUid(): Int = session.uid.getAndAdd(1)

    /**
     * Generate a new collection-number.
     * @param map the map in which to create a new map.
     * @return the new collection-number of the new collection.
     */
    fun newCollectionNumber(map: PgMap): Int64 = map.newCollectionNumber(conn)

    /**
     * Creates a new tuple-number for a new collection (to be created).
     * @param map the map in which the collection is stored.
     * @param collectionNumber the collection-number of the collection.
     * @return a new tuple-number.
     */
    fun collectionTupleNumber(map: PgMap, collectionNumber: Int64): TupleNumber =
        TupleNumber(StoreNumber(map.number, collectionNumber, 0), version, newUid())

    /**
     * Creates a new tuple-number for an existing collection.
     * @param collection the collection for which to return a new tuple-number.
     * @return a new tuple-number.
     */
    fun collectionTupleNumber(collection: PgCollection): TupleNumber =
        TupleNumber(StoreNumber(collection.map.number, collection.number, 0), version, newUid())

    /**
     * Creates a new tuple-number for a feature.
     * @param collection the collection in which the feature is stored.
     * @param featureId the feature-id for which to generate a new tuple-number.
     * @return a new tuple-number.
     */
    fun newFeatureTupleNumber(collection: PgCollection, featureId: String): TupleNumber =
        TupleNumber(
            StoreNumber(
                collection.map.number,
                collection.number,
                partitionNumber(featureId)
            ), version, newUid()
        )

    /**
     * The write operations ordered by:
     * - map-id
     * - collection-id
     * - partition-number
     * - operation (CREATE, UPSERT, UPDATE, DELETE, PURGE, UNKNOWN)
     * - feature-id
     *
     * This is very important to prevent deadlocks in the database, all code that modifies features must use the same ordering!
     */
    private val orderedWrites: WriteExtList

    init {
        val writes = this.writes
        val orderedWrites = WriteExtList()
        orderedWrites.setCapacity(writes.getCapacity()) // Ensure we do not have to resize array-list too often in Java!
        var i = 0
        while (i < writes.size) {
            val write = writes[i]
            val writeExt = write?.proxy(WriteExt::class)
            writeExt?.i = i
            // TODO: Add pre-processing here, if we need any.
            orderedWrites.add(writeExt)
            i++
        }
        orderedWrites.sortWith(Write::sortCompare)
        this.orderedWrites = orderedWrites
    }

    // We should keep references generated tuples to be able to cached them for the result-set!
    private val tupleCache = NakshaCache.tupleCache(storage.id)
    private val tuples = TupleList()
    private val tupleNumbers = TupleNumberList()

    fun execute(): Response {
        val tupleNumbers = this.tupleNumbers
        val previousMetadataProvider = ExistingMetadataProvider(session, orderedWrites)

        // First, process collections, no performance need here for now.
        for (write in orderedWrites) {
            if (write == null) continue
            val tupleNumber: TupleNumber = if (write.collectionId == VIRT_COLLECTIONS) {
                when (write.op) {
                    WriteOp.CREATE -> cachedTupleNumber(
                        write,
                        CreateCollection(session).execute(mapOf(write), write)
                    )

                    WriteOp.UPSERT -> cachedTupleNumber(
                        write,
                        upsertCollection(mapOf(write), write)
                    )

                    WriteOp.UPDATE -> cachedTupleNumber(
                        write,
                        updateCollection(mapOf(write), write)
                    )

                    WriteOp.DELETE, WriteOp.PURGE -> DropCollection(session).execute(
                        mapOf(write),
                        write
                    )

                    else -> throw NakshaException(
                        UNSUPPORTED_OPERATION,
                        "Unknown write-operation: '${write.op}'"
                    )
                }
            } else {
                val collection = collectionOf(write)
                when (write.op) {
                    WriteOp.CREATE -> cachedTupleNumber(
                        write,
                        InsertFeature(session, writeExecutor).execute(collection, write)
                    )

                    WriteOp.UPSERT ->
                        if (write.id == null || previousMetadataProvider.get(
                                collection.head.name,
                                write.id!!
                            ) == null
                        ) {
                            cachedTupleNumber(
                                write,
                                InsertFeature(session, writeExecutor).execute(collection, write)
                            )
                        } else {
                            cachedTupleNumber(
                                write,
                                UpdateFeature(
                                    session,
                                    previousMetadataProvider,
                                    writeExecutor
                                ).execute(collection, write)
                            )
                        }

                    WriteOp.UPDATE -> cachedTupleNumber(
                        write,
                        UpdateFeature(session, previousMetadataProvider, writeExecutor).execute(
                            collection,
                            write
                        )
                    )

                    WriteOp.DELETE -> DeleteFeature(session, writeExecutor).execute(
                        collection,
                        write
                    )

                    WriteOp.PURGE -> TODO()
                    else -> throw NakshaException(
                        UNSUPPORTED_OPERATION,
                        "Unknown write-operation: '${write.op}'"
                    )
                }
            }
            tupleNumbers[write.i] = tupleNumber
        }

        writeExecutor.finish()
        // If everything was done perfectly, fine.
        val tupleNumberByteArray = TupleNumberByteArray(storage, tupleNumbers.toByteArray())
        return SuccessResponse(
            PgResultSet(
                storage,
                session,
                tupleNumberByteArray,
                incomplete = false,
                validTill = tupleNumberByteArray.size,
                offset = 0,
                limit = tupleNumberByteArray.size,
                orderBy = null,
                filters = request.resultFilters
            )
        )
    }

    private fun cachedTupleNumber(write: WriteExt, tuple: Tuple): TupleNumber {
        tuples[write.i] = tuple
        tupleCache.store(tuple)
        return tuple.tupleNumber
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
        if (!collection.exists(conn)) throw NakshaException(
            COLLECTION_NOT_FOUND,
            "No such collection: $collectionId"
        )
        return collection
    }

    internal fun updateCollection(map: PgMap, write: WriteExt): Tuple {
        TODO("Implement me")
    }

    internal fun upsertCollection(map: PgMap, write: WriteExt): Tuple {
        TODO("Implement me")
    }
}