package naksha.psql.executors.write

import naksha.model.Naksha.NakshaCompanion.quoteIdent
import naksha.model.Tuple
import naksha.model.objects.NakshaFeature
import naksha.psql.PgCollection
import naksha.psql.PgColumn
import naksha.psql.PgPlan
import naksha.psql.PgSession
import naksha.psql.executors.write.WriteFeatureUtils.allColumnValues

class BulkWriteExecutor(
    val session: PgSession,
): WriteExecutor {

    private var deleteFromDel: MutableMap<PgCollection, PgPlan> = mutableMapOf()
    private var insertToHead: MutableMap<PgCollection, PgPlan> = mutableMapOf()

    override fun removeFeatureFromDel(collection: PgCollection, featureId: String) {
        if (!deleteFromDel.containsKey(collection)) {
            collection.deleted?.let { delTable ->
                val quotedDelTable = quoteIdent(delTable.name)
                val quotedIdColumn = quoteIdent(PgColumn.id.name)
                val plan = session.usePgConnection().prepare("DELETE FROM $quotedDelTable WHERE $quotedIdColumn=$1", arrayOf(PgColumn.id.type.text))
                deleteFromDel[collection] = plan
            }
        }
        deleteFromDel[collection]!!.addBatch(arrayOf(featureId))
    }

    override fun executeInsert(
        collection: PgCollection,
        tuple: Tuple,
        feature: NakshaFeature
    ) {
        if (!insertToHead.containsKey(collection)) {
            val quotedCollectionId = quoteIdent(collection.id)
            val plan = session.usePgConnection().prepare(
                """INSERT INTO $quotedCollectionId(${PgColumn.allWritableColumns.joinToString(",")})
                      VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,$14,$15,$16,$17,$18,$19,$20,$21,$22,$23)
                      """.trimIndent(),
                PgColumn.allWritableColumns.map { it.type.text }.toTypedArray()
            )
            insertToHead[collection] = plan
        }
        insertToHead[collection]!!.addBatch(allColumnValues(tuple = tuple, feature = feature, txn = session.transaction().txn))
    }

    override fun finish() {
        deleteFromDel.forEach {
            it.value.executeBatch()
            it.value.close()
        }
        insertToHead.forEach {
            it.value.executeBatch()
            it.value.close()
        }
    }
}