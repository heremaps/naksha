package naksha.psql.executors.write

import naksha.model.*
import naksha.model.Naksha.NakshaCompanion.featureNumber
import naksha.model.Naksha.NakshaCompanion.hashId
import naksha.model.objects.NakshaFeature
import naksha.psql.PgCollection
import naksha.psql.PgSession
import naksha.psql.executors.WriteExt
import naksha.psql.executors.write.WriteFeatureUtils.newFeatureTupleNumber
import naksha.psql.executors.write.WriteFeatureUtils.resolveFlags
import naksha.psql.executors.write.WriteFeatureUtils.tuple

class InsertFeature(
    private val session: PgSession,
    private val writeExecutor: WriteExecutor
) {

    //TODO: consider changing type to some result
    fun execute(collection: PgCollection, write: WriteExt): Tuple {
        val feature = write.feature?.proxy(NakshaFeature::class) ?: throw NakshaException(
            NakshaError.ILLEGAL_ARGUMENT,
            "CREATE without feature"
        )
        require(feature.id == write.id) {
            "Feature id in payload (${feature.id}) and write request (${write.id}) are different"
        }
        val featureNumber = featureNumber(hashId(feature.id))
        val tupleNumber = newFeatureTupleNumber(collection, featureNumber, session)
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
        val versionTime = session.useTransaction().time
        return Metadata.forOperation(session, feature, tupleNumber, Operation.CREATED)
    }
}