package com.here.naksha.lib.plv8

class NakshaBulkLoader(
        val session: NakshaSession
) {
    fun bulkWriteFeatures(
            collectionId: String,
            op_arr: Array<ByteArray>,
            feature_arr: Array<ByteArray?>,
            geo_type_arr: Array<Short>,
            geo_arr: Array<ByteArray?>,
            tags_arr: Array<ByteArray?>
    ): ITable {
        TODO()
    }
}