package naksha.psql.executors.write

import naksha.model.*
import naksha.model.Metadata.Metadata_C.hash
import naksha.model.objects.NakshaFeature
import naksha.model.request.ReadFeatures
import naksha.model.request.SuccessResponse
import naksha.model.request.Write
import naksha.psql.PgCollection
import naksha.psql.PgColumn
import naksha.psql.PgSession
import naksha.psql.PgTable
import naksha.psql.PgUtil.PgUtilCompanion.quoteIdent
import naksha.psql.executors.PgReader
import naksha.psql.executors.write.WriteFeatureUtils.newFeatureTupleNumber
import naksha.psql.executors.write.WriteFeatureUtils.resolveFlags
import naksha.psql.executors.write.WriteFeatureUtils.tuple

class DeleteFeature(session: PgSession) : UpdateFeature(session) {
    override fun execute(collection: PgCollection, write: Write): Tuple {
        val featureId = write.featureId ?: throw NakshaException(NakshaError.ILLEGAL_ARGUMENT, "No feature ID provided")

        val tuples = session.storage.getLatestTuples(
            conn = session.usePgConnection(),
            mapId = session.map,
            collectionId = collection.id,
            featureIds = arrayOf(featureId)
        )

        if (tuples.isEmpty()) {
            TODO("feature does not exist in head, should somehow return delete success but with a tuple")
        }

        val tuple = tuples.first()

        val tupleNumber = newFeatureTupleNumber(collection, featureId, session)
        val flags = resolveFlags(collection, session).action(Action.DELETED)

        // Only modify head, hst and del tables if feature exists
        if (feature != null) {
            // If hst table enabled
            collection.history?.let { hstTable ->
                // copy head state into hst with txn_next === txn
                insertHeadToTable(
                    destinationTable = hstTable,
                    headTable = collection.head,
                    featureId = featureId
                )
                // also copy head state into hst with txn_next === txn and action DELETED as a tombstone state
                insertHeadToTable(
                    destinationTable = hstTable,
                    headTable = collection.head,
                    tupleNumber = tupleNumber,
                    flags = flags,
                    featureId = featureId,
                )
            }

            // If del table enabled, copy head state into del, with action DELETED and txn_next === txn as a tombstone state
            collection.deleted?.let { delTable ->
                insertHeadToTable(
                    destinationTable = delTable,
                    headTable = collection.head,
                    tupleNumber = tupleNumber,
                    flags = flags,
                    featureId = featureId,
                )
            }

            removeFeatureFromHead(collection, featureId)
        }
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