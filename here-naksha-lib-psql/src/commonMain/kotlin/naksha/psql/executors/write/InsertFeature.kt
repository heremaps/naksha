package naksha.psql.executors.write

import naksha.model.*
import naksha.model.Metadata.Metadata_C.hash
import naksha.model.Naksha.NakshaCompanion.quoteIdent
import naksha.model.objects.NakshaFeature
import naksha.model.request.Write
import naksha.psql.PgCollection
import naksha.psql.PgColumn
import naksha.psql.PgSession
import naksha.psql.executors.write.WriteFeatureUtils.allColumnValues
import naksha.psql.executors.write.WriteFeatureUtils.newFeatureTupleNumber
import naksha.psql.executors.write.WriteFeatureUtils.resolveFlags
import naksha.psql.executors.write.WriteFeatureUtils.tuple

class InsertFeature(
    val session: PgSession,
    val writeExecutor: WriteExecutor
) {

    //TODO: consider changing type to some result
    fun execute(collection: PgCollection, write: Write): Tuple {
        val feature = write.feature?.proxy(NakshaFeature::class) ?: throw NakshaException(
            NakshaError.ILLEGAL_ARGUMENT,
            "CREATE without feature"
        )
        require(feature.id == write.featureId) {
            "Feature id in payload (${feature.id}) and write request (${write.featureId}) are different"
        }

        val tupleNumber = newFeatureTupleNumber(collection, feature.id, session)
        val flags = resolveFlags(collection, session)
        val tuple = tuple(
            session.storage,
            tupleNumber,
            feature,
            metadata(tupleNumber, feature, flags),
            write.attachment,
            flags
        )

        writeExecutor.removeFeatureFromDel(collection, feature.id)
        writeExecutor.executeInsert(collection, tuple, feature)
        return tuple
    }

    private fun metadata(
        tupleNumber: TupleNumber,
        feature: NakshaFeature,
        flags: Flags,
    ): Metadata {
        val versionTime = session.versionTime()
        return Metadata(
            storeNumber = tupleNumber.storeNumber,
            version = tupleNumber.version,
            uid = tupleNumber.uid,
            hash = hash(feature, session.options.excludePaths, session.options.excludeFn),
            createdAt = versionTime,
            updatedAt = versionTime,
            author = session.options.author,
            appId = session.options.appId,
            flags = flags,
            id = feature.id,
            type = NakshaFeature.FEATURE_TYPE
        )
    }
}