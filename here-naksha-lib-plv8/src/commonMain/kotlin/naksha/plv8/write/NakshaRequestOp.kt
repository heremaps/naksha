package naksha.plv8.write

import com.here.naksha.lib.jbon.XYZ_OP_CREATE
import com.here.naksha.lib.jbon.XYZ_OP_DELETE
import com.here.naksha.lib.jbon.XYZ_OP_PURGE
import naksha.model.Guid
import naksha.model.request.*
import naksha.model.response.Row
import naksha.plv8.ERR_UNIQUE_VIOLATION
import naksha.plv8.NakshaException
import naksha.plv8.NakshaSession
import naksha.plv8.Static

internal class NakshaRequestOp(
    val reqWrite: Write,
    val dbRow: Row?,
    val atomicUUID: String?,
    val collectionId: String,
    val collectionPartitionCount: Int
) {
    val id: String = reqWrite.getId()
    val partition: Int = Static.partitionNumber(id, collectionPartitionCount)

    // Used for sorting
    val key = "${Static.PARTITION_ID[partition]}_${id}"

    companion object {
        private const val UNDETERMINED_PARTITION = -2
        private const val MULTIPLE_DIFFERENT_PARTITIONS = -1

        fun mapToOperations(
            collectionId: String,
            writeRequest: WriteRequest,
            session: NakshaSession,
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
                    nakWriteOp,
                    row,
                    collectionId = collectionId,
                    atomicUUID = requestedUUID(
                        session.storage.id(),
                        nakWriteOp
                    ),
                    collectionPartitionCount = collectionPartitionCount
                )
                operations.add(op)
                if (partition == UNDETERMINED_PARTITION) {
                    partition = op.partition
                } else if (partition != op.partition) {
                    partition =
                        MULTIPLE_DIFFERENT_PARTITIONS
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


        private fun prepareRow(session: NakshaSession, nakWriteOp: Write): Row? {
            return when (nakWriteOp) {
                is FeatureOp -> session.storage.convertFeatureToRow(nakWriteOp.feature)

                is RowOp -> nakWriteOp.row
                else -> null
            }
        }

        private fun requestedUUID(storageId: String, writeOp: Write): String? {
            return when (writeOp) {
                is UpdateRow -> if (writeOp.atomic) {
                    val luid = writeOp.row.meta!!.getLuid()
                    Guid(storageId, writeOp.collectionId, writeOp.getId(), luid).toString()
                } else null

                is DeleteFeature -> writeOp.uuid
                is PurgeFeature -> writeOp.uuid
                else -> null
            }
        }

    }
}