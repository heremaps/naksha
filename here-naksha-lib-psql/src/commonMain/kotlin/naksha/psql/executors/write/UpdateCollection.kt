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
            "Feature id not given"
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

        // Create the tables
        val collection = map[colId]
        collection.create(
            connection = session.usePgConnection(),
            partitions = feature.partitions,
            storageClass = PgStorageClass.of(feature.storageClass)
        )
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
        conn.execute(
            sql = """ INSERT INTO $VIRT_COLLECTIONS_QUOTED(${PgColumn.allWritableColumns.joinToString(",")})
                      VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,$14,$15,$16,$17,$18,$19,$20,$21,$22,$23)
                      """.trimIndent(),
            args = allColumnValues(tuple = tuple, feature = feature, txn = transaction.txn)
        ).close()
    }
}