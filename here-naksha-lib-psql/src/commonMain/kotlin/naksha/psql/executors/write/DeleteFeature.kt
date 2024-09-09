package naksha.psql.executors.write

import naksha.model.Tuple
import naksha.model.Version
import naksha.model.request.Write
import naksha.psql.PgCollection
import naksha.psql.PgColumn
import naksha.psql.PgSession
import naksha.psql.PgTable
import naksha.psql.PgUtil.PgUtilCompanion.quoteIdent
import naksha.psql.executors.write.WriteFeatureUtils.newFeatureTupleNumber
import naksha.psql.executors.write.WriteFeatureUtils.resolveFlags
import naksha.psql.executors.write.WriteFeatureUtils.tuple

class DeleteFeature(
    val session: PgSession
) {
    fun execute(collection: PgCollection, write: Write): Tuple {
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

        collection.history?.let { hstTable ->
            insertPreviousVersionToHst(
                hstTable = hstTable,
                headTable = collection.head,
                nextVersion = newVersion
            )
        }
    }

    private fun insertPreviousVersionToHst(
        hstTable: PgTable,
        headTable: PgTable,
        nextVersion: Version
    ) {
        val hstTableName = quoteIdent(hstTable.name)
        val headTableName = quoteIdent(headTable.name)
        val columnsWithoutNext = PgColumn.allWritableColumns
            .filterNot { it == PgColumn.txn_next }
            .joinToString(separator = ",")
        session.usePgConnection().execute(
            sql = """
                INSERT INTO $hstTableName(${PgColumn.txn_next.name},$columnsWithoutNext)
                SELECT $1,$columnsWithoutNext FROM $headTableName
            """.trimIndent(),
            args = arrayOf(nextVersion.txn)
        ).close()
    }
}