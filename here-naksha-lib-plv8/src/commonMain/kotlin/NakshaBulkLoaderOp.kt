package com.here.naksha.lib.plv8

import com.here.naksha.lib.jbon.*

internal class NakshaBulkLoaderOp(
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
                geo_type_arr: Array<Short>,
                geo_arr: Array<ByteArray?>,
                tags_arr: Array<ByteArray?>
        ): NakshaBulkLoaderOps {
            check(op_arr.size == feature_arr.size && op_arr.size == geo_type_arr.size && op_arr.size == geo_arr.size && op_arr.size == tags_arr.size) {
                "not all input arrays has same size"
            }
            var partition : Int = -2
            val featureReader = JbFeature(JbDictManager())
            val operations = ArrayList<NakshaBulkLoaderOp>(op_arr.size)
            val idsToModify = ArrayList<String>(op_arr.size)
            val idsToPurge = ArrayList<String>()
            var total = 0
            val opReader = XyzOp()
            for (i in op_arr.indices) {
                val START = Jb.env.currentMicros()
                opReader.mapBytes(op_arr[i])
                val END = Jb.env.currentMicros()
                total += (END - START).toInt()

                val id = if (opReader.id() == null) {
                    featureReader.mapBytes(feature_arr[i])
                    featureReader.id() ?: throw NakshaException.forId(ERR_FEATURE_NOT_EXISTS, "Missing id", null)
                } else {
                    opReader.id()!!
                }
                if (opReader.op() != XYZ_OP_CREATE) {
                    idsToModify.add(id)
                    if (opReader.op() == XYZ_OP_PURGE) {
                        idsToPurge.add(id)
                    }
                }
                val row = newMap()
                row[COL_ID] = id
                row[COL_TAGS] = tags_arr[i]
                row[COL_GEOMETRY] = geo_arr[i]
                row[COL_FEATURE] = feature_arr[i]
                row[COL_GEO_TYPE] = geo_type_arr[i]
                row[COL_GEO_GRID] = opReader.grid()

                val op = NakshaBulkLoaderOp(row,xyzOp = opReader,collectionId = collectionId)
                operations.add(op)
                if (partition == -2) {
                    partition = op.partition
                } else if (partition != op.partition) {
                    partition = -1
                }
            }
            println("opReader.mapBytes(op_arr[i]) took ${total / 1000}ms")
            return NakshaBulkLoaderOps(operations.sortedBy { it.key }, idsToModify, idsToPurge, if (partition>=0) partition else null)
        }
    }
}
