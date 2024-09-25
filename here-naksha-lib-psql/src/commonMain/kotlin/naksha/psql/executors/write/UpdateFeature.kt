package naksha.psql.executors.write

import naksha.model.*
import naksha.model.Metadata.Metadata_C.geoGrid
import naksha.model.Metadata.Metadata_C.hash
import naksha.model.objects.NakshaFeature
import naksha.psql.PgCollection
import naksha.psql.PgSession
import naksha.psql.executors.PgWriter
import naksha.psql.executors.WriteExt
import naksha.psql.executors.write.WriteFeatureUtils.newFeatureTupleNumber
import naksha.psql.executors.write.WriteFeatureUtils.resolveFlags
import naksha.psql.executors.write.WriteFeatureUtils.tuple
import kotlin.jvm.JvmField

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
        require(feature.id == write.featureId) {
            "Feature id in payload (${feature.id}) and write request (${write.featureId}) are different"
        }
        val previousMetadata = existingMetadataProvider.get(collection.head.name, write.id!!)
        require(previousMetadata != null) {
            "Trying update feature that not exists in head: ${write.id}"
        }
        require(feature.id == previousMetadata.id) {
            "Feature id (${feature.id}) differs from previous metadata (${previousMetadata.id})"
        }
        require(previousMetadata.nextVersion == null) {
            "Previous metadata shouldn't have 'nextVersion' but it does (${previousMetadata.nextVersion})"
        }

        val tupleNumber = newFeatureTupleNumber(collection, feature.id, session)
        val flags = resolveFlags(collection, session)
        val newVersion = Version(previousMetadata.version.txn + 1)
        val tuple = tuple(
            session.storage,
            tupleNumber,
            feature,
            metadataForNewVersion(previousMetadata, previousMetadata.version, feature, flags),
            write.attachment,
            flags
        )

        writeExecutor.removeFeatureFromDel(collection, feature.id)
        collection.history?.let { hstTable ->
            writeExecutor.copyHeadToHst(
                collection = collection,
                featureId = feature.id
            )
        }
        writeExecutor.updateFeatureInHead(collection, tuple, feature, newVersion, previousMetadata)
        return tuple
    }


    private fun metadataForNewVersion(
        previousMetadata: Metadata,
        newVersion: Version,
        feature: NakshaFeature,
        flags: Flags
    ): Metadata {
        val versionTime = session.versionTime()
        return previousMetadata.copy(
            updatedAt = versionTime,
            authorTs = if (session.options.author == null) previousMetadata.authorTs else versionTime,
            version = newVersion,
            prevVersion = previousMetadata.version,
            uid = session.uid.getAndAdd(1),
            puid = previousMetadata.puid,
            hash = hash(feature, session.options.excludePaths, session.options.excludeFn),
            changeCount = previousMetadata.changeCount + 1,
            geoGrid = geoGrid(feature),
            flags = flags.action(Action.UPDATED),
            appId = session.options.appId,
            author = session.options.author ?: previousMetadata.author,
            id = feature.id
        )
    }


}