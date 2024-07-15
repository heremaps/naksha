package naksha.psql.write

import naksha.model.Guid
import naksha.model.Luid
import naksha.model.request.*
import naksha.model.request.Write.Companion.XYZ_OP_CREATE
import naksha.model.request.Write.Companion.XYZ_OP_DELETE
import naksha.model.request.Write.Companion.XYZ_OP_PURGE
import naksha.model.Txn
import naksha.psql.ERR_UNIQUE_VIOLATION
import naksha.psql.NakshaException
import naksha.psql.PgSession
import naksha.psql.PgStatic
import naksha.psql.*

internal class NakshaRequestOp(
    val reqWrite: Write,
    val dbRow: PsqlRow?,
    val atomicUUID: String?,
    val collectionId: String,
    val collectionPartitionCount: Int
) {
    val id: String = reqWrite.getId()
    val partition: Int = PgStatic.partitionNumber(id, collectionPartitionCount)

    // Used for sorting
    val key = "${PgStatic.PARTITION_ID[partition]}_${id}"

    companion object {
        private const val UNDETERMINED_PARTITION = -2
        private const val MULTIPLE_DIFFERENT_PARTITIONS = -1

        fun mapToOperations(
            collectionId: String,
            writeRequest: WriteRequest,
            session: PgSession,
            collectionPartitionCount: Int
        ): CollectionWriteOps {
            var partition: Int = UNDETERMINED_PARTITION
            val size = writeRequest.ops.size
            val operations = ArrayList<NakshaRequestOp>(size)
            val idsToModify = ArrayList<String>(size)
            val idsToPurge = ArrayList<String>()
            val idsToDel = ArrayList<String>()
            val uniqueIds = HashSet<String>(size)
            for (nakWriteOp in writeRequest.ops) {

                val id = nakWriteOp.getId()

                if (uniqueIds.contains(id)) {
                    throw NakshaException.forId(
                        ERR_UNIQUE_VIOLATION,
                        "Cannot perform multiple operations on single feature in one transaction",
                        id
                    )
                } else {
                    uniqueIds.add(id)
                }

                if (nakWriteOp.op != XYZ_OP_CREATE) {
                    idsToModify.add(id)
                    if (nakWriteOp.op == XYZ_OP_PURGE) {
                        idsToPurge.add(id)
                    } else if (nakWriteOp.op == XYZ_OP_DELETE) {
                        idsToDel.add(id)
                    }
                }
                val row = prepareRow(session, nakWriteOp)

                val op = NakshaRequestOp(
                    reqWrite = nakWriteOp,
                    dbRow = row,
                    collectionId = collectionId,
                    atomicUUID = requestedUUID(session.storage.id(), nakWriteOp),
                    collectionPartitionCount = collectionPartitionCount
                )
                operations.add(op)
                if (partition == UNDETERMINED_PARTITION) {
                    partition = op.partition
                } else if (partition != op.partition) {
                    partition = MULTIPLE_DIFFERENT_PARTITIONS
                }
            }

            return CollectionWriteOps(
                collectionId,
                operations.sortedBy { it.key },
                idsToModify,
                idsToPurge,
                idsToDel,
                if (partition >= 0) partition else null
            )
        }


        private fun prepareRow(session: PgSession, nakWriteOp: Write): PsqlRow? {
            return when (nakWriteOp) {
                is FeatureOp -> PsqlRow.fromRow(session.storage.featureToRow(nakWriteOp.feature.platformObject()))
                is RowOp -> PsqlRow.fromRow(nakWriteOp.row)
                else -> null
            }
        }

        private fun requestedUUID(storageId: String, writeOp: Write): String? {
            return when (writeOp) {
                is UpdateRow -> if (writeOp.atomic) {
                    val luid = Luid(Txn(writeOp.row.meta!!.txn), writeOp.row.meta!!.uid)
                    Guid(storageId, writeOp.collectionId, writeOp.getId(), luid).toString()
                } else null

                is DeleteFeature -> writeOp.uuid
                is PurgeFeature -> writeOp.uuid
                else -> null
            }
        }

    }
}
