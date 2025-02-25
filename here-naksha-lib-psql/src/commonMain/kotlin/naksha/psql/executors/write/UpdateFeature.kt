package naksha.psql.executors.write

import naksha.model.*
import naksha.model.Metadata.Metadata_C.calculateHereTile
import naksha.model.Metadata.Metadata_C.calculateHash
import naksha.model.Naksha.NakshaCompanion.featureNumber
import naksha.model.Naksha.NakshaCompanion.hashId
import naksha.model.NakshaError.NakshaErrorCompanion.MAP_NOT_FOUND
import naksha.model.objects.NakshaFeature
import naksha.psql.PgCollection
import naksha.psql.PgSession
import naksha.psql.executors.WriteExt
import naksha.psql.executors.write.WriteFeatureUtils.newFeatureTupleNumber
import naksha.psql.executors.write.WriteFeatureUtils.resolveFlags
import naksha.psql.executors.write.WriteFeatureUtils.tuple

class UpdateFeature(
    private val session: PgSession,
    private val existingMetadataProvider: ExistingMetadataProvider,
    private val writeExecutor: WriteExecutor
) {

    //TODO this implementation currently do not support atomic updates!
    // In other words, it ignores if version is set in the Write operation,
    // which requires that the current HEAD state is exactly in this version.
    fun execute(collection: PgCollection, write: WriteExt): Tuple {
        val feature = write.feature?.proxy(NakshaFeature::class) ?: throw NakshaException(
            NakshaError.ILLEGAL_ARGUMENT,
            "UPDATE without feature"
        )
        if (feature.id != write.id) throw NakshaException(NakshaError.ILLEGAL_ARGUMENT,"Feature id in payload (${feature.id}) and write request (${write.id}) are different")
        val previousMetadata = existingMetadataProvider.get(collection.head.name, write.id!!)
            ?: throw NakshaException(NakshaError.FEATURE_NOT_FOUND, "Trying update feature that not exists in head: ${write.id}")
        if (feature.id != previousMetadata.id) {
            throw NakshaException(NakshaError.ILLEGAL_ARGUMENT, "Feature id (${feature.id}) differs from previous metadata (${previousMetadata.id})")
        }
        if (previousMetadata.nextVersion != null) {
            throw NakshaException(NakshaError.ILLEGAL_ARGUMENT, "Previous metadata shouldn't have 'nextVersion' but it does (${previousMetadata.nextVersion})")
        }

        val map = session.storage.adminMap.getPgMapById(session.useConnection(), collection.map.id) ?: throw NakshaException(MAP_NOT_FOUND, "Map with id '${collection.map.id}' does not exist")
        val featureNumber = featureNumber(hashId(feature.id))
        val tupleNumber = newFeatureTupleNumber(collection, featureNumber, session)
        val tuple = session.useTx().updated(map.nakshaMap, collection.nakshaCollection, feature)

        writeExecutor.removeFeatureFromDel(collection, feature.id)
        collection.history?.let { hstTable ->
            writeExecutor.copyHeadToHst(
                collection = collection,
                featureId = feature.id
            )
        }
        writeExecutor.updateFeatureInHead(collection, tuple, feature, tupleNumber.version, previousMetadata)
        return tuple
    }
}