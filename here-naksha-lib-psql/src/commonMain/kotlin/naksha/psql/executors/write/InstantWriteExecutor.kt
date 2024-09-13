package naksha.psql.executors.write

import naksha.model.Naksha.NakshaCompanion.quoteIdent
import naksha.model.Tuple
import naksha.model.objects.NakshaFeature
import naksha.psql.PgCollection
import naksha.psql.PgColumn
import naksha.psql.PgSession
import naksha.psql.executors.write.WriteFeatureUtils.allColumnValues

class InstantWriteExecutor(
    val session: PgSession,
): WriteExecutor {

    override fun removeFeatureFromDel(collection: PgCollection, featureId: String) {
        collection.deleted?.let { delTable ->
            val quotedDelTable = quoteIdent(delTable.name)
            val quotedIdColumn = quoteIdent(PgColumn.id.name)
            session.usePgConnection()
                .execute(
                    sql = "DELETE FROM $quotedDelTable WHERE $quotedIdColumn=$1",
                    args = arrayOf(featureId)
                )
        }
    }

    override fun executeInsert(
        collection: PgCollection,
        tuple: Tuple,
        feature: NakshaFeature
    ) {
        val transaction = session.transaction()
        val conn = session.usePgConnection()
        val quotedCollectionId = quoteIdent(collection.id)
        conn.execute(
            sql = """ INSERT INTO $quotedCollectionId(${PgColumn.allWritableColumns.joinToString(",")})
                      VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,$14,$15,$16,$17,$18,$19,$20,$21,$22,$23)
                      """.trimIndent(),
            args = allColumnValues(tuple = tuple, feature = feature, txn = transaction.txn)
        )
    }

    override fun finish() {
        // nothing to do
    }
}