package naksha.psql.write

import naksha.model.request.ResultRow
import naksha.model.request.WriteRequest
import naksha.model.response.SuccessResponse
import naksha.psql.NKC_TABLE
import naksha.psql.PgSession

class WriteRequestExecutor(
    val session: PgSession,
    private val modifyCounters: Boolean = true
) {

    /**
     * Executes given operations in request in order:
     * 1. DDL (naksha~collections) operations.
     * 2. Features operations.
     * Returns result only on success, otherwise throws exception.
     */
    fun write(writeRequest: WriteRequest): SuccessResponse {
        val responseRows: MutableList<ResultRow> = mutableListOf()

        val collectionOperationsMap = writeRequest.ops.groupBy { it.collectionId }.toMutableMap()

        // first execute operations on NKC and then on features
        if (collectionOperationsMap.contains(NKC_TABLE)) {
            val nkcRequest = writeRequest.newRequestWithOps(collectionOperationsMap[NKC_TABLE]!!.toTypedArray())
            val result = SingleCollectionWriter(NKC_TABLE, session, modifyCounters).writeCollections(nkcRequest)
            responseRows.addAll(result.rows)
            collectionOperationsMap.remove(NKC_TABLE)
        }

        // then execute all others features' operations
        for (collection in collectionOperationsMap.keys) {
            val partialRequest = writeRequest.newRequestWithOps(collectionOperationsMap[collection]!!.toTypedArray())
            val result = SingleCollectionWriter(collection, session, modifyCounters).writeFeatures(partialRequest)
            responseRows.addAll(result.rows)
        }
        return SuccessResponse(rows = responseRows)
    }
}