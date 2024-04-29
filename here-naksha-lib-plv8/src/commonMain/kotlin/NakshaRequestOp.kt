package com.here.naksha.lib.plv8

import com.here.naksha.lib.jbon.*
import com.here.naksha.lib.nak.Flags
import com.here.naksha.lib.plv8.Static.DEBUG

internal class NakshaRequestOp(
        val rawFeature: ByteArray?,
        val rowMap: IMap,
        val xyzOp: XyzOp,
        val collectionId: String
) {
    val id: String = rowMap.getAny(COL_ID) as String
    val partition: Int = Static.partitionNumber(id)

    // Used for sorting
    val key = "${Static.PARTITION_ID[partition]}_${id}"

    companion object {
        fun mapToOperations(
                collectionId: String,
                op_arr: Array<ByteArray>,
                feature_arr: Array<ByteArray?>,
                flags_arr: Array<Int?>,
                geo_arr: Array<ByteArray?>,
                tags_arr: Array<ByteArray?>,
                sql: IPlv8Sql
        ): NakshaWriteOps {
            check(op_arr.size == feature_arr.size && op_arr.size == flags_arr.size && op_arr.size == geo_arr.size && op_arr.size == tags_arr.size) {
                "not all input arrays has same size"
            }
            var partition: Int = -2
            val featureReader = JbFeature(JbDictManager())
            val operations = ArrayList<NakshaRequestOp>(op_arr.size)
            val idsToModify = ArrayList<String>(op_arr.size)
            val idsToPurge = ArrayList<String>()
            val idsToDel = ArrayList<String>()
            val uniqueIds = HashSet<String>(op_arr.size)
            var total = 0
            val opReader = XyzOp()
            for (i in op_arr.indices) {
                if (DEBUG) {
                    val START = Jb.env.currentMicros()
                    opReader.mapBytes(op_arr[i])
                    val END = Jb.env.currentMicros()
                    total += (END - START).toInt()
                } else {
                    opReader.mapBytes(op_arr[i])
                }

                val id = if (opReader.id() == null) {
                    featureReader.mapBytes(feature_arr[i])
                    featureReader.id() ?: throw NakshaException.forId(ERR_FEATURE_NOT_EXISTS, "Missing id", null)
                } else {
                    opReader.id()!!
                }
                if (uniqueIds.contains(id)) {
                    throw NakshaException.forId(ERR_UNIQUE_VIOLATION, "Cannot perform multiple operations on single feature in one transaction", id)
                } else {
                    uniqueIds.add(id)
                }

                if (opReader.op() != XYZ_OP_CREATE) {
                    idsToModify.add(id)
                    if (opReader.op() == XYZ_OP_PURGE) {
                        idsToPurge.add(id)
                    } else if (opReader.op() == XYZ_OP_DELETE) {
                        idsToDel.add(id)
                    }
                }
                val row = newMap()
                row[COL_ID] = id
                row[COL_TAGS] = tags_arr[i]
                row[COL_GEOMETRY] = geo_arr[i]
                val flags = Flags(flags_arr[i])
                row[COL_FEATURE] = if (sql.info().gzipSupported && feature_arr[i] != null) {
                    flags.forceGzipOnFeatureEncoding()
                    sql.gzipCompress(feature_arr[i]!!)
                } else {
                    flags.turnOffGzipOnFeatureEncoding()
                    feature_arr[i]
                }
                row[COL_FLAGS] = flags.toCombinedFlags()
                if (opReader.grid() != null) {
                    // we don't want it to be null, as null would override calculated value later in response
                    row[COL_GEO_GRID] = opReader.grid()
                }

                val op = NakshaRequestOp(feature_arr[i], row, xyzOp = opReader, collectionId = collectionId)
                operations.add(op)
                if (partition == -2) {
                    partition = op.partition
                } else if (partition != op.partition) {
                    partition = -1
                }
            }

            if (DEBUG) println("opReader.mapBytes(op_arr[i]) took ${total / 1000}ms")
            return NakshaWriteOps(collectionId, operations.sortedBy { it.key }, idsToModify, idsToPurge, idsToDel, if (partition >= 0) partition else null)
        }
    }
}
