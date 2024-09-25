package naksha.psql.executors.write

import naksha.model.*
import naksha.model.request.ReadFeatures
import naksha.model.request.SuccessResponse
import naksha.psql.PgCollection
import naksha.psql.PgSession
import naksha.psql.executors.PgReader
import naksha.psql.executors.PgWriter
import naksha.psql.executors.WriteExt
import naksha.psql.executors.write.WriteFeatureUtils.newFeatureTupleNumber
import naksha.psql.executors.write.WriteFeatureUtils.resolveFlags

class DeleteFeature(
    private val session: PgSession,
    private val writeExecutor: WriteExecutor
) {
    fun execute(collection: PgCollection, write: WriteExt): TupleNumber {
        val featureId = write.featureId ?: throw NakshaException(NakshaError.ILLEGAL_ARGUMENT, "No feature ID provided")

        val tupleNumber = newFeatureTupleNumber(collection, featureId, session)
        val flags = resolveFlags(collection, session).action(Action.DELETED)

        val readFeatures = ReadFeatures(collection.id)
        readFeatures.featureIds.add(featureId)
        val response = PgReader(session, readFeatures).execute().proxy(SuccessResponse::class)

        // Only modify head, hst and del tables if feature exists
        if (response.features.isNotEmpty()) {
            // If hst table enabled
            collection.history?.let { hstTable ->
                // copy head state into hst with txn_next === txn
                writeExecutor.copyHeadToHst(collection = collection, featureId = featureId)
                // also copy head state into hst with txn_next === txn and action DELETED as a tombstone state
                writeExecutor.copyHeadToHst(
                    collection = collection,
                    tupleNumber = tupleNumber,
                    flags = flags,
                    featureId = featureId,
                )
            }

            // If del table enabled, copy head state into del, with action DELETED and txn_next === txn as a tombstone state
            collection.deleted?.let { delTable ->
                writeExecutor.copyHeadToDel(
                    collection = collection,
                    tupleNumber = tupleNumber,
                    flags = flags,
                    featureId = featureId,
                )
            }

            writeExecutor.removeFeatureFromHead(collection, featureId)
        }
        return tupleNumber
    }
}