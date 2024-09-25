package naksha.psql.executors.write

import naksha.base.Int64
import naksha.jbon.JbDictionary
import naksha.model.*
import naksha.model.objects.NakshaFeature
import naksha.psql.PgCollection
import naksha.psql.PgSession
import naksha.psql.PgStorage
import naksha.psql.PgUtil

/**
 * Common utility functions used when writing features
 */
internal object WriteFeatureUtils {

    /**
     * Creates new tuple number within given transaction (obtained from [PgSession])
     */
    internal fun newFeatureTupleNumber(
        collection: PgCollection,
        featureId: String,
        session: PgSession
    ): TupleNumber = TupleNumber(
        StoreNumber(
            collection.map.number,
            collection.number,
            Naksha.partitionNumber(featureId)
        ),
        session.version(),
        session.uid.getAndAdd(1)
    )

    /**
    Generates values matching [naksha.psql.PgColumn.allColumns] array
     */
    internal fun allColumnValues(
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

    /**
     * Resolves default flags based on [PgCollection] and [PgSession] properties
     */
    internal fun resolveFlags(collection: PgCollection, session: PgSession): Flags =
        collection.nakshaCollection.defaultFlags ?: session.storage.defaultFlags

    internal fun tuple(
        storage: PgStorage,
        tupleNumber: TupleNumber,
        feature: NakshaFeature,
        metadata: Metadata,
        attachment: ByteArray?,
        flags: Flags,
        encodingDict: JbDictionary? = null
    ): Tuple {
        return Tuple(
            storage = storage,
            tupleNumber = tupleNumber,
            geo = PgUtil.encodeGeometry(feature?.geometry, flags),
            referencePoint = PgUtil.encodeGeometry(feature?.referencePoint, flags),
            feature = PgUtil.encodeFeature(feature, flags, encodingDict),
            tags = PgUtil.encodeTags(
                feature?.properties?.xyz?.tags?.toTagMap(),
                storage.defaultFlags,
                encodingDict
            ),
            attachment = attachment,
            meta = metadata
        )
    }
}