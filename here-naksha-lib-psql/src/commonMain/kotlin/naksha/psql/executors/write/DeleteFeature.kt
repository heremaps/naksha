package naksha.psql.executors.write

import naksha.model.*
import naksha.model.Metadata.Metadata_C.hash
import naksha.model.objects.NakshaFeature
import naksha.model.request.ReadFeatures
import naksha.model.request.SuccessResponse
import naksha.model.request.Write
import naksha.psql.PgCollection
import naksha.psql.PgSession
import naksha.psql.PgUtil.PgUtilCompanion.quoteIdent
import naksha.psql.executors.PgReader
import naksha.psql.executors.write.WriteFeatureUtils.newFeatureTupleNumber
import naksha.psql.executors.write.WriteFeatureUtils.resolveFlags
import naksha.psql.executors.write.WriteFeatureUtils.tuple

class DeleteFeature(session: PgSession) : UpdateFeature(session) {
    override fun execute(collection: PgCollection, write: Write): Tuple {
        val featureId = write.featureId
        require(featureId != null) {"No feature ID provided"}

        var feature = write.feature
        if (feature == null) {
            val readFeatures = ReadFeatures(collection.id)
            readFeatures.featureIds.add(featureId)
            val response = PgReader(session, readFeatures).execute().proxy(SuccessResponse::class)
            if (!response.features.isEmpty())
            {
                feature = response.features.first()
            }
        }

        val previousMetadata = fetchCurrentMeta(collection, featureId)
        // If hst table enabled, copy head state into hst
        collection.history?.let { hstTable ->
            insertHeadVersionToHst(
                hstTable = hstTable,
                headTable = collection.head,
                versionInHead = previousMetadata.version
            )
        }

        val tupleNumber = newFeatureTupleNumber(collection, featureId, session)
        val flags = resolveFlags(collection, session).action(ACTION_DELETE)
        val tuple = tuple(
            session.storage,
            tupleNumber,
            feature,
            metadata(tupleNumber, feature, featureId, flags),
            write.attachment,
            flags
        )

        removeFeatureFromHead(collection, featureId)
        return tuple
    }


    private fun removeFeatureFromHead(collection: PgCollection, featureId: String) {
        collection.head.let { headTable ->
            val quotedHeadTable = quoteIdent(headTable.name)
            session.usePgConnection()
                .execute(
                    sql = "DELETE FROM $quotedHeadTable WHERE $quotedIdColumn=$1",
                    args = arrayOf(featureId)
                ).close()
        }
    }

    private fun metadata(
        tupleNumber: TupleNumber,
        feature: NakshaFeature?,
        featureId: String,
        flags: Flags,
    ): Metadata {
        val versionTime = session.versionTime()
        var hash = 0
        if (feature != null) hash = hash(feature, session.options.excludePaths, session.options.excludeFn)
        return Metadata(
            storeNumber = tupleNumber.storeNumber,
            version = tupleNumber.version,
            uid = tupleNumber.uid,
            hash = hash,
            createdAt = versionTime,
            updatedAt = versionTime,
            author = session.options.author,
            appId = session.options.appId,
            flags = flags,
            id = featureId,
            type = NakshaFeature.FEATURE_TYPE
        )
    }
}