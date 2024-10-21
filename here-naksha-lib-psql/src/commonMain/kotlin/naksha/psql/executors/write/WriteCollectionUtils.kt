package naksha.psql.executors.write

import naksha.jbon.JbDictionary
import naksha.model.*
import naksha.model.objects.NakshaCollection
import naksha.model.objects.NakshaFeature
import naksha.psql.PgSession
import naksha.psql.PgUtil

internal object WriteCollectionUtils {
    internal fun tupleOfCollection(
        session: PgSession,
        tupleNumber: TupleNumber,
        feature: NakshaFeature,
        attachment: ByteArray?,
        featureId: String,
        flags: Flags,
        encodingDict: JbDictionary? = null
    ): Tuple {
        return Tuple(
            storage = session.storage,
            tupleNumber = tupleNumber,
            fetchBits = FetchMode.FETCH_ALL,
            geo = PgUtil.encodeGeometry(feature.geometry, flags),
            referencePoint = PgUtil.encodeGeometry(feature.referencePoint, flags),
            feature = PgUtil.encodeFeature(feature, flags, encodingDict),
            tags = PgUtil.encodeTags(
                feature.properties.xyz.tags?.toTagMap(),
                session.storage.defaultFlags,
                encodingDict
            ),
            attachment = attachment,
            meta = Metadata(
                storeNumber = tupleNumber.storeNumber,
                version = tupleNumber.version,
                uid = tupleNumber.uid,
                updatedAt = session.versionTime(),
                author = session.options.author,
                appId = session.options.appId,
                flags = flags,
                id = featureId,
                type = NakshaCollection.FEATURE_TYPE
            )
        )
    }
}