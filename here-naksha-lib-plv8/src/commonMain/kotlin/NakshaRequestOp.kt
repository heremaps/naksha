package com.here.naksha.lib.plv8

import com.here.naksha.lib.base.Base
import com.here.naksha.lib.base.NakWriteFeatures
import com.here.naksha.lib.base.NakWriteRequest
import com.here.naksha.lib.base.NakWriteRow
import com.here.naksha.lib.base.iterator
import com.here.naksha.lib.base.size
import com.here.naksha.lib.jbon.*
import com.here.naksha.lib.plv8.Static.DEBUG

internal class NakshaRequestOp(
        val writeRow: NakWriteRow,
        val rowMap: IMap,
        val collectionId: String,
        val collectionPartitionCount: Int
) {
    val id: String = rowMap.getAny(COL_ID) as String
    val partition: Int = Static.partitionNumber(id, collectionPartitionCount)

    // Used for sorting
    val key = "${Static.PARTITION_ID[partition]}_${id}"

    companion object {
        fun mapToOperations(
                collectionId: String,
                writeRequest: NakWriteRequest,
                sql: IPlv8Sql,
                collectionPartitionCount: Int
        ): NakshaWriteOps {
            var partition: Int = -2
            val size = writeRequest.getRows().size()
            val operations = ArrayList<NakshaRequestOp>(size)
            val idsToModify = ArrayList<String>(size)
            val idsToPurge = ArrayList<String>()
            val idsToDel = ArrayList<String>()
            val uniqueIds = HashSet<String>(size)
            for (rowOb in writeRequest.getRows()) {
                val nakWriteOp = Base.assign(rowOb, NakWriteRow.klass)

                val id = nakWriteOp.getId()
                        ?: nakWriteOp.getFeature()?.getId()
                        ?: throw NakshaException.forId(ERR_FEATURE_NOT_EXISTS, "Missing id", null)

                if (uniqueIds.contains(id)) {
                    throw NakshaException.forId(ERR_UNIQUE_VIOLATION, "Cannot perform multiple operations on single feature in one transaction", id)
                } else {
                    uniqueIds.add(id)
                }

                if (nakWriteOp.getOp() != XYZ_OP_CREATE) {
                    idsToModify.add(id)
                    if (nakWriteOp.getOp() == XYZ_OP_PURGE) {
                        idsToPurge.add(id)
                    } else if (nakWriteOp.getOp() == XYZ_OP_DELETE) {
                        idsToDel.add(id)
                    }
                }
                val row = newMap()
                row[COL_ID] = id
                row[COL_TAGS] = nakWriteOp.getRow()?.getTags()?.getByteArray()
                row[COL_GEOMETRY] = nakWriteOp.getRow()?.getGeo()?.getByteArray()
                val flags = nakWriteOp.getFlagsObject()
                val featureBytes = nakWriteOp.getRow()?.getFeature()?.getByteArray()
                row[COL_FEATURE] = if (sql.info().gzipSupported && featureBytes != null) {
                    flags.forceGzipOnFeatureEncoding()
                    sql.gzipCompress(featureBytes)
                } else {
                    flags.turnOffGzipOnFeatureEncoding()
                    featureBytes
                }
                row[COL_FLAGS] = flags.toCombinedFlags()
                if (nakWriteOp.getGrid() != null) {
                    // we don't want it to be null, as null would override calculated value later in response
                    row[COL_GEO_GRID] = nakWriteOp.getGrid()
                }

                val op = NakshaRequestOp(nakWriteOp, row, collectionId = collectionId, collectionPartitionCount)
                operations.add(op)
                if (partition == -2) {
                    partition = op.partition
                } else if (partition != op.partition) {
                    partition = -1
                }
            }

            return NakshaWriteOps(collectionId, operations.sortedBy { it.key }, idsToModify, idsToPurge, idsToDel, if (partition >= 0) partition else null)
        }
    }
}
