package naksha.psql

import naksha.base.Action
import naksha.base.Id
import naksha.base.NakshaError
import naksha.base.NakshaError.NakshaErrorCompanion.CLOSED
import naksha.base.NakshaError.NakshaErrorCompanion.COLLECTION_NOT_FOUND
import naksha.base.NakshaError.NakshaErrorCompanion.EXCEPTION
import naksha.base.NakshaError.NakshaErrorCompanion.TIMEOUT
import naksha.base.NakshaException
import naksha.base.PlatformMap
import naksha.base.Version
import naksha.base.unsupportedOp
import naksha.model.IStorage
import naksha.model.IStreamSession
import naksha.model.SessionOptions
import naksha.model.Tuple
import naksha.model.objects.NakshaTx
import naksha.model.streaming.Stream
import naksha.model.streaming.StreamChunk
import naksha.model.streaming.StreamException
import naksha.model.streaming.StreamRequest
import naksha.model.streaming.StreamTransaction
import naksha.model.streaming.StreamTuple
import naksha.psql.PgColumn.PgColumn_C.FnColumn
import naksha.psql.PgColumn.PgColumn_C.VersionColumn
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class PsqlStreamSession(
    private val pgStorage: PsqlStorage,
    override val options: SessionOptions,
) : IStreamSession {
    override val storage: IStorage get() = pgStorage
    override var socketTimeout: Int = options.socketTimeout
    override var stmtTimeout: Int = options.stmtTimeout
    override var lockTimeout: Int = options.lockTimeout

    @Volatile
    private var closed = false

    override val mayRead: Boolean get() = true
    override val mayWrite: Boolean get() = false

    override fun read(request: StreamRequest): Stream {
        val conn = pgStorage.newConnection(options, true)
        try {
            val catalog = pgStorage.adminCatalog.getPgCatalogById(conn, request.catalogId.text)
                ?: throw NakshaException(COLLECTION_NOT_FOUND, "No such catalog: ${request.catalogId}")
            val collection = catalog.getPgCollectionById(conn, request.collectionId.text)
                ?: throw NakshaException(COLLECTION_NOT_FOUND, "No such collection: ${request.collectionId}")
            return if (request.ignoreTransactions || !request.queryHistory) {
                PerFeatureStream(this, request, conn, collection)
            } else {
                PerTransactionStream(this, request, conn, pgStorage.adminCatalog, collection)
            }
        } catch (e: Throwable) {
            conn.close()
            throw e
        }
    }

    override fun write(chunk: StreamChunk): Unit = throw unsupportedOp("write")

    override fun isClosed(): Boolean = closed

    override fun close() {
        closed = true
    }

    /** Reads on the thread calling [next]; chunks may be acknowledged in any order, the oldest unacknowledged position is the recovery point. */
    abstract class PsqlStream(
        session: PsqlStreamSession,
        request: StreamRequest,
        protected val conn: PgConnection,
        protected val collection: PgCollection,
        startPosition: Long,
    ) : Stream(session, request) {

        /** Reads the next page via [add] and moves [readPosition] behind it; returns _false_ when there is nothing more to read. */
        protected abstract fun fetch(): Boolean

        protected abstract fun recoveryRequestAt(position: Long): StreamRequest

        protected open fun versionAt(position: Long): Version = Version(request.minVersion)

        /** The restart position after the chunks read so far. */
        protected var readPosition: Long = startPosition

        private class Pending(val chunk: StreamChunk, val position: Long) {
            var acknowledged = false
        }

        private val defaultTimeout = session.options.stmtTimeout.let { if (it > 0) it.toLong() else 300_000L }
        private val buffer = ArrayDeque<Pending>()
        private var done = false
        private val lock = ReentrantLock()
        private val changed = lock.newCondition()
        private val pending = ArrayDeque<Pending>()
        private var cursor = startPosition
        private var closed = false
        private var error: NakshaError? = null
        private var ackTuples = 0L
        private var unackTuples = 0L
        private var lastRecoveryPosition = Long.MIN_VALUE
        private var lastRecovery: StreamRequest? = null

        protected fun add(chunk: StreamChunk, position: Long) {
            buffer.addLast(Pending(chunk, position))
        }

        private fun read() {
            try {
                while (buffer.isEmpty() && !done) if (!fetch()) done = true
            } catch (e: NakshaException) {
                lock.withLock { fail(e.error) }
            } catch (e: Throwable) {
                lock.withLock { fail(NakshaError(EXCEPTION, e.message ?: e.toString(), e)) }
            }
            lock.withLock { cursor = buffer.firstOrNull()?.position ?: readPosition }
        }

        private fun fail(reason: NakshaError): Nothing {
            if (error == null) error = reason
            val recovery = recoveryRequestLocked()
            shutdownLocked()
            throw StreamException(recovery, this, reason)
        }

        private fun shutdownLocked() {
            if (closed) return
            closed = true
            changed.signalAll()
            conn.close()
        }

        override fun hasNext(): Boolean {
            lock.withLock {
                error?.let { fail(it) }
                if (closed) return false
            }
            if (buffer.isEmpty() && !done) read()
            return buffer.isNotEmpty()
        }

        override fun next(): StreamChunk = next(defaultTimeout) ?: lock.withLock {
            if (closed) throw NakshaException(CLOSED, "Stream closed")
            fail(NakshaError(TIMEOUT, "Timeout waiting for the next chunk"))
        }

        override fun next(timeoutMillis: Long): StreamChunk? {
            if (!hasNext()) throw NoSuchElementException()
            lock.withLock {
                var nanos = TimeUnit.MILLISECONDS.toNanos(maxOf(0L, timeoutMillis))
                while (request.sequential && pending.isNotEmpty() && error == null && !closed) {
                    if (nanos <= 0L) return null
                    nanos = changed.awaitNanos(nanos)
                }
                error?.let { fail(it) }
                if (closed) return null
                val p = buffer.removeFirst()
                pending.addLast(p)
                unackTuples += p.chunk.tuples.size
                cursor = buffer.firstOrNull()?.position ?: readPosition
                return p.chunk
            }
        }

        override fun acknowledge(chunk: StreamChunk) = lock.withLock {
            val p = pending.firstOrNull { it.chunk === chunk } ?: return
            if (p.acknowledged) return
            p.acknowledged = true
            ackTuples += chunk.tuples.size
            unackTuples -= chunk.tuples.size
            while (pending.firstOrNull()?.acknowledged == true) pending.removeFirst()
            changed.signalAll()
        }

        override fun fail(chunk: StreamChunk, reason: NakshaError) = lock.withLock {
            if (error == null) error = reason
            changed.signalAll()
        }

        private fun recoveryPositionLocked(): Long = pending.firstOrNull()?.position ?: cursor

        private fun recoveryRequestLocked(): StreamRequest {
            val position = recoveryPositionLocked()
            val last = lastRecovery
            if (last != null && position == lastRecoveryPosition) return last
            return recoveryRequestAt(position).also {
                lastRecoveryPosition = position
                lastRecovery = it
            }
        }

        private fun awaitAcknowledgedLocked(timeoutMillis: Long): Boolean {
            var nanos = TimeUnit.MILLISECONDS.toNanos(maxOf(0L, timeoutMillis))
            while (pending.isNotEmpty() && error == null) {
                if (nanos <= 0L) return false
                nanos = changed.awaitNanos(nanos)
            }
            return error == null
        }

        override val minVersion: Version get() = lock.withLock { versionAt(recoveryPositionLocked()) }
        override val unacknowledgedChunks: Array<StreamChunk>
            get() = lock.withLock { pending.filter { !it.acknowledged }.map { it.chunk }.toTypedArray() }
        override val acknowledgedChunks: Array<StreamChunk>
            get() = lock.withLock { pending.filter { it.acknowledged }.map { it.chunk }.toTypedArray() }
        override val estimatedChunks: Long get() = -1L
        override val estimatedTuples: Long get() = -1L
        override val acknowledgedTuples: Long get() = lock.withLock { ackTuples }
        override val unacknowledgedTuples: Long get() = lock.withLock { unackTuples }
        override val isRecoverable: Boolean get() = true

        override fun isAcknowledged(timeoutMillis: Long): Boolean = lock.withLock { awaitAcknowledgedLocked(timeoutMillis) }

        override fun isClosed(): Boolean = lock.withLock { closed }

        override fun close() = lock.withLock {
            if (closed) return
            if (!awaitAcknowledgedLocked(defaultTimeout)) fail(error ?: NakshaError(TIMEOUT, "Timeout waiting for acknowledgements"))
            val more = buffer.isNotEmpty() || !done
            val recovery = recoveryRequestLocked()
            shutdownLocked()
            if (more) throw StreamException(recovery, this, NakshaError(CLOSED, "Stream closed with unread chunks"))
        }

        override fun closeForRecovery(): StreamRequest = lock.withLock {
            awaitAcknowledgedLocked(defaultTimeout)
            val recovery = recoveryRequestLocked()
            shutdownLocked()
            recovery
        }

        override fun getRecoveryRequest(): StreamRequest = lock.withLock { recoveryRequestLocked() }

        protected val fn = PgUtil.quoteIdent(FnColumn.name)
        protected val version = PgUtil.quoteIdent(VersionColumn.name)
        protected val head = collection.headTable.quotedName
        protected val hst = collection.historyTable.quotedName

        protected fun selectAll(where: String, orderAndLimit: String): String {
            val cols = PgRows().withCollection(collection).aliases()
            return """SELECT $cols FROM $head WHERE $where
UNION ALL
SELECT $cols FROM $hst WHERE $where
$orderAndLimit"""
        }

        protected fun query(sql: String, types: Array<String>, args: Array<Any?>): List<Tuple> {
            collection.catalog.setSearchPath(conn)
            val rows = PgRows().withCollection(collection)
            conn.prepare(sql, types).execute(args).fetch().use { rows.readAll(it) }
            return (0 until rows.size).mapNotNull { rows[it] }
        }

        protected fun toStreamTuple(t: Tuple): StreamTuple = StreamTuple(
            id = Id(t.id),
            feature = t.toNakshaFeature().platformObject() as PlatformMap,
            tupleNumber = t.tupleNumber,
            nextVersion = t.nextVersion,
            version = t.tupleNumber.version,
        )

        protected fun isDelete(t: Tuple): Boolean = t.tupleNumber.action == Action.DELETE
    }

    class PerFeatureStream(
        session: PsqlStreamSession,
        request: StreamRequest,
        conn: PgConnection,
        collection: PgCollection,
    ) : PsqlStream(session, request, conn, collection, (request.getRaw(CURSOR) as? Number)?.toLong() ?: Long.MIN_VALUE) {

        private val pageSize = maxOf(request.chunkSize, 100) * 4
        private var lastFn = readPosition
        private var lastVersion = Long.MIN_VALUE

        override fun fetch(): Boolean {
            val where = "($fn, $version) > ($1, $2) AND $version >= $3 AND $version <= $4"
            // TODO: queryHistory = false must return the latest state <= request.version, which may live in HISTORY.
            val sql = if (!request.queryHistory) {
                "SELECT ${PgRows().withCollection(collection).aliases()} FROM $head WHERE $where ORDER BY $fn, $version LIMIT $pageSize"
            } else {
                selectAll(where, "ORDER BY $fn, $version LIMIT $pageSize")
            }
            val page = query(sql, arrayOf(INT8, INT8, INT8, INT8), arrayOf(lastFn, lastVersion, request.minVersion, request.version))
            if (page.isEmpty()) return false
            val pageStart = page.first().tupleNumber.featureNumber
            val last = page.last().tupleNumber
            lastFn = last.featureNumber
            lastVersion = last.version

            // Bucket k holds the k-th state of each feature, so no bucket repeats a feature and the history stays in order.
            val buckets = ArrayList<ArrayList<StreamTuple>>()
            val seen = HashMap<Long, Int>()
            for (t in page) {
                // TODO: queryDeleted = false must drop the whole feature when its latest state is a delete.
                if (!request.queryDeleted && isDelete(t)) continue
                val k = seen.merge(t.tupleNumber.featureNumber, 1, Int::plus)!! - 1
                while (buckets.size <= k) buckets.add(ArrayList())
                buckets[k].add(toStreamTuple(t))
            }
            for (bucket in buckets) {
                for (part in bucket.chunked(request.chunkSize)) add(StreamChunk(this, part.toTypedArray()), pageStart)
            }
            val full = page.size == pageSize
            readPosition = if (full) lastFn else lastFn + 1
            return full
        }

        override fun recoveryRequestAt(position: Long): StreamRequest = copyWith(request, CURSOR, position)
    }

    class PerTransactionStream(
        session: PsqlStreamSession,
        request: StreamRequest,
        conn: PgConnection,
        private val admin: PgAdminCatalog,
        collection: PgCollection,
    ) : PsqlStream(session, request, conn, collection, request.minVersion) {

        private val transactions = ArrayDeque<NakshaTx>()
        private var from = readPosition
        private var lastPage = false

        override fun versionAt(position: Long): Version = Version(position)

        override fun fetch(): Boolean {
            while (transactions.isEmpty()) {
                if (lastPage) return false
                loadTransactions()
            }
            val tx = transactions.removeFirst()
            val txVersion = tx.version.number
            add(StreamTransaction(this, txVersion, Id(tx.id), tx, loadVersion(txVersion).toTypedArray()), txVersion)
            readPosition = txVersion + 1
            return transactions.isNotEmpty() || !lastPage
        }

        private fun loadTransactions() {
            val txns = admin.transactions
            // TODO: no index on collection; this scans every transaction in the range.
            val sql = """SELECT ${PgRows().withCollection(txns).aliases()} FROM "naksha~admin".${txns.headTable.quotedName}
WHERE $version >= $1 AND $version <= $2 ORDER BY $version LIMIT $TX_PAGE"""
            admin.setSearchPath(conn)
            val rows = PgRows().withCollection(txns)
            conn.prepare(sql, arrayOf(INT8, INT8)).execute(arrayOf(from, request.version)).fetch().use { rows.readAll(it) }
            for (i in 0 until rows.size) {
                val tx = rows[i]?.toNakshaFeature()?.proxy(NakshaTx::class) ?: continue
                from = tx.version.number + 1
                if (tx.maps[collection.catalog.id]?.collections?.get(collection.id) != null) transactions.addLast(tx)
            }
            if (rows.size < TX_PAGE) lastPage = true
            if (transactions.isEmpty()) readPosition = from
        }

        private fun loadVersion(txVersion: Long): List<StreamTuple> {
            // The lowest two bits of a tuple version hold the action, so match the whole range of the transaction version.
            // TODO: HISTORY has no index on version alone; this is a scan per transaction.
            val base = txVersion and 3L.inv()
            val sql = selectAll("$version >= $1 AND $version <= $2", "ORDER BY $fn")
            return query(sql, arrayOf(INT8, INT8), arrayOf(base, base or 3L))
                .filter { request.queryDeleted || !isDelete(it) }
                .map { toStreamTuple(it) }
        }

        override fun recoveryRequestAt(position: Long): StreamRequest = copyWith(request, "minVersion", position)
    }

    companion object {
        private const val CURSOR = "psqlCursor"
        private const val TX_PAGE = 500
        private val INT8 = PgType.INT64.text

        private fun copyWith(request: StreamRequest, key: String, value: Long): StreamRequest {
            val r = StreamRequest()
            r.putAll(request)
            r[key] = value
            return r
        }
    }
}
