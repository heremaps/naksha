package com.here.naksha.lib.plv8

import com.here.naksha.lib.base.DeleteFeature
import com.here.naksha.lib.base.FeatureOp
import com.here.naksha.lib.base.WriteOp
import com.here.naksha.lib.base.WriteRequest
import com.here.naksha.lib.base.PurgeFeature
import com.here.naksha.lib.base.RowOp
import com.here.naksha.lib.base.UpdateRow
import com.here.naksha.lib.base.UploadOp
import com.here.naksha.lib.base.XYZ_OP_CREATE
import com.here.naksha.lib.base.XYZ_OP_DELETE
import com.here.naksha.lib.base.XYZ_OP_PURGE
import com.here.naksha.lib.jbon.*

internal class NakshaRequestOp(
        val writeReq: WriteOp,
        val rowMap: IMap,
        val atomicUUID: String?,
        val collectionId: String,
        val collectionPartitionCount: Int
) {
    val id: String = rowMap.getAny(COL_ID) as String
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
        ): NakshaWriteOps {
            var partition: Int = UNDETERMINED_PARTITION
            val size = writeRequest.rows.size
            val operations = ArrayList<NakshaRequestOp>(size)
            val idsToModify = ArrayList<String>(size)
            val idsToPurge = ArrayList<String>()
            val idsToDel = ArrayList<String>()
            val uniqueIds = HashSet<String>(size)
            for (nakWriteOp in writeRequest.rows) {

                val id = nakWriteOp.getId()

                if (uniqueIds.contains(id)) {
                    throw NakshaException.forId(ERR_UNIQUE_VIOLATION, "Cannot perform multiple operations on single feature in one transaction", id)
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

                val row = prepareRow(session, nakWriteOp, collectionId)

                val op = NakshaRequestOp(nakWriteOp, row, collectionId = collectionId, atomicUUID = requestedUUID(nakWriteOp), collectionPartitionCount = collectionPartitionCount)
                operations.add(op)
                if (partition == UNDETERMINED_PARTITION) {
                    partition = op.partition
                } else if (partition != op.partition) {
                    partition = MULTIPLE_DIFFERENT_PARTITIONS
                }
            }

            return NakshaWriteOps(collectionId, operations.sortedBy { it.key }, idsToModify, idsToPurge, idsToDel, if (partition >= 0) partition else null)
        }


        private fun prepareRow(session: NakshaSession, nakWriteOp: WriteOp, collectionId: String): IMap {
            val row = newMap()
            row[COL_ID] = nakWriteOp.getId()
            when (nakWriteOp) {
                is FeatureOp -> {
                    row[COL_TAGS] = session.getTagsAsJbon(nakWriteOp.feature.getProperties().getXyz()?.getTags(), collectionId)
                    // TODO FIXME write geo as bytea
                    row[COL_GEOMETRY] = null // nakWriteOp.feature?.getCoordinates<BaseArray<Any?>>()
                    row[COL_FEATURE] = session.getFeatureAsJbon(nakWriteOp.feature.data(), nakWriteOp.getFlags(), collectionId)
                    row[COL_FLAGS] = nakWriteOp.getFlags().toCombinedFlags()
                }
                is RowOp -> {
                    row[COL_TAGS] = session.getFinalFeatureBytes(nakWriteOp.getFlags(), nakWriteOp.row.tags)
                    row[COL_GEOMETRY] = nakWriteOp.row.geo
                    row[COL_FEATURE] = session.getFinalFeatureBytes(nakWriteOp.getFlags(), nakWriteOp.row.feature)
                    row[COL_FLAGS] = nakWriteOp.getFlags().toCombinedFlags()
                }
            }
            if (nakWriteOp is UploadOp && nakWriteOp.getGrid() != null) {
                // we don't want it to be null, as null would override calculated value later in response
                row[COL_GEO_GRID] = nakWriteOp.getGrid()
            }
            return row
        }

        private fun requestedUUID(writeOp: WriteOp): String? {
            return when (writeOp) {
                is UpdateRow -> if (writeOp.atomic) writeOp.row.uuid!! else null
                is DeleteFeature -> writeOp.uuid
                is PurgeFeature -> writeOp.uuid
                else -> null
            }
        }
    }
}
