package com.here.naksha.lib.plv8

internal data class NakshaWriteOps(
        val operations: List<NakshaRequestOp>,
        val idsToModify: List<String>,
        val idsToPurge: List<String>,
        val idsToDel: List<String>,
        /**
         * If all features are in one partition, this holds the partition id, otherwise _null_.
         */
        val partition: Int?
)
