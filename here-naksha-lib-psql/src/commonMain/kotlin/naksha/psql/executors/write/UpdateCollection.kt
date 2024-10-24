package naksha.psql.executors.write

import naksha.model.*
import naksha.model.Naksha.NakshaCompanion.VIRT_COLLECTIONS_QUOTED
import naksha.model.objects.NakshaCollection
import naksha.model.objects.NakshaFeature
import naksha.psql.*
import naksha.psql.executors.WriteExt
import naksha.psql.executors.write.WriteCollectionUtils.tupleOfCollection
import naksha.psql.executors.write.WriteFeatureUtils.allColumnValues
import naksha.psql.executors.write.WriteFeatureUtils.newFeatureTupleNumber

class UpdateCollection(
    private val session: PgSession
) {

    fun execute(map: PgMap, write: WriteExt): Tuple {
        // Note: write.collectionId is always naksha~collections!
        val feature = write.feature?.proxy(NakshaCollection::class) ?: throw NakshaException(
            NakshaError.ILLEGAL_ARGUMENT,
            "UPDATE without collection as feature"
        )
        require(write.featureId != null) {
            "Collection id not given"
        }
        val colId = write.featureId!!
//        val cursor = readTupleInVirtualCollection(colId)
//        cursor.get<String>("")
        val tuple = tupleOfCollection(
            session = session,
            tupleNumber = newFeatureTupleNumber(
                map.collections(),
                colId,
                session
            ),
            feature = feature,
            attachment = write.attachment,
            featureId = colId,
            flags = session.storage.defaultFlags,
            encodingDict = map.encodingDict(colId, feature)
        )

        // update the entry in naksha~collections
        updateVirtualCollection(tuple, feature)
        return tuple
    }

//    private fun readTupleInVirtualCollection(collectionId: String): PgCursor {
//        val conn = session.usePgConnection()
//        val cursor = conn.execute(
//            sql = """ SELECT (${PgColumn.allColumns.joinToString(",")}) FROM $VIRT_COLLECTIONS_QUOTED
//                WHERE ${PgColumn.id} = '${collectionId}'
//                      """.trimIndent(),
//        )
//        conn.close()
//        return cursor
//    }

    private fun updateVirtualCollection(
        tuple: Tuple,
        feature: NakshaFeature
    ) {
        val transaction = session.transaction()
        val conn = session.usePgConnection()
        val statement = StringBuilder("""UPDATE $VIRT_COLLECTIONS_QUOTED SET """)
        PgColumn.allWritableColumns.forEachIndexed {
            index, column ->
                statement.append(column).append(" = $").append(index+1)
                if (index+1 < PgColumn.allWritableColumns.size) statement.append(",")
                statement.append("\n")
        }
        statement.append("WHERE ${PgColumn.id} = '${feature.id}'")
        conn.execute(
            sql = statement.toString().trimIndent(),
            args = allColumnValues(tuple = tuple, feature = feature, txn = transaction.txn)
        ).close()
    }
}