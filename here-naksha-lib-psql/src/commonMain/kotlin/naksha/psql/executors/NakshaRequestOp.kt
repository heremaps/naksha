//package naksha.psql.executors
//
//import naksha.model.Guid
//import naksha.model.RowId
//import naksha.model.request.Write.Companion.XYZ_OP_CREATE
//import naksha.model.request.Write.Companion.XYZ_OP_DELETE
//import naksha.model.request.Write.Companion.XYZ_OP_PURGE
//import naksha.model.Version
//import naksha.model.request.Write
//import naksha.model.request.WriteRequest
//import naksha.model.request.op.*
//import naksha.psql.ERR_UNIQUE_VIOLATION
//import naksha.psql.NakshaException
//import naksha.psql.PgSession
//import naksha.psql.*
//import naksha.psql.PgUtil.PgUtilCompanion.partitionNumber
//import naksha.psql.PgUtil.PgUtilCompanion.partitionPosix
//
//internal class NakshaRequestOp(
//    val write: Write,
//    val dbRow: PgRow?,
//    val atomicUUID: String?,
//    val collectionId: String,
//    val collectionPartitionCount: Int
//) {
//    val id: String = write.getId()
//    val partition: Int = partitionNumber(id) % collectionPartitionCount
//
//    // Used for sorting
//    val key = "${partitionPosix(partition)}_${id}"
//
//    companion object {
//        private const val UNDETERMINED_PARTITION = -2
//        private const val MULTIPLE_DIFFERENT_PARTITIONS = -1
//
//        fun mapToOperations(
//            collectionId: String,
//            writeRequest: WriteRequest,
//            session: PgSession,
//            collectionPartitionCount: Int
//        ): CollectionWriteOps {
//            var partition: Int = UNDETERMINED_PARTITION
//            val size = writeRequest.writes.size
//            val operations = ArrayList<NakshaRequestOp>(size)
//            val idsToModify = ArrayList<String>(size)
//            val idsToPurge = ArrayList<String>()
//            val idsToDel = ArrayList<String>()
//            val uniqueIds = HashSet<String>(size)
//            for (nakWriteOp in writeRequest.writes) {
//
//                val id = nakWriteOp.getId()
//
//                if (uniqueIds.contains(id)) {
//                    throw NakshaException.forId(
//                        ERR_UNIQUE_VIOLATION,
//                        "Cannot perform multiple operations on single feature in one transaction",
//                        id
//                    )
//                } else {
//                    uniqueIds.add(id)
//                }
//
//                if (nakWriteOp.op != XYZ_OP_CREATE) {
//                    idsToModify.add(id)
//                    if (nakWriteOp.op == XYZ_OP_PURGE) {
//                        idsToPurge.add(id)
//                    } else if (nakWriteOp.op == XYZ_OP_DELETE) {
//                        idsToDel.add(id)
//                    }
//                }
//                val row = prepareRow(session, nakWriteOp)
//
//                val op = NakshaRequestOp(
//                    write = nakWriteOp,
//                    dbRow = row,
//                    collectionId = collectionId,
//                    atomicUUID = requestedUUID(session.storage.id(), nakWriteOp),
//                    collectionPartitionCount = collectionPartitionCount
//                )
//                operations.add(op)
//                if (partition == UNDETERMINED_PARTITION) {
//                    partition = op.partition
//                } else if (partition != op.partition) {
//                    partition = MULTIPLE_DIFFERENT_PARTITIONS
//                }
//            }
//
//            return CollectionWriteOps(
//                collectionId,
//                operations.sortedBy { it.key },
//                idsToModify,
//                idsToPurge,
//                idsToDel,
//                if (partition >= 0) partition else null
//            )
//        }
//
//
//        private fun prepareRow(session: PgSession, nakWriteOp: Write): PgRow? {
//            return when (nakWriteOp) {
//                is WriteFeature -> DbRowMapper.rowToPgRow(session.storage.featureToRow(nakWriteOp.feature.platformObject()))
//                is WriteRow -> DbRowMapper.rowToPgRow(nakWriteOp.row)
//                else -> null
//            }
//        }
//
//        private fun requestedUUID(storageId: String, writeOp: Write): String? {
//            return when (writeOp) {
//                is UpdateRow -> if (writeOp.atomic) {
//                    val rowId = RowId(Version(writeOp.row.meta!!.version), writeOp.row.meta!!.uid)
//                    Guid(storageId, writeOp.collectionId, writeOp.getId(), rowId).toString()
//                } else null
//
//                is DeleteFeature -> writeOp.guid.toString()
//                is PurgeFeature -> writeOp.guid.toString()
//                else -> null
//            }
//        }
//
//    }
//}
