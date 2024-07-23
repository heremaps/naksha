package naksha.psql.write

import naksha.model.request.ResultRow
import naksha.model.request.WriteRequest
import naksha.model.response.SuccessResponse
import naksha.psql.NKC_TABLE
import naksha.psql.PgResultSet
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
            val nkcRequest = writeRequest.copyTo(WriteRequest())
            nkcRequest.ops.clear()
            val writeOps = collectionOperationsMap[NKC_TABLE]
            if (writeOps != null) nkcRequest.ops.addAll(writeOps)
            val result = SingleCollectionWriter(NKC_TABLE, session, modifyCounters).writeCollections(nkcRequest)
            responseRows.addAll(result.resultSet.rows())
            collectionOperationsMap.remove(NKC_TABLE)
        }

        // then execute all others features' operations
        for (collection in collectionOperationsMap.keys) {
            val partialRequest = writeRequest.copyTo(WriteRequest())
            partialRequest.ops.clear()
            val writeOps = collectionOperationsMap[NKC_TABLE]
            if (writeOps != null) partialRequest.ops.addAll(writeOps)
            val result = SingleCollectionWriter(collection, session, modifyCounters).writeFeatures(partialRequest)
            responseRows.addAll(result.resultSet.rows())
        }
        return SuccessResponse(PgResultSet(session.storage, writeRequest.rowOptions, responseRows))
    }
}