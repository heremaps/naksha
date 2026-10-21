package com.here.naksha.cli.streaming

import naksha.base.Id
import naksha.base.JvmMap
import naksha.base.NakshaError
import naksha.base.Version
import naksha.geo.GeoUtil
import naksha.model.objects.NakshaFeature
import naksha.model.streaming.Stream
import naksha.model.streaming.StreamChunk
import naksha.model.streaming.StreamRequest
import naksha.model.streaming.StreamTuple

class StreamV2(private val v2Session: StreamReadSessionV2, request: StreamRequest) : Stream(v2Session, request) {

    companion object {
        /**
         * An ever-increasing sequence number <collection>i_seq that is used as the last component of feature UUIDs, also available in a dedicated postgres column and indexed.
         */
        const val VERSION_COLUMN_SQL = "i"
        private const val TXN_INDEX_SQL = "(jsondata->'properties'->'@ns:com:here:xyz'->'txn')::bigint"
    }

    private fun txnIndexSqlWithTableAlias(alias: String): String {
        return "($alias.jsondata->'properties'->'@ns:com:here:xyz'->'txn')::bigint"
    }

    override val minVersion: Version
        get() = Version(_minVersion)
    override val unacknowledgedChunks: Array<StreamChunk>
        get() = _chunks.subList(minAckChunkIndex + 1, _chunks.size)
                .filterNot { it in _acknowledgedChunksList }.toTypedArray()

    override val acknowledgedChunks: Array<StreamChunk>
        get() = _acknowledgedChunksList.toTypedArray()
    override val estimatedChunks: Long = -1
    override val estimatedTuples: Long = -1
    override val acknowledgedTuples: Long = 0
    override val unacknowledgedTuples: Long = 0
    override val isRecoverable: Boolean = false

    private var _minVersion = request.minVersion
    private val _chunks = ArrayList<StreamChunk>()
    private val _acknowledgedChunksList = ArrayList<StreamChunk>()
    private var minAckChunkIndex = -1
    private var minVersionToRead = request.minVersion

    override fun hasNext(): Boolean {
        return minVersionToRead <= request.version
    }

    override fun isAcknowledged(timeoutMillis: Long): Boolean {
        TODO("Not yet implemented")
    }

    private fun queryHeadSql(request: StreamRequest, startingVersion: Long): String {
        val whereClause = StringBuilder(" WHERE $VERSION_COLUMN_SQL >= $startingVersion")
        if (request.version < Version.HEAD.number) {
            whereClause.append(" AND $VERSION_COLUMN_SQL <= ${request.version}")
        }
        return "SELECT jsondata, ST_AsEWKB(geo) as geo, i FROM ${request.catalogId}.${request.collectionId}$whereClause ORDER BY $VERSION_COLUMN_SQL ASC LIMIT ${request.chunkSize};"
    }

    private fun queryByTransactionSql(request: StreamRequest, previousTxn: Long): String {
        return """
WITH next_txn AS (
 (SELECT $TXN_INDEX_SQL AS t FROM ${request.catalogId}.${request.collectionId} WHERE $TXN_INDEX_SQL>$previousTxn
 ORDER BY t ASC LIMIT 1)
UNION ALL
 (SELECT $TXN_INDEX_SQL AS t FROM ${request.catalogId}.${request.collectionId}_del WHERE $TXN_INDEX_SQL>$previousTxn
 ORDER BY t ASC LIMIT 1)
UNION ALL
 (SELECT $TXN_INDEX_SQL AS t FROM ${request.catalogId}.${request.collectionId}_hst WHERE $TXN_INDEX_SQL>$previousTxn
 ORDER BY t ASC LIMIT 1)
),
min_txn AS (
 SELECT MIN(t) AS txn
 FROM next_txn
)
SELECT jsondata, ST_AsEWKB(geo) as geo, i FROM ${request.catalogId}.${request.collectionId} head, min_txn n
 WHERE ${txnIndexSqlWithTableAlias("head")}=n.txn
UNION ALL
SELECT jsondata, ST_AsEWKB(geo) as geo, i FROM ${request.catalogId}.${request.collectionId}_del del, min_txn n
 WHERE ${txnIndexSqlWithTableAlias("del")}=n.txn
UNION ALL
SELECT jsondata, ST_AsEWKB(geo) as geo, i FROM ${request.catalogId}.${request.collectionId}_hst hst, min_txn n
 WHERE ${txnIndexSqlWithTableAlias("hst")}=n.txn;
"""
    }

    override fun next(): StreamChunk {
        if (!hasNext()) {
            throw NoSuchElementException("No more chunks available")
        }
        val sql =
//            if (request.queryHistory && request.queryDeleted) queryByTransactionSql(request, minVersionToRead) else
                queryHeadSql(request, minVersionToRead)
        val cursor = v2Session.connection.execute(sql)
        val tuples = ArrayList<StreamTuple>()
        while (cursor.next()) {
            val jsonData : JvmMap = cursor["jsondata"]
            val geoData : ByteArray = cursor["geo"]
            val version : Long = cursor["i"]
            val feature = jsonData.proxy(NakshaFeature::class)
            feature.geometry = GeoUtil.fromEWKB(geoData)
            val id = Id(feature.id)
            val streamTuple = StreamTuple(
                id = id,
                feature = feature.platformObject(),
                version = version,
            )
            tuples.add(streamTuple)
        }
        val chunk = StreamChunk(this, tuples.toTypedArray())
        _chunks.add(chunk)
        minVersionToRead = chunk.tuples.maxOfOrNull { it.version }?.plus(1) ?: minVersionToRead
        return chunk
    }

    override fun next(timeoutMillis: Long): StreamChunk? {
        TODO("Not yet implemented")
    }

    override fun acknowledge(chunk: StreamChunk): Unit = synchronized(this) {
        val index = _chunks.indexOf(chunk)
        require(index >= 0) { "Chunk not found in stream" }
        // Already acknowledged: ignore, as the contract requires
        if (index <= minAckChunkIndex || _acknowledgedChunksList.contains(chunk)) return
        _acknowledgedChunksList.add(chunk)

        // Move the recovery point forward over the contiguous acknowledged prefix
        while (minAckChunkIndex + 1 < _chunks.size) {
            val head = _chunks[minAckChunkIndex + 1]
            if (!_acknowledgedChunksList.remove(head)) break
            minAckChunkIndex++
            val maxVersion = head.tuples.maxOfOrNull { it.version } ?: continue
            if (maxVersion >= 0) _minVersion = maxVersion + 1 //This works only for V2 and compatible storages, not V3, because V3 versions contain Action bits
        }
    }

    override fun fail(chunk: StreamChunk, reason: NakshaError) {
        TODO("Not yet implemented")
    }

    override fun isClosed(): Boolean {
        TODO("Not yet implemented")
    }

    override fun close() {
        TODO("Not yet implemented")
    }

    override fun closeForRecovery(): StreamRequest {
        TODO("Not yet implemented")
    }

    override fun getRecoveryRequest(timeoutMillis: Long): StreamRequest {
        TODO("Not yet implemented")
    }
}