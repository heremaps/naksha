package naksha.psql.executors.write

import naksha.base.Int64
import naksha.base.PlatformUtil
import naksha.jbon.JbDictionary
import naksha.model.*
import naksha.model.Naksha.NakshaCompanion.VIRT_COLLECTIONS_QUOTED
import naksha.model.objects.NakshaCollection
import naksha.model.objects.NakshaFeature
import naksha.model.objects.StoreMode
import naksha.psql.*
import naksha.psql.executors.WriteExt
import naksha.psql.executors.write.WriteCollectionUtils.tupleOfCollection
import naksha.psql.executors.write.WriteFeatureUtils.allColumnValues

class CreateCollection(
    private val session: PgSession
) {

    fun execute(map: PgMap, write: WriteExt): Tuple {
        // Note: write.collectionId is always naksha~collections!
        val feature = write.feature?.proxy(NakshaCollection::class) ?: throw NakshaException(
            NakshaError.ILLEGAL_ARGUMENT,
            "CREATE without feature"
        )
        val colId = write.featureId ?: PlatformUtil.randomString()
        val collectionNumber = newCollectionNumber(map)
        val tupleNumber = newCollectionTupleNumber(map, collectionNumber)
        val tuple = tupleOfCollection(
            session = session,
            tupleNumber = tupleNumber,
            feature = feature,
            attachment = write.attachment,
            featureId = colId,
            flags = session.storage.defaultFlags,
            encodingDict = map.encodingDict(colId, feature)
        )

        // insert row into naksha~collections before creating tables
        executeInsert(VIRT_COLLECTIONS_QUOTED, tuple, feature)

        // Create the tables
        val collection = map[colId]
        collection.create(
            connection = session.usePgConnection(),
            partitions = feature.partitions,
            storageClass = PgStorageClass.of(feature.storageClass),
            indices = PgIndex.DEFAULT_INDICES,
            storeHistory = feature.storeHistory != StoreMode.OFF,
            storedDeleted = feature.storeDeleted != StoreMode.OFF,
            storeMeta = feature.storeMeta != StoreMode.OFF
        )
        return tuple
    }

    private fun executeInsert(
        quotedCollectionId: String,
        tuple: Tuple,
        feature: NakshaFeature
    ) {
        val transaction = session.transaction()
        val conn = session.usePgConnection()
        conn.execute(
            sql = """ INSERT INTO $quotedCollectionId(${PgColumn.allWritableColumns.joinToString(",")})
                      VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,$14,$15,$16,$17,$18,$19,$20,$21,$22,$23)
                      """.trimIndent(),
            args = allColumnValues(tuple = tuple, feature = feature, txn = transaction.txn)
        ).close()
    }

    /**
     * Creates a new tuple-number for a new collection (to be created).
     * @param map the map in which the collection is stored.
     * @param collectionNumber the collection-number of the collection.
     * @return a new tuple-number.
     */
    private fun newCollectionTupleNumber(map: PgMap, collectionNumber: Int64): TupleNumber =
        TupleNumber(StoreNumber(map.number, collectionNumber, 0), session.version(), newUid())

    /**
     * Generate a new collection-number.
     * @param map the map in which to create a new map.
     * @return the new collection-number of the new collection.
     */
    fun newCollectionNumber(map: PgMap): Int64 = map.newCollectionNumber(session.usePgConnection())

    /**
     * Returns a new `uid` for a new tuple.
     */
    private fun newUid(): Int = session.uid.getAndAdd(1)
}