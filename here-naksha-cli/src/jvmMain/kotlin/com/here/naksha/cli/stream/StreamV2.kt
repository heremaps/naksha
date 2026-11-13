package com.here.naksha.cli.stream

import naksha.base.Action
import naksha.base.Id
import naksha.base.JvmMap
import naksha.base.NakshaError
import naksha.base.NakshaError.NakshaErrorCompanion.CLOSED
import naksha.base.NakshaError.NakshaErrorCompanion.TIMEOUT
import naksha.base.NakshaException
import naksha.base.Version
import naksha.geo.GeoUtil
import naksha.model.objects.NakshaFeature
import naksha.model.streaming.Stream
import naksha.model.streaming.StreamChunk
import naksha.model.streaming.StreamException
import naksha.model.streaming.StreamRequest
import naksha.model.streaming.StreamTransaction
import naksha.model.streaming.StreamTuple
import naksha.psql.PgPlan
import naksha.psql.PsqlPlan
import java.lang.Long.parseLong
import java.sql.SQLException
import java.sql.SQLTimeoutException
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

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
        get() = _stateLock.withLock {
            val v = _minVersion
            if (request.ignoreTransactions || v <= 0L) Version(v) else legacyTxnToV3Version(v)
        }
    override val unacknowledgedChunks: Array<StreamChunk>
        get() = _stateLock.withLock { _pending.filterNot { it in _acknowledgedChunksList }.toTypedArray() }

    override val acknowledgedChunks: Array<StreamChunk>
        get() = _stateLock.withLock { _acknowledgedChunksList.toTypedArray() }
    override val estimatedChunks: Long = -1
    override val estimatedTuples: Long = -1
    override val acknowledgedTuples: Long
        get() = _stateLock.withLock { _acknowledgedTuples }
    override val unacknowledgedTuples: Long
        get() = _stateLock.withLock { _readTuples - _acknowledgedTuples }
    override val isRecoverable: Boolean = true

    private var _minVersion = if (request.ignoreTransactions) request.minVersion else toLegacyTxn(request.minVersion)
    private val _pending = ArrayDeque<StreamChunk>()          // in-flight chunks, oldest first
    private val _acknowledgedChunksList = ArrayList<StreamChunk>()
    private var _readTuples = 0L
    private var _acknowledgedTuples = 0L
    private var _nextVersionToRead = _minVersion
    private var _hasNext : Boolean = true
    private var _recoveryRequest: StreamRequest? = null
    @Volatile
    private var _closed = false
    private val _readLock = ReentrantLock()

    private val _stateLock = ReentrantLock()
    private val _stateChanged = _stateLock.newCondition()
    private val _closeLock = ReentrantLock()
    private var _closeComplete = false
    private var _failure: NakshaError? = null

    /** Position to resume from after [chunk]; `null` if the chunk is empty (position unchanged). */
    private fun resumePointOf(chunk: StreamChunk): Long? =
        if (request.ignoreTransactions)
            chunk.tuples.lastOrNull()?.let { ((it.version ushr 2) and SEQ_30_MAX) + 1 }
        else
            (chunk as StreamTransaction).version.takeIf { it > 0 }?.let { v3VersionToLegacyTxn(Version(it)) }

    override fun hasNext(): Boolean = _hasNext && !_closed

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
    @Volatile
    private var _plan: PgPlan? = null

    /**
     * Prepare the SQL statement only once per stream, and reuse it for each chunk.
     */
    private fun headPlan(): PgPlan =
        _plan ?: v2Session.connection.prepare(buildChunkSql(), arrayOf("int8")).also { _plan = it }

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


    /**
     * Prepare the SQL statement only once per stream, and reuse it for each chunk.
     */
    private fun txnPlan(): PgPlan =
        _plan ?: v2Session.connection.prepare(buildTxnSql(), arrayOf("int8")).also { _plan = it }

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

    private fun Throwable.isQueryCancelled(): Boolean =
        generateSequence(this) { it.cause }.any {
            it is SQLTimeoutException || (it is SQLException && it.sqlState == "57014")
        }

    /** Must hold [_readLock]. Returns `null` if the query was cancelled (timeout or close); state is then unchanged. */
    private fun readChunk(queryTimeoutSec: Int): StreamChunk? {
        val plan = if (request.ignoreTransactions) headPlan() else txnPlan()
        (plan as PsqlPlan).stmt.queryTimeout = queryTimeoutSec   // plans are reused, so set it on every call; 0 = no limit
        val cursor = try {
            throwIfFailed()
            if (_closed) return null
            plan.execute(arrayOf(_nextVersionToRead))
        } catch (e: NakshaException) {
            if (e.isQueryCancelled()) {
                throwIfFailed()
                return null
            }
            throw e
        }
        val tuples = ArrayList<StreamTuple>()
        if (request.ignoreTransactions) tuples.ensureCapacity(request.chunkSize)
        var txn = Long.MIN_VALUE
        var txnVersion = 0L
        try {
            while (!_closed && cursor.next()) {
                val jsonData : JvmMap = cursor["jsondata"]
                val geoData : ByteArray = cursor["geo"]
                val feature = jsonData.proxy(NakshaFeature::class)
                val xyz = (jsonData["properties"] as JvmMap)["@ns:com:here:xyz"] as JvmMap // to avoid proxy creations when using feature.properties.xyz
                val uuid = xyz["uuid"] as String
                val featureSeq = parseLong(uuid, uuid.lastIndexOf(':') + 1, uuid.length, 10)
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
        } catch (e: SQLException) {
            if (!e.isQueryCancelled()) throw e
            throwIfFailed()
            return null
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
        _stateLock.withLock {
            throwIfFailed()
            if (_closed) return null
            if (request.ignoreTransactions && chunk.tuples.size < request.chunkSize) _hasNext = false
            if (!request.ignoreTransactions && (txn == Long.MIN_VALUE || txn == Version.HEAD.number)) _hasNext = false
            _nextVersionToRead = resumePointOf(chunk) ?: _nextVersionToRead
            _pending.addLast(chunk)
            _readTuples += chunk.tuples.size
        }
        return chunk
    }

    override fun next(): StreamChunk = _readLock.withLock {
        throwIfFailed()
        if (_closed) throw NakshaException(CLOSED, "Stream closed")
        if (!_hasNext) throw NoSuchElementException("No more chunks available")
        readChunk(0) ?: throw NakshaException(CLOSED, "Stream closed")   // null only if cancelled from outside
    }

    override fun next(timeoutMillis: Long): StreamChunk? {
        throwIfFailed()
        if (_closed) throw NakshaException(CLOSED, "Stream closed")
        if (!_hasNext) throw NoSuchElementException("No more chunks available")
        if (timeoutMillis <= 0L) return null   // nothing prefetched, a DB round-trip is never instant
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
        if (!_readLock.tryLock(timeoutMillis, TimeUnit.MILLISECONDS)) return null
        try {
            if (_closed) return null           // close() was called while we were waiting
            if (!_hasNext) throw NoSuchElementException("No more chunks available")
            val remainingMs = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime())
            if (remainingMs <= 0L) return null
            val seconds = ((remainingMs + 999L) / 1000L).coerceIn(1L, Int.MAX_VALUE.toLong()).toInt()
            return readChunk(seconds)
        } finally {
            _readLock.unlock()
        }
    }


    override fun acknowledge(chunk: StreamChunk): Unit = _stateLock.withLock {
        require(chunk.stream === this) { "Chunk not from this stream" }
        // Already trimmed (fully acknowledged) or already acknowledged: ignore, as the contract requires
        if (chunk !in _pending || chunk in _acknowledgedChunksList) return
        _acknowledgedChunksList.add(chunk)
        _acknowledgedTuples += chunk.tuples.size
        // TODO what if 1 writer stalls and cause _acknowledgedChunksList list to grow too much?
        while (_pending.isNotEmpty() && _acknowledgedChunksList.remove(_pending.first())) {
            resumePointOf(_pending.removeFirst())?.let { _minVersion = it }
        }
        _stateChanged.signalAll()
    }

    override fun isAcknowledged(timeoutMillis: Long): Boolean = _stateLock.withLock {
        var remaining = TimeUnit.MILLISECONDS.toNanos(timeoutMillis.coerceAtLeast(0L))
        while (_pending.isNotEmpty() && _failure == null) {
            if (remaining <= 0L) return false
            remaining = _stateChanged.awaitNanos(remaining)
        }
        _failure == null
    }

    override fun fail(chunk: StreamChunk, reason: NakshaError): Unit = _stateLock.withLock {
        require(chunk.stream === this) { "Chunk not from this stream" }
        if (chunk !in _pending || chunk in _acknowledgedChunksList) return
        if (_failure == null) _failure = reason
        _closed = true
        _stateChanged.signalAll()
    }

    private fun throwIfFailed(): Unit = _stateLock.withLock {
        _failure?.let { throw StreamException(getRecoveryRequest(), this, it) }
    }

    override fun isClosed(): Boolean = _closed

    override fun close(): Unit = _closeLock.withLock {
        if (_closeComplete) {
            throwIfFailed()
            return
        }
        _stateLock.withLock { _closed = true }

        try {
            try {
                // Repeat: JDBC cancel() can do nothing just before execute() starts.
                while (!_readLock.tryLock(10L, TimeUnit.MILLISECONDS)) {
                    _plan?.let { (it as PsqlPlan).stmt.cancel() }
                }
            } finally {
                // Release the plan even if cancellation or lock waiting fails.
                if (!_readLock.isHeldByCurrentThread) _readLock.lock()
                try {
                    val plan = _plan
                    _plan = null
                    plan?.close()
                } finally {
                    _readLock.unlock()
                }
            }

            _stateLock.withLock {
                // Same fallback as QueueStream.timeoutMillis().
                val configuredTimeout = v2Session.options.stmtTimeout.toLong()
                val timeoutMillis = if (configuredTimeout > 0L) configuredTimeout else 300_000L

                if (!isAcknowledged(timeoutMillis) && _failure == null) {
                    _failure = NakshaError(TIMEOUT, "Timeout waiting for acknowledgements")
                }
                throwIfFailed()

                if (_hasNext) {
                    throw StreamException(
                        getRecoveryRequest(),
                        this,
                        CLOSED,
                        "Stream closed with unread chunks"
                    )
                }
            }
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw e
        } finally {
            _closeComplete = true
        }
    }

    override fun closeForRecovery(): StreamRequest {
        try {
            close()
        } catch (e: StreamException) {
            return e.recoveryRequest
        } catch (e: SQLException) {
            NakshaError(
                NakshaError.EXCEPTION,
                "SQL error while closing stream for recovery",
                e
            ).print()
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            NakshaError(
                NakshaError.EXCEPTION,
                "Interrupted while closing stream for recovery",
                e
            ).print()
        }
        return getRecoveryRequest()
    }

    override fun getRecoveryRequest(): StreamRequest = _stateLock.withLock {
        val position = _minVersion
        val cached = _recoveryRequest
        if (cached != null && cached.minVersion == position) cached
        else StreamRequest(
            databaseId = request.databaseId,
            catalogId = request.catalogId,
            collectionId = request.collectionId,
            version = request.version,
            queryDeleted = request.queryDeleted,
            queryHistory = request.queryHistory,
            sequential = request.sequential,
            minVersion = position,
            ignoreTransactions = request.ignoreTransactions,
            chunkSize = request.chunkSize
        ).also {
            _recoveryRequest = it
        }
    }
}