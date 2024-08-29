package naksha.psql.executors.write

import naksha.model.*
import naksha.model.Metadata.Metadata_C.hash
import naksha.model.Naksha.NakshaCompanion.quoteIdent
import naksha.model.objects.NakshaFeature
import naksha.model.objects.Transaction
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

        removeFeatureFromDel(collection, feature.id)
        executeInsert(quoteIdent(collection.id), tuple, feature)
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

    private fun removeFeatureFromDel(collection: PgCollection, featureId: String) {
        collection.deleted?.let { delTable ->
            val quotedDelTable = quoteIdent(delTable.name)
            val quotedIdColumn = quoteIdent(PgColumn.id.name)
            session.usePgConnection()
                .execute(
                    sql = "DELETE FROM $quotedDelTable WHERE $quotedIdColumn='$featureId'"
                )
        }
    }

    private fun executeInsert(
        quotedCollectionId: String,
        tuple: Tuple,
        feature: NakshaFeature
    ): Tuple {
        val transaction = session.transaction()
        val conn = session.usePgConnection()
        conn.execute(
            sql = """ INSERT INTO $quotedCollectionId(${PgColumn.allWritableColumns.joinToString(",")})
                      VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,$14,$15,$16,$17,$18,$19,$20,$21,$22,$23)
                      """.trimIndent(),
            args = allColumnValues(tuple = tuple, feature = feature, txn = transaction.txn)
        )
        return tuple
    }
}