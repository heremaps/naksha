package com.here.naksha.lib.plv8

internal data class NakshaBulkLoaderOps(
        val operations: List<NakshaBulkLoaderOp>,
        val idsToModify: List<String>,
        val idsToPurge: List<String>,
        /**
         * If all features are in one partition, this holds the partition id, otherwise _null_.
         */
        val partition: Int?
)
