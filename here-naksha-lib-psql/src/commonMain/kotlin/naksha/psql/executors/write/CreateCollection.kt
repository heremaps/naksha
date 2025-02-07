package naksha.psql.executors.write

import naksha.base.PlatformUtil
import naksha.model.*
import naksha.model.Naksha.NakshaCompanion.quoteIdent
import naksha.model.objects.NakshaCollection
import naksha.model.objects.NakshaFeature
import naksha.psql.*
import naksha.psql.executors.WriteExt
import naksha.psql.executors.write.WriteFeatureUtils.allColumnValues

class CreateCollection(
    private val session: PgSession
) {

    fun execute(map: PgMap, write: WriteExt): Tuple {
        // Note: write.collectionId is always naksha~collections!
        val collection = write.feature?.proxy(NakshaCollection::class) ?: throw NakshaException(
            NakshaError.ILLEGAL_ARGUMENT,
            "CREATE without feature"
        )
        val collectionId = write.featureId ?: PlatformUtil.randomString()
        val collectionNumber = newCollectionNumber(map)
        val tupleNumber = newCollectionTupleNumber(map, collectionNumber)
        val metadata = Metadata.forOperation(session, collection, tupleNumber, Operation.CREATED)
        val dictionary = session.storage.getEncodingDictionary(collection, null)
        val tuple = Tuple(
            meta = metadata,
            feature = Naksha.encodeFeature(collection, metadata.flags, dictionary),
            geo = Naksha.encodeGeometry(collection.geometry, metadata.flags),
            referencePoint = Naksha.encodeGeometry(collection.referencePoint, metadata.flags),
            tags = Naksha.encodeTags(collection.properties.xyz.tags.toTagMap(), metadata.flags, dictionary),
            attachment = write.attachment,
            complete = true
        )

        // insert row into naksha~collections before creating tables
        executeInsert(quoteIdent(collectionId), tuple, collection)

        // Create the tables
        session.storage.adminMap.createCollection(session.useConnection(), map, collection)
        return tuple
    }

    private fun executeInsert(
        quotedCollectionId: String,
        tuple: Tuple,
        feature: NakshaFeature
    ): Tuple {
        val transaction = session.useTransaction()
        val conn = session.useConnection()
        // TODO: Check if allColumns is really correct here !!!
        conn.execute(
            sql = """ INSERT INTO $quotedCollectionId(${PgColumn.allColumns.joinToString(",")})
                      VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,$14,$15,$16,$17,$18,$19,$20,$21,$22,$23)
                      """.trimIndent(),
            args = allColumnValues(tuple = tuple, feature = feature, txn = transaction.txn)
        ).close()
        return tuple
    }

    /**
     * Creates a new tuple-number for a new collection (to be created).
     * @param map the map in which the collection is stored.
     * @param collectionNumber the collection-number of the collection.
     * @return a new tuple-number.
     */
    private fun newCollectionTupleNumber(map: PgMap, collectionNumber: Int): TupleNumber =
        TupleNumber(session.storage.number, map.number, collectionNumber, 0, session.useTransaction().version, newUid())

    /**
     * Generate a new collection-number.
     * @param map the map in which to create a new map.
     * @return the new collection-number of the new collection.
     */
    fun newCollectionNumber(map: PgMap): Int = session.storage.adminMap.newCollectionNumber(session.useConnection())

    /**
     * Returns a new `uid` for a new tuple.
     */
    private fun newUid(): Int = session.uid.getAndAdd(1)
}