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
        val newVersion = Version(previousMetadata.version.txn + 1)
        // If hst table enabled
        collection.history?.let { hstTable ->
            // copy head state into hst with txn_next === txn
            insertHeadVersionToHst(
                hstTable = hstTable,
                headTable = collection.head,
                versionInHst = previousMetadata.version,
                featureId = featureId,
            )
            // also copy head state into hst with txn_next === txn and action DELETED as a tombstone state
            insertTombstoneVersionToTable(
                destinationTable = hstTable,
                headTable = collection.head,
                versionInDes = newVersion,
                flagsWithDeleted = flags,
                featureId = featureId,
            )
        }

        // If del table enabled, copy head state into del, with action DELETED and txn_next === txn as a tombstone state
        collection.deleted?.let { delTable ->
            insertTombstoneVersionToTable(
                destinationTable = delTable,
                headTable = collection.head,
                versionInDes = newVersion,
                flagsWithDeleted = flags,
                featureId = featureId,
            )
        }

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

    private fun insertTombstoneVersionToTable(
        destinationTable: PgTable,
        headTable: PgTable,
        versionInDes: Version,
        flagsWithDeleted: Flags,
        featureId: String
    ) {
        val headTableName = quoteIdent(headTable.name)
        val desTableName = quoteIdent(destinationTable.name)
        val columnsWithoutNextFlags = PgColumn.allWritableColumns
            .filterNot { it == PgColumn.txn_next }
            .filterNot { it == PgColumn.txn }
            .filterNot { it == PgColumn.flags }
            .joinToString(separator = ",")
        session.usePgConnection().execute(
            sql = """
                INSERT INTO $desTableName(${PgColumn.txn_next.name},${PgColumn.txn.name},${PgColumn.flags.name},$columnsWithoutNextFlags)
                SELECT $1,$2,$3,$columnsWithoutNextFlags FROM $headTableName
                WHERE $quotedIdColumn='$featureId'
            """.trimIndent(),
            args = arrayOf(versionInDes.txn, versionInDes.txn, flagsWithDeleted)
        ).close()
    }
}