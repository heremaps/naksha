package naksha.psql.executors

import naksha.base.Int64
import naksha.base.PlatformUtil
import naksha.jbon.JbDictionary
import naksha.model.*
import naksha.model.Naksha.NakshaCompanion.VIRT_COLLECTIONS
import naksha.model.Naksha.NakshaCompanion.VIRT_COLLECTIONS_QUOTED
import naksha.model.Naksha.NakshaCompanion.partitionNumber
import naksha.model.NakshaError.NakshaErrorCompanion.COLLECTION_NOT_FOUND
import naksha.model.NakshaError.NakshaErrorCompanion.ILLEGAL_ARGUMENT
import naksha.model.NakshaError.NakshaErrorCompanion.MAP_NOT_FOUND
import naksha.model.NakshaError.NakshaErrorCompanion.UNSUPPORTED_OPERATION
import naksha.model.objects.NakshaCollection
import naksha.model.objects.NakshaFeature
import naksha.model.request.*
import naksha.psql.*
import naksha.psql.executors.write.DeleteFeature
import naksha.psql.executors.write.InsertFeature
import naksha.psql.executors.write.UpdateFeature
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
    fun newCollectionTupleNumber(map: PgMap, collectionNumber: Int64): TupleNumber =
        TupleNumber(StoreNumber(map.number, collectionNumber, 0), version, newUid())

    /**
     * Creates a new tuple-number for an existing collection.
     * @param collection the collection for which to return a new tuple-number.
     * @return a new tuple-number.
     */
    fun newCollectionTupleNumber(collection: PgCollection): TupleNumber =
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
    private var resultSet: PgResultSet? = null

    fun execute(): Response {
        val tupleCache = this.tupleCache
        val tuples = this.tuples
        val tupleNumbers = this.tupleNumbers

        // First, process collections, no performance need here for now.
        for (write in orderedWrites) {
            if (write == null) continue
            val tupleNumber: TupleNumber = if (write.collectionId == VIRT_COLLECTIONS) {
                when (write.op) {
                    WriteOp.CREATE -> returnTuple(write, createCollection(mapOf(write), write))
                    WriteOp.UPSERT -> returnTuple(write, upsertCollection(mapOf(write), write))
                    WriteOp.UPDATE -> returnTuple(write, updateCollection(mapOf(write), write))
                    WriteOp.DELETE -> returnTuple(write, deleteCollection(mapOf(write), write))
                    WriteOp.PURGE -> returnTuple(write, purgeCollection(mapOf(write), write))
                    else -> throw NakshaException(
                        UNSUPPORTED_OPERATION,
                        "Unknown write-operation: '${write.op}'"
                    )
                }
            } else {
                when (write.op) {
                    WriteOp.CREATE -> InsertFeature(this).execute(collectionOf(write), write)
                    WriteOp.UPSERT -> returnTuple(write, upsertFeature(collectionOf(write), write))
                    WriteOp.UPDATE -> UpdateFeature(this).execute(collectionOf(write), write)
                    WriteOp.DELETE -> DeleteFeature(this).execute(collectionOf(write), write)
                    WriteOp.PURGE -> returnTuple(write, purgeFeature(collectionOf(write), write))
                    else -> throw NakshaException(
                        UNSUPPORTED_OPERATION,
                        "Unknown write-operation: '${write.op}'"
                    )
                }
            }
            tupleNumbers[write.i] = tupleNumber
        }

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

    fun returnTuple(write: WriteExt, tuple: Tuple): TupleNumber {
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

    private fun createCollection(map: PgMap, write: WriteExt): Tuple {
        // Note: write.collectionId is always naksha~collections!
        val feature = write.feature?.proxy(NakshaCollection::class) ?: throw NakshaException(
            ILLEGAL_ARGUMENT,
            "CREATE without feature"
        )
        val colId = write.featureId ?: PlatformUtil.randomString()
        val collectionNumber = newCollectionNumber(map)
        val tupleNumber = newCollectionTupleNumber(map, collectionNumber)
        val tuple = tuple(
            tupleNumber,
            feature,
            write.attachment,
            colId,
            storage.defaultFlags,
            map.encodingDict(colId, feature)
        )

        // insert row into naksha~collections before creating tables
        executeInsert(VIRT_COLLECTIONS_QUOTED, tuple, feature)

        // Create the tables
        val collection = map[colId]
        collection.create(
            conn,
            feature.partitions,
            PgStorageClass.of(feature.storageClass)
        )
        return tuple
    }

    /**
    Generates values matching [naksha.psql.PgColumn.allColumns] array
     */
    private fun allColumnValues(
        tuple: Tuple,
        feature: NakshaFeature,
        prevTxn: Int64? = null, // prev_txn is null for first version
        txn: Int64,
        nextTxn: Int64? = null, // prev_txn is null for newest version
        prevUid: Int? = null, // puid is null for first version
        changeCount: Int = 1 // change_count is '1' for the first version
    ): Array<Any?> {
        return arrayOf(
            tuple.tupleNumber.storeNumber,
            tuple.meta.updatedAt,
            tuple.meta.createdAt,
            tuple.meta.authorTs,
            nextTxn,
            txn,
            prevTxn,
            tuple.tupleNumber.uid,
            prevUid,
            changeCount,
            Metadata.hash(feature),
            Metadata.geoGrid(feature),
            tuple.meta.flags,
            feature.id,
            tuple.meta.appId,
            tuple.meta.author,
            tuple.meta.type,
            tuple.meta.origin,
            tuple.tags,
            tuple.referencePoint,
            tuple.geo,
            tuple.feature,
            tuple.attachment
        )
    }

    private fun tuple(
        tupleNumber: TupleNumber,
        feature: NakshaFeature,
        attachment: ByteArray?,
        featureId: String,
        flags: Flags,
        encodingDict: JbDictionary? = null
    ): Tuple {
        return Tuple(
            storage = storage,
            tupleNumber = tupleNumber,
            geo = PgUtil.encodeGeometry(feature.geometry, flags),
            referencePoint = PgUtil.encodeGeometry(feature.referencePoint, flags),
            feature = PgUtil.encodeFeature(feature, flags, encodingDict),
            tags = PgUtil.encodeTags(
                feature.properties.xyz.tags?.toTagMap(),
                storage.defaultFlags,
                encodingDict
            ),
            attachment = attachment,
            meta = Metadata(
                storeNumber = tupleNumber.storeNumber,
                version = tupleNumber.version,
                uid = tupleNumber.uid,
                updatedAt = updateTime(),
                author = session.options.author,
                appId = session.options.appId,
                flags = flags,
                id = featureId,
                type = NakshaCollection.FEATURE_TYPE
            )
        )
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

    internal fun executeInsert(
        quotedCollectionId: String,
        tuple: Tuple,
        feature: NakshaFeature
    ): Tuple {
        val transaction = session.transaction()
        conn.execute(
            sql = """ INSERT INTO $quotedCollectionId(${PgColumn.allWritableColumns.joinToString(",")})
                      VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,$14,$15,$16,$17,$18,$19,$20,$21,$22,$23)
                      """.trimIndent(),
            args = allColumnValues(tuple = tuple, feature = feature, txn = transaction.txn)
        ).close()
        return tuple
    }

    internal fun upsertFeature(collection: PgCollection, write: WriteExt): Tuple {
        TODO("Implement me")
    }

    internal fun purgeFeature(collection: PgCollection, write: WriteExt): Tuple {
        TODO("Implement me")
    }
}