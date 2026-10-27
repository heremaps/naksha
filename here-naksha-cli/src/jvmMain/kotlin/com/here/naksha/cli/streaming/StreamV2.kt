package com.here.naksha.cli.streaming

import naksha.base.Action
import naksha.base.Id
import naksha.base.JvmMap
import naksha.base.NakshaError
import naksha.base.Version
import naksha.geo.GeoUtil
import naksha.model.objects.NakshaFeature
import naksha.model.streaming.Stream
import naksha.model.streaming.StreamChunk
import naksha.model.streaming.StreamRequest
import naksha.model.streaming.StreamTransaction
import naksha.model.streaming.StreamTuple
import naksha.psql.PgPlan
import java.lang.Long.parseLong

class StreamV2(private val v2Session: StreamReadSessionV2, request: StreamRequest) : Stream(v2Session, request) {

    companion object {
        /**
         * An ever-increasing sequence number <collection>i_seq that is used as the last component of feature UUIDs, also available in a dedicated postgres column and indexed.
         */
        const val VERSION_COLUMN_SQL = "i"
        private const val LEGACY_DATUM_FACTOR = 100_000_000_000L

        /** Converts legacy `yyyyMMdd * 10^11 + seq` txn into the V3 [Version] format. */
        fun legacyTxnToV3Version(legacy: Long): Version {
            val date = legacy / LEGACY_DATUM_FACTOR
            val seq = legacy % LEGACY_DATUM_FACTOR
            val year = (date / 10_000).toInt()
            val month = ((date / 100) % 100).toInt()
            val day = (date % 100).toInt()
            return Version.auto(year, month, day, seq, Action.VERSION)
        }
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
    private var nextVersionToRead = request.minVersion

    override fun hasNext(): Boolean {
        return true
//        return (request.ignoreTransactions) && (minVersionToRead <= request.version) TODO
    }

    override fun isAcknowledged(timeoutMillis: Long): Boolean {
        TODO("Not yet implemented")
    }

    private fun queryHeadSql(request: StreamRequest, startingVersion: Long): String {
        val whereClause = StringBuilder(" WHERE $VERSION_COLUMN_SQL >= $startingVersion")
        if (request.version < Version.HEAD.number) {
            whereClause.append(" AND $VERSION_COLUMN_SQL <= ${request.version}")
        }
        return "SELECT jsondata, ST_AsEWKB(geo) as geo FROM ${request.catalogId}.${request.collectionId}$whereClause ORDER BY $VERSION_COLUMN_SQL ASC LIMIT ${request.chunkSize};"
    }

        /*
WITH next_txn AS (
 (SELECT (jsondata->'properties'->'@ns:com:here:xyz'->'txn')::bigint AS t FROM schema.head WHERE (jsondata->'properties'->'@ns:com:here:xyz'->'txn')::bigint>2026101600000004335
 ORDER BY t ASC LIMIT 1)
UNION ALL
 (SELECT (jsondata->'properties'->'@ns:com:here:xyz'->'txn')::bigint AS t FROM schema.del WHERE (jsondata->'properties'->'@ns:com:here:xyz'->'txn')::bigint>2026101600000004335
 ORDER BY t ASC LIMIT 1)
UNION ALL
 (SELECT (jsondata->'properties'->'@ns:com:here:xyz'->'txn')::bigint AS t FROM schema.hst WHERE (jsondata->'properties'->'@ns:com:here:xyz'->'txn')::bigint>2026101600000004335
 ORDER BY t ASC LIMIT 1)
),
min_txn AS (
 SELECT MIN(t) AS txn FROM next_txn
)
SELECT jsondata, ST_AsEWKB(geo) as geo FROM schema.head head, min_txn n
 WHERE (head.jsondata->'properties'->'@ns:com:here:xyz'->'txn')::bigint=n.txn
UNION ALL
SELECT jsondata, ST_AsEWKB(geo) as geo FROM schema.del del, min_txn n
 WHERE (del.jsondata->'properties'->'@ns:com:here:xyz'->'txn')::bigint=n.txn
UNION ALL
SELECT jsondata, ST_AsEWKB(geo) as geo FROM schema.hst hst, min_txn n
 WHERE (hst.jsondata->'properties'->'@ns:com:here:xyz'->'txn')::bigint=n.txn;
        */


    private var txnPlan: PgPlan? = null

    private fun txnPlan(): PgPlan =
        txnPlan ?: v2Session.connection.prepare(buildTxnSql(), arrayOf("int8")).also { txnPlan = it }

    // Runs once per stream
    private fun buildTxnSql(): String {
        val head = "${request.catalogId}.${request.collectionId}"
        val sb = StringBuilder(1536) // enough for all 3 tables with real names and real txn version
        sb.append("WITH next_txn AS (")
        sb.appendNextTxn(head, "")
        if (request.queryDeleted) sb.append(" UNION ALL ").appendNextTxn(head, "_del")
        if (request.queryHistory) sb.append(" UNION ALL ").appendNextTxn(head, "_hst")
        sb.append("), min_txn AS (SELECT MIN(t) AS txn FROM next_txn) ")
        sb.appendSelect(head, "", "head")
        if (request.queryDeleted) sb.append(" UNION ALL ").appendSelect(head, "_del", "del")
        if (request.queryHistory) sb.append(" UNION ALL ").appendSelect(head, "_hst", "hst")
        return sb.toString()
        //TODO DELETE states are also in hst
        //val notDeleted = if (request.queryDeleted) "" else
        //            " AND NOT EXISTS (SELECT 1 FROM $del d WHERE d.jsondata->>'id' = hst.jsondata->>'id')"
        //        sb.append(notDeleted)
    }

    private fun StringBuilder.appendTxn(alias: String?): StringBuilder {
        append('(')
        if (alias != null) append(alias).append('.')
        return append("jsondata->'properties'->'@ns:com:here:xyz'->'txn')::bigint")
    }

    private fun StringBuilder.appendNextTxn(head: String, suffix: String): StringBuilder =
        append("(SELECT ").appendTxn(null).append(" AS t FROM ").append(head).append(suffix)
            .append(" WHERE ").appendTxn(null).append(">\$1 ORDER BY t ASC LIMIT 1)")

    private fun StringBuilder.appendSelect(head: String, suffix: String, alias: String): StringBuilder =
        append("SELECT ").append("jsondata, ST_AsEWKB(geo) AS geo FROM ")
            .append(head).append(suffix).append(' ').append(alias).append(", min_txn n WHERE ")
            .appendTxn(alias).append("=n.txn")

    override fun next(): StreamChunk {
        if (!hasNext()) {
            throw NoSuchElementException("No more chunks available")
        }
        val cursor = if (!request.ignoreTransactions)
            txnPlan().execute(arrayOf(nextVersionToRead))
        else
            v2Session.connection.execute(queryHeadSql(request, nextVersionToRead))

        val tuples = ArrayList<StreamTuple>()
        var txn : Long? = null
        var txnAsVersion : Version? = null
        try {
            while (cursor.next()) {
                val jsonData : JvmMap = cursor["jsondata"]
                val geoData : ByteArray = cursor["geo"]
                val feature = jsonData.proxy(NakshaFeature::class)
                val xyz = feature.properties.xyz
                val uuid = xyz["uuid"] as String
                val featureSeq = parseLong(uuid, uuid.lastIndexOf(':') + 1, uuid.length, 10)
                txn = checkNotNull(txn ?: xyz["txn"]) as Long //we cannot get txn from xyz because the underlying code is strictly V3 compatible.
                txnAsVersion = txnAsVersion ?: legacyTxnToV3Version(txn)
                val action = xyz.action
                feature.geometry = GeoUtil.fromEWKB(geoData)
                val id = Id(feature.id)
                val streamTuple = StreamTuple(
                    id = id,
                    feature = feature.platformObject(),
                    version = Version.auto(
                        txnAsVersion.year,
                        txnAsVersion.month,
                        txnAsVersion.day,
                        featureSeq,
                        action
                        ).number,
                )
                tuples.add(streamTuple)
            }
        } finally {
            cursor.close()
        }
        val chunk =
            if (request.ignoreTransactions)
                StreamChunk(this, tuples.toTypedArray())
            else StreamTransaction(stream = this,
                id = null,
                version = txnAsVersion!!.number,
                features = tuples.toTypedArray(),
                transaction = null)
        _chunks.add(chunk)
        nextVersionToRead =
            if (request.ignoreTransactions)
                chunk.tuples.maxOfOrNull { it.version }?.let { ((it ushr 2) and Version.SEQ_MAX) + 1 } ?: nextVersionToRead
            else
                txn ?: throw IllegalStateException("Transaction ID not found")
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
        txnPlan?.close()
    }

    override fun closeForRecovery(): StreamRequest {
        TODO("Not yet implemented")
    }

    override fun getRecoveryRequest(timeoutMillis: Long): StreamRequest {
        TODO("Not yet implemented")
    }
}