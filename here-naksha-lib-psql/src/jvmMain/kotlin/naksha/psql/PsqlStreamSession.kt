package naksha.psql

import naksha.base.Action
import naksha.base.Id
import naksha.base.NakshaError.NakshaErrorCompanion.COLLECTION_NOT_FOUND
import naksha.base.NakshaException
import naksha.base.PlatformMap
import naksha.base.Version
import naksha.model.SessionOptions
import naksha.model.Tuple
import naksha.model.objects.NakshaTx
import naksha.model.streaming.AbstractStreamSession
import naksha.model.streaming.QueueStream
import naksha.model.streaming.Stream
import naksha.model.streaming.StreamChunk
import naksha.model.streaming.StreamRequest
import naksha.model.streaming.StreamTransaction
import naksha.model.streaming.StreamTuple
import naksha.psql.PgColumn.PgColumn_C.FnColumn
import naksha.psql.PgColumn.PgColumn_C.VersionColumn

class PsqlStreamSession(
    private val pgStorage: PsqlStorage,
    options: SessionOptions,
) : AbstractStreamSession(pgStorage, options) {

    override val mayRead: Boolean get() = true

    override fun read(request: StreamRequest): Stream {
        val conn = pgStorage.newConnection(options, true)
        try {
            val catalog = pgStorage.adminCatalog.getPgCatalogById(conn, request.catalogId.text)
                ?: throw NakshaException(COLLECTION_NOT_FOUND, "No such catalog: ${request.catalogId}")
            val collection = catalog.getPgCollectionById(conn, request.collectionId.text)
                ?: throw NakshaException(COLLECTION_NOT_FOUND, "No such collection: ${request.collectionId}")
            return if (request.ignoreTransactions || !request.queryHistory) {
                PerFeatureStream(this, request, options, conn, collection)
            } else {
                PerTransactionStream(this, request, options, conn, pgStorage.adminCatalog, collection)
            }
        } catch (e: Throwable) {
            conn.close()
            throw e
        }
    }

    abstract class PsqlStream(
        session: PsqlStreamSession,
        request: StreamRequest,
        options: SessionOptions,
        protected val conn: PgConnection,
        protected val collection: PgCollection,
    ) : QueueStream(session, request, options, if (request.sequential) 1 else 16) {

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

        override fun close() {
            try {
                super.close()
            } finally {
                conn.close()
            }
        }

        override fun closeForRecovery(): StreamRequest = try {
            super.closeForRecovery()
        } finally {
            conn.close()
        }
    }

    class PerFeatureStream(
        session: PsqlStreamSession,
        request: StreamRequest,
        options: SessionOptions,
        conn: PgConnection,
        collection: PgCollection,
    ) : PsqlStream(session, request, options, conn, collection) {

        override val startPosition: Long = (request.getRaw(CURSOR) as? Number)?.toLong() ?: Long.MIN_VALUE

        init {
            start()
        }

        override fun produce(sink: Sink) {
            val pageSize = maxOf(request.chunkSize, 100) * 4
            var lastFn = startPosition
            var lastVersion = Long.MIN_VALUE
            // TODO: queryHistory = false must return the latest state <= request.version, which may live in HISTORY.
            val headOnly = !request.queryHistory
            while (true) {
                val where = "($fn, $version) > ($1, $2) AND $version >= $3 AND $version <= $4"
                val sql = if (headOnly) {
                    "SELECT ${PgRows().withCollection(collection).aliases()} FROM $head WHERE $where ORDER BY $fn, $version LIMIT $pageSize"
                } else {
                    selectAll(where, "ORDER BY $fn, $version LIMIT $pageSize")
                }
                val page = query(sql, arrayOf(INT8, INT8, INT8, INT8),
                    arrayOf(lastFn, lastVersion, request.minVersion, request.version))
                if (page.isEmpty()) break
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
                val next = if (page.size < pageSize) lastFn + 1 else lastFn
                for (bucket in buckets) {
                    for (part in bucket.chunked(request.chunkSize)) {
                        if (!sink.emit(StreamChunk(this, part.toTypedArray()), pageStart, next)) return
                    }
                }
                if (page.size < pageSize) break
            }
        }

        override fun recoveryRequestAt(position: Long): StreamRequest = copyWith(request, CURSOR, position)
    }

    class PerTransactionStream(
        session: PsqlStreamSession,
        request: StreamRequest,
        options: SessionOptions,
        conn: PgConnection,
        private val admin: PgAdminCatalog,
        collection: PgCollection,
    ) : PsqlStream(session, request, options, conn, collection) {

        override val startPosition: Long = request.minVersion

        init {
            start()
        }

        override fun versionAt(position: Long): Version = Version(position)

        override fun produce(sink: Sink) {
            val txns = admin.transactions
            val cols = PgRows().withCollection(txns).aliases()
            var from = startPosition
            while (true) {
                // TODO: no index on collection; this scans every transaction in the range.
                val sql = """SELECT $cols FROM "naksha~admin".${txns.headTable.quotedName}
WHERE $version >= $1 AND $version <= $2 ORDER BY $version LIMIT 500"""
                admin.setSearchPath(conn)
                val rows = PgRows().withCollection(txns)
                conn.prepare(sql, arrayOf(INT8, INT8)).execute(arrayOf(from, request.version)).fetch().use { rows.readAll(it) }
                if (rows.size == 0) break
                for (i in 0 until rows.size) {
                    val t = rows[i] ?: continue
                    val tx = t.toNakshaFeature().proxy(NakshaTx::class)
                    val txVersion = tx.version.number
                    from = txVersion + 1
                    val touched = tx.maps[collection.catalog.id]?.collections?.get(collection.id) != null
                    if (!touched) continue
                    val tuples = loadVersion(txVersion)
                    val chunk = StreamTransaction(this, txVersion, Id(tx.id), tx, tuples.toTypedArray())
                    if (!sink.emit(chunk, txVersion, txVersion + 1)) return
                }
                if (rows.size < 500) break
            }
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
        private val INT8 = PgType.INT64.text

        private fun copyWith(request: StreamRequest, key: String, value: Long): StreamRequest {
            val r = StreamRequest()
            r.putAll(request)
            r[key] = value
            return r
        }
    }
}
