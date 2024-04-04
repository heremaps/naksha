package com.here.naksha.lib.plv8

import com.here.naksha.lib.jbon.*

class FeatureRow(
        val rowMap: IMap,
        val xyzOp: XyzOp,
        val collectionId: String
) {
    val partition: Int = Static.partitionNumber(id())

    fun id(): String = rowMap[COL_ID]!!

    companion object {
        fun mapToOperations(
                collectionId: String,
                op_arr: Array<ByteArray>,
                feature_arr: Array<ByteArray?>,
                geo_type_arr: Array<Short>,
                geo_arr: Array<ByteArray?>,
                tags_arr: Array<ByteArray?>
        ): Operations {
            check(op_arr.size == feature_arr.size && op_arr.size == geo_type_arr.size && op_arr.size == geo_arr.size && op_arr.size == tags_arr.size) {
                "not all input arrays has same size"
            }
            val featureReader = JbFeature(JbDictManager())
            val operations = ArrayList<FeatureRow>(op_arr.size)
            val idsToModify = ArrayList<String>(op_arr.size)
            val idsToPurge = ArrayList<String>()
            for (i in op_arr.indices) {
                val opReader = XyzOp()
                opReader.mapBytes(op_arr[i])

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

                operations.add(FeatureRow(
                        row,
                        xyzOp = opReader,
                        collectionId = collectionId
                ))
            }
            return Operations(operations, idsToModify, idsToPurge)
        }
    }
}

data class Operations(val operations: List<FeatureRow>, val idsToModify: List<String>, val idsToPurge: List<String>)