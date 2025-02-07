package naksha.psql.executors.write

import naksha.model.*
import naksha.model.NakshaError.NakshaErrorCompanion.COLLECTION_NOT_FOUND
import naksha.model.NakshaError.NakshaErrorCompanion.ILLEGAL_ARGUMENT
import naksha.model.objects.NakshaCollection
import naksha.model.objects.NakshaFeature
import naksha.psql.*
import naksha.psql.executors.WriteExt
import naksha.psql.executors.write.WriteFeatureUtils.allColumnValues

class UpdateCollection(
    private val session: PgSession
) {

    fun execute(map: PgMap, write: WriteExt): Tuple? {
        // Note: write.collectionId is always naksha~collections!
        val feature = write.feature?.proxy(NakshaCollection::class) ?: throw NakshaException(
            NakshaError.ILLEGAL_ARGUMENT,
            "UPDATE without collection as feature"
        )
        val collectionId = write.featureId ?: throw NakshaException(ILLEGAL_ARGUMENT, "Collection has no id")
        val collection = session.storage.adminMap.getPgCollectionById(session.useConnection(), map, collectionId) ?: throw NakshaException(COLLECTION_NOT_FOUND, "Collection $collectionId not found")
        val tuple = session.updated(map.nakshaMap, collection.nakshaCollection, feature)

        // update the entry in naksha~collections
        //return updateVirtualCollection(tuple, feature)
        // TODO: We need to update the collection content!
        return tuple
    }

//    private fun updateVirtualCollection(
//        tuple: Tuple,
//        feature: NakshaFeature
//    ): Tuple? {
//        val transaction = session.transaction()
//        val conn = session.usePgConnection()
//        val statement = StringBuilder("""UPDATE $VIRT_COLLECTIONS_QUOTED SET """)
//        PgColumn.allWritableColumns.forEachIndexed {
//            index, column ->
//                statement.append(column).append(" = $").append(index+1)
//                if (index+1 < PgColumn.allWritableColumns.size) statement.append(",")
//                statement.append("\n")
//        }
//        statement.append("WHERE ${PgColumn.id} = $").append(PgColumn.allWritableColumns.size+1)
//        val cursor = conn.execute(
//            sql = statement.toString().trimIndent(),
//            args = allColumnValues(tuple = tuple, feature = feature, txn = transaction.txn).plus(feature.id)
//        )
//        val affectedRows = cursor.affectedRows()
//        cursor.close()
//        if (affectedRows == 0) return null
//        return tuple
//    }
}