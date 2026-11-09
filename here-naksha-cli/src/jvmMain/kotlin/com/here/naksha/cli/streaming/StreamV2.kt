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
        private const val DATE_BITS_MASK = -1L shl 32    // year|month|day bits of a V3 version
        private const val SEQ_30_MAX = 0x3FFF_FFFFL      // Version.SEQ_30_MASK is private

        /** Converts legacy `yyyyMMdd * 10^11 + seq` txn into the V3 [Version] format. */
        fun legacyTxnToV3Version(legacy: Long): Version {
            val date = legacy / LEGACY_DATUM_FACTOR
            val seq = legacy % LEGACY_DATUM_FACTOR
            val year = (date / 10_000).toInt()
            val month = ((date / 100) % 100).toInt()
            val day = (date % 100).toInt()
            return Version.auto(year, month, day, seq, Action.VERSION)
        }

        /** Inverse of [legacyTxnToV3Version]. */
        fun v3VersionToLegacyTxn(version: Version): Long =
            (version.year * 10_000L + version.month * 100L + version.day) * LEGACY_DATUM_FACTOR + version.seq

        /** Accepts either a legacy txn (> 2^53) or a dated V3 version; returns the legacy txn. */
        private fun toLegacyTxn(v: Long): Long {
            if (v <= 0L || v > Version.MAX_SAFE_INTEGER) return v
            val version = Version(v)
            require(version.isDated()) { "minVersion $v is neither a legacy txn nor a dated version" }
            return v3VersionToLegacyTxn(version)
        }
    }

    override val minVersion: Version
        get() = synchronized(this) {
            val v = _minVersion
            if (request.ignoreTransactions || v <= 0L) Version(v) else legacyTxnToV3Version(v)
        }
    override val unacknowledgedChunks: Array<StreamChunk>
        get() = synchronized(this) { _pending.filterNot { it in _acknowledgedChunksList }.toTypedArray() }

    override val acknowledgedChunks: Array<StreamChunk>
        get() = synchronized(this) { _acknowledgedChunksList.toTypedArray() }
    override val estimatedChunks: Long = -1
    override val estimatedTuples: Long = -1
    override val acknowledgedTuples: Long = 0
    override val unacknowledgedTuples: Long = 0
    override val isRecoverable: Boolean = false

    private var _minVersion = if (request.ignoreTransactions) request.minVersion else toLegacyTxn(request.minVersion)
    private val _pending = ArrayDeque<StreamChunk>()          // in-flight chunks, oldest first
    private val _acknowledgedChunksList = ArrayList<StreamChunk>()
    private var nextVersionToRead = _minVersion
    private var hasNext : Boolean = true

    /** Position to resume from after [chunk]; `null` if the chunk is empty (position unchanged). */
    private fun resumePointOf(chunk: StreamChunk): Long? =
        if (request.ignoreTransactions)
            chunk.tuples.lastOrNull()?.let { ((it.version ushr 2) and SEQ_30_MAX) + 1 }
        else
            (chunk as StreamTransaction).version.takeIf { it > 0 }?.let { v3VersionToLegacyTxn(Version(it)) }

    override fun hasNext(): Boolean {
        return hasNext
    }

    override fun isAcknowledged(timeoutMillis: Long): Boolean {
        TODO("Not yet implemented")
    }

    private fun StringBuilder.appendNotDeleted(alias: String?): StringBuilder {
        append(" AND (")
        if (alias != null) append(alias).append('.')
        return append("jsondata->'properties'->'@ns:com:here:xyz'->>'action') IS DISTINCT FROM 'DELETE'")
    }

/*
SELECT jsondata, ST_AsEWKB(geo) AS geo FROM (
 (SELECT jsondata, geo, i FROM schema.col WHERE i>=$1 ORDER BY i ASC LIMIT 1000)
 UNION ALL
 (SELECT jsondata, geo, i FROM schema.col_hst WHERE i>=$1
   AND (jsondata->'properties'->'@ns:com:here:xyz'->>'action') IS DISTINCT FROM 'DELETE'
   ORDER BY i ASC LIMIT 1000)
) u ORDER BY i ASC LIMIT 1000

-- request.version < HEAD: each branch also gets "AND i<=<version>"
-- queryHistory=true,  queryDeleted=true : col_hst branch without the action filter
-- queryHistory=false, queryDeleted=true : col_del branch instead of col_hst, no action filter
*/

    private var _headPlan: PgPlan? = null

    /**
     * Prepare the SQL statement only once per stream, and reuse it for each chunk.
     */
    private fun headPlan(): PgPlan =
        _headPlan ?: v2Session.connection.prepare(buildChunkSql(), arrayOf("int8")).also { _headPlan = it }

    // Runs once per stream
    private fun buildChunkSql(): String {
        val head = "${request.catalogId}.${request.collectionId}"
        val sb = StringBuilder(512) // enough for all tables with real names and real version
        sb.append("SELECT jsondata, ST_AsEWKB(geo) AS geo FROM (")
        sb.appendChunkSelect(head, "")
        if (request.queryHistory) sb.append(" UNION ALL ").appendChunkSelect(head, "_hst", !request.queryDeleted)
        // Only query del table if we query deleted but not history. If we query history, deleted features are already included in hst tables.
        else if (request.queryDeleted) sb.append(" UNION ALL ").appendChunkSelect(head, "_del")
        sb.append(") u ORDER BY ").append(VERSION_COLUMN_SQL).append(" ASC LIMIT ").append(request.chunkSize)
        return sb.toString()
    }

    private fun StringBuilder.appendChunkSelect(head: String, suffix: String, skipDeleted: Boolean = false): StringBuilder {
        append("(SELECT jsondata, geo, ").append(VERSION_COLUMN_SQL)
            .append(" FROM ").append(head).append(suffix)
            .append(" WHERE ").append(VERSION_COLUMN_SQL).append($$">=$1")
        if (request.version < Version.HEAD.number) {
            append(" AND ").append(VERSION_COLUMN_SQL).append("<=").append(request.version)
        }
        if (skipDeleted) appendNotDeleted(null)
        return append(" ORDER BY ").append(VERSION_COLUMN_SQL).append(" ASC LIMIT ").append(request.chunkSize).append(')')
    }

/*
WITH next_txn AS (
  (SELECT (jsondata->'properties'->'@ns:com:here:xyz'->'txn')::bigint AS t FROM schema.col
    WHERE (jsondata->'properties'->'@ns:com:here:xyz'->'txn')::bigint>$1
    ORDER BY t ASC LIMIT 1)
  UNION ALL
  (SELECT (jsondata->'properties'->'@ns:com:here:xyz'->'txn')::bigint AS t FROM schema.col_hst
    WHERE (jsondata->'properties'->'@ns:com:here:xyz'->'txn')::bigint>$1
    AND (jsondata->'properties'->'@ns:com:here:xyz'->>'action') IS DISTINCT FROM 'DELETE'
    ORDER BY t ASC LIMIT 1)
  ),
min_txn AS (SELECT MIN(t) AS txn FROM next_txn)
SELECT jsondata, ST_AsEWKB(geo) AS geo FROM schema.col head, min_txn n
  WHERE (head.jsondata->'properties'->'@ns:com:here:xyz'->'txn')::bigint=n.txn
UNION ALL
SELECT jsondata, ST_AsEWKB(geo) AS geo FROM schema.col_hst hst, min_txn n
  WHERE (hst.jsondata->'properties'->'@ns:com:here:xyz'->'txn')::bigint=n.txn
  AND (hst.jsondata->'properties'->'@ns:com:here:xyz'->>'action') IS DISTINCT FROM 'DELETE'

-- queryHistory=true,  queryDeleted=true : col_hst branches without the action filter
-- queryHistory=false, queryDeleted=true : col_del / del branches instead of col_hst / hst, no action filter
*/


    private var _txnPlan: PgPlan? = null

    /**
     * Prepare the SQL statement only once per stream, and reuse it for each chunk.
     */
    private fun txnPlan(): PgPlan =
        _txnPlan ?: v2Session.connection.prepare(buildTxnSql(), arrayOf("int8")).also { _txnPlan = it }

    // Runs once per stream
    private fun buildTxnSql(): String {
        val head = "${request.catalogId}.${request.collectionId}"
        val sb = StringBuilder(1536) // enough for all tables with real names and real txn version
        sb.append("WITH next_txn AS (")
        sb.appendNextTxn(head, "")
        if (request.queryHistory) sb.append(" UNION ALL ").appendNextTxn(head, "_hst", !request.queryDeleted)
        // Only query del table if we query deleted but not history. If we query history, deleted features are already included in hst tables.
        else if (request.queryDeleted) sb.append(" UNION ALL ").appendNextTxn(head, "_del")
        sb.append("), min_txn AS (SELECT MIN(t) AS txn FROM next_txn) ")
        sb.appendTxnSelect(head, "", "head")
        if (request.queryHistory) sb.append(" UNION ALL ").appendTxnSelect(head, "_hst", "hst", !request.queryDeleted)
        // Only query del table if we query deleted but not history. If we query history, deleted features are already included in hst tables.
        else if (request.queryDeleted) sb.append(" UNION ALL ").appendTxnSelect(head, "_del", "del")
        return sb.toString()
    }

    private fun StringBuilder.appendTxn(alias: String?): StringBuilder {
        append('(')
        if (alias != null) append(alias).append('.')
        return append("jsondata->'properties'->'@ns:com:here:xyz'->'txn')::bigint")
    }

    private fun StringBuilder.appendNextTxn(head: String, suffix: String, skipDeleted: Boolean = false): StringBuilder {
        append("(SELECT ").appendTxn(null).append(" AS t FROM ").append(head).append(suffix)
            .append(" WHERE ").appendTxn(null).append($$">$1")
        if (skipDeleted) appendNotDeleted(null)
        return append(" ORDER BY t ASC LIMIT 1)")
    }

    private fun StringBuilder.appendTxnSelect(head: String, suffix: String, alias: String, skipDeleted: Boolean = false): StringBuilder {
        append("SELECT jsondata, ST_AsEWKB(geo) AS geo FROM ")
            .append(head).append(suffix).append(' ').append(alias).append(", min_txn n WHERE ")
            .appendTxn(alias).append("=n.txn")
        if (skipDeleted) appendNotDeleted(alias)
        return this
    }

    override fun next(): StreamChunk {
        if (!hasNext()) {
            throw NoSuchElementException("No more chunks available")
        }
        val cursor = if (request.ignoreTransactions)
            headPlan().execute(arrayOf(nextVersionToRead))
        else
            txnPlan().execute(arrayOf(nextVersionToRead))

        val tuples = ArrayList<StreamTuple>()
        if (request.ignoreTransactions) tuples.ensureCapacity(request.chunkSize)
        var txn = Long.MIN_VALUE
        var txnVersion = 0L
        var lastSeq = -1L
        try {
            while (cursor.next()) {
                val jsonData : JvmMap = cursor["jsondata"]
                val geoData : ByteArray = cursor["geo"]
                val feature = jsonData.proxy(NakshaFeature::class)
                val xyz = (jsonData["properties"] as JvmMap)["@ns:com:here:xyz"] as JvmMap // to avoid proxy creations when using feature.properties.xyz
                val uuid = xyz["uuid"] as String
                val featureSeq = parseLong(uuid, uuid.lastIndexOf(':') + 1, uuid.length, 10)
                lastSeq = featureSeq
                val rowTxn = xyz["txn"] as Long
                if (rowTxn != txn) {
                    txn = rowTxn
                    txnVersion = legacyTxnToV3Version(rowTxn).number
                }
                require(featureSeq in 0..SEQ_30_MAX) { "seq out of 30-bit range: $featureSeq" }
                val action = Action.fromString(xyz["action"] as String)
                feature.geometry = GeoUtil.fromEWKB(geoData)
                val id = Id(feature.id)
                val streamTuple = StreamTuple(
                    id = id,
                    feature = feature.platformObject(),
                    version = (txnVersion and DATE_BITS_MASK) or (featureSeq shl 2) or action.longValue
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
                version = txnVersion,
                features = tuples.toTypedArray(),
                transaction = null)
        if (request.ignoreTransactions && chunk.tuples.size < request.chunkSize) hasNext = false
        if (!request.ignoreTransactions && (txn == Long.MIN_VALUE || txn == Version.HEAD.number)) hasNext = false
        nextVersionToRead =
            if (request.ignoreTransactions)
                if (lastSeq >= 0) lastSeq + 1 else nextVersionToRead
            else
                if (txn == Long.MIN_VALUE) nextVersionToRead else txn
        synchronized(this) { _pending.addLast(chunk) }
        return chunk
    }

    override fun next(timeoutMillis: Long): StreamChunk? {
        TODO("Not yet implemented")
    }

    override fun acknowledge(chunk: StreamChunk): Unit = synchronized(this) {
        require(chunk.stream === this) { "Chunk not from this stream" }
        // Already trimmed (fully acknowledged) or already acknowledged: ignore, as the contract requires
        if (chunk !in _pending || chunk in _acknowledgedChunksList) return
        _acknowledgedChunksList.add(chunk)
        while (_pending.isNotEmpty() && _acknowledgedChunksList.remove(_pending.first())) {
            resumePointOf(_pending.removeFirst())?.let { _minVersion = it }
        }
    }

    override fun fail(chunk: StreamChunk, reason: NakshaError) {
        TODO("Not yet implemented")
    }

    override fun isClosed(): Boolean {
        TODO("Not yet implemented")
    }

    override fun close() {
        _txnPlan?.close()
        _headPlan?.close()
    }

    override fun closeForRecovery(): StreamRequest {
        TODO("Not yet implemented")
    }

    override fun getRecoveryRequest(): StreamRequest {
        TODO("Not yet implemented")
    }
}