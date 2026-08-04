@file:Suppress("OPT_IN_USAGE")

package naksha.psql

import naksha.base.*
import naksha.base.Id.Id_C.ADMIN_CATALOG_ID
import naksha.base.Id.Id_C.TRANSACTIONS_COL_ID
import naksha.base.Base.BaseCompanion.logger
import naksha.base.Base.BaseCompanion.longToInt64
import naksha.base.Base.BaseCompanion.newAtomicLong
import naksha.model.*
import naksha.base.NakshaError.NakshaErrorCompanion.ILLEGAL_STATE
import naksha.model.objects.NakshaCollection
import naksha.model.objects.NakshaCatalog
import naksha.model.objects.XyzMembers
import naksha.model.objects.XyzProcessors
import naksha.model.request.*
import naksha.model.request.WriteRequest
import naksha.model.objects.NakshaTx
import naksha.model.objects.StandardMembers.StandardMembers_C.FeatureNumberMember
import naksha.model.objects.StandardMembers.StandardMembers_C.VersionMember
import naksha.model.request.WriteOp.WriteOp_C.CREATE
import kotlin.js.JsExport
import kotlin.jvm.JvmField

/**
 * A session linked to a PostgresQL database.
 *
 * This object is created when [IStorage.newReadSession] or [IStorage.newWriteSession] are called. Creating a session is cheap, it will not allocate any database connection until needed.
 * @since 3.0
 */
@JsExport
open class PgSession(
    /**
     * The storage to which the session is bound.
     * @since 3.0
     */
    override val storage: PgStorage,

    /**
     * The session options to use, if any specific.
     * @since 3.0
     */
    options: SessionOptions,

    /**
     * If this session is read-only.
     * @since 3.0
     */
    @JvmField val readOnly: Boolean
) : IWriteSession, IReadSession, ISession {

    companion object PgSession_C {
        private val nextSessionId = newAtomicLong(longToInt64(0L))
        private val ONE = longToInt64(1L)

        // Decodes each 16-byte entry of `$1::bytea[]` into `(fn, version)` bigints (big-endian halves).
        private const val TUPLE_LOOKUP_CTE: String =
            "lookup AS (\n" +
            "  SELECT (\n" +
            "      (get_byte(b,0)::bigint << 56) |\n" +
            "      (get_byte(b,1)::bigint << 48) |\n" +
            "      (get_byte(b,2)::bigint << 40) |\n" +
            "      (get_byte(b,3)::bigint << 32) |\n" +
            "      (get_byte(b,4)::bigint << 24) |\n" +
            "      (get_byte(b,5)::bigint << 16) |\n" +
            "      (get_byte(b,6)::bigint << 8)  |\n" +
            "       get_byte(b,7)::bigint\n" +
            "    ) AS fn, (\n" +
            "      (get_byte(b,8)::bigint  << 56) |\n" +
            "      (get_byte(b,9)::bigint  << 48) |\n" +
            "      (get_byte(b,10)::bigint << 40) |\n" +
            "      (get_byte(b,11)::bigint << 32) |\n" +
            "      (get_byte(b,12)::bigint << 24) |\n" +
            "      (get_byte(b,13)::bigint << 16) |\n" +
            "      (get_byte(b,14)::bigint << 8)  |\n" +
            "       get_byte(b,15)::bigint\n" +
            "    ) AS version\n" +
            "  FROM unnest(\$1::bytea[]) AS t(b)\n" +
            ")"
    }

    /**
     * A unique numerical identifier for the session.
     * @since 3.0
     */
    val id: Int64 = nextSessionId.getAndAdd(ONE)

    /**
     * Assert that this session mutable and open, so **not** closed.
     * - Throws [NakshaError.ILLEGAL_STATE] if this session is [readOnly] or [closed][isClosed].
     * @since 3.0
     */
    fun assertMutable() {
        if (readOnly) throw NakshaException(ILLEGAL_STATE, "Failed to acquire mutable connection from read-only session")
        assertOpen()
    }

    /**
     * Assert that this session is open, so has **not** been closed.
     * @since 3.0
     * @throws NakshaException with [ILLEGAL_STATE] if this session is [closed][isClosed].
     */
    fun assertOpen() {
        if (_closed) throw NakshaException(ILLEGAL_STATE, "Connection closed")
    }

    private var optionsValue: SessionOptions = options

    override val options: SessionOptions
        get() = optionsValue

    override var socketTimeout: Int
        get() = options.socketTimeout
        set(value) {
            optionsValue = options.copy(socketTimeout = value)
        }

    override var stmtTimeout: Int
        get() = options.stmtTimeout
        set(value) {
            optionsValue = options.copy(stmtTimeout = value)
        }

    override var lockTimeout: Int
        get() = options.lockTimeout
        set(value) {
            optionsValue = options.copy(lockTimeout = value)
        }

    /**
     * The PostgresQL database connection currently being used; if any.
     * @since 3.0
     */
    var pgConnection: PgConnection? = null
        private set

    /**
     * Tests if reading in parallel is applicable for this session.
     * @return _true_ if multiple read-connections can be used in parallel for this session; _false_ otherwise.
     * @since 3.0
     */
    val mayReadParallel: Boolean
        get() = pgConnection == null && options.parallel

    /**
     * Opens a new read connection for parallel reading the session.
     *
     * Currently, the main target platform for this method are JVM based languages.
     * - Throws [NakshaError.ILLEGAL_STATE] if the session is [closed][isClosed] or the session [may not be read in parallel][mayReadParallel].
     * @return a new read-only connection for this session, which must be closed when done reading.
     * @since 3.0
     */
    fun newReadConnection(): PgSessionReadConn {
        assertOpen()
        if (!mayReadParallel) throw NakshaException(ILLEGAL_STATE, "Session can't be read in parallel right now")
        return PgSessionReadConn(storage.newConnection(options, readOnly, this::initConnection), true)
    }

    /**
     * Returns a single shared read-connection that must be closed after reading.
     *
     * This implementation returns a wrapped [pgConnection], the usage is like:
     * ```kotlin
     * (
     *   if (session.mayReadParallel)
     *     session.newReadConnection()
     *   else
     *     session.readConnection()
     * ).use {
     *   // use it.conn
     * }
     * ```
     *
     * - Throws [NakshaError.ILLEGAL_STATE] if the session is [closed][isClosed].
     * @return a single shared read-connection.
     * @since 3.0
     */
    fun readConnection(): PgSessionReadConn = PgSessionReadConn(useConnection(), closeUnderlying = false)

    /**
     * Returns a single shared PostgresQL session connection.
     *
     * If none is yet acquired, acquires on from the pools and returns it. This connection is shared and must not be closed, it will automatically be closed when either [rollback] or [commit] are invoked.
     * @return the shared PostgresQL connection.
     * @since 3.0
     */
    fun useConnection(): PgConnection {
        assertOpen()
        var conn = pgConnection
        if (conn == null) {
            conn = storage.newConnection(options, readOnly, this::initConnection)
            pgConnection = conn
        }
        return conn
    }

    /**
     * If a connection is backing this session currently, return the [id][PgConnection.id] of the [connection][PgConnection], otherwise `null`.
     * @since 3.0
     */
    val connectionId: Int64?
        get() = pgConnection?.id

    /**
     * Internally invoked by [useConnection] to initialize the connection.
     * @param conn the connection to initialize.
     * @param query the query to executed, can be modified, when overriding this method.
     * @since 3.0
     */
    protected open fun initConnection(conn: PgConnection, query: String) {
        // This is the same as the default implementation, when init is null, see PgStorage::newConnection
        conn.execute(query).close()
    }

    /**
     * The last [PostgreSQL Error Code](https://www.postgresql.org/docs/current/errcodes-appendix.html) or _null_, if no error has happened.
     * @since 3.0
     */
    var error: PgError? = null
        private set

    /**
     * The current transaction wrapper; if any.
     * @since 3.0
     */
    internal var tx: PgTx? = null
        private set

    private val preparedPartitions = mutableMapOf<PgCollection, MutableSet<Int>>()

    internal fun isPartitionPrepared(collection: PgCollection, partitionNumber: Int): Boolean =
        preparedPartitions[collection]?.contains(partitionNumber) == true

    internal fun markPartitionPrepared(collection: PgCollection, partitionNumber: Int) {
        preparedPartitions.getOrPut(collection) { mutableSetOf() }.add(partitionNumber)
    }

    internal fun snapshotPreparedPartitions(): Map<PgCollection, Set<Int>> =
        preparedPartitions.mapValues { it.value.toSet() }

    internal fun restorePreparedPartitions(snapshot: Map<PgCollection, Set<Int>>) {
        preparedPartitions.clear()
        for ((collection, set) in snapshot) preparedPartitions[collection] = set.toMutableSet()
    }

    private fun promotePreparedPartitions() {
        if (preparedPartitions.isEmpty()) return
        for ((collection, set) in preparedPartitions) {
            val historyTable = collection.historyTable
            for (partitionNumber in set) historyTable.addPartition(partitionNumber)
        }
    }

    /**
     * Return the current transaction, if no transaction started yet, starts a new one.
     * @return the current transaction.
     * @since 3.0
     */
    internal fun useTx(): PgTx {
        assertMutable()
        assertOpen()
        var tx: PgTx? = this.tx
        if (tx == null) {
            val txn = storage.newConnection(options, false, null).use { conn -> storage.adminCatalog.newTxn(conn) }
            tx = PgTx(storage, txn.version, storage.adminCatalog, this)
            this.tx = tx
        }
        return tx
    }

    override fun getTransaction(): NakshaTx? = tx?.nakshaTx

    /**
     * Return the current transaction, if no transaction started yet, starts a new one.
     *
     * - Throws [NakshaError.ILLEGAL_STATE] if this is session is [readOnly] or [closed][isClosed].
     * @return the current transaction.
     */
    override fun useTransaction(): NakshaTx = useTx().nakshaTx

    private var executionCount: Int = 0

    override fun execute(request: Request): Response {
        try {
            val response = when (request) {
                is WriteRequest -> {
                    val writer = PgWriter(this, executionCount++ != 0)
                    writer.execute(request)
                }

                is ReadRequest -> {
                    val reader = PgReader(this, request)
                    val readResponse = reader.execute()
                    if (tx == null) {
                        // If this read was performed on a blank session, without a pending transaction, then we can release the connection.
                        pgConnection?.close()
                        pgConnection = null
                    }
                    readResponse
                }
                else -> throw illegalArg("Unknown request: ${request::class.simpleName}")
            }
            return response
        } catch (t: Throwable) {
            val e = t as? NakshaException ?: PgExceptionMapper.map(t)
            logger.debug(e.message ?: t.message ?: "Exception without message", t)
            return ErrorResponse(e.error)
        }
    }

    /**
     * Reset the session into the initial state.
     */
    private fun clear() {
        error = null
        tx = null
        preparedPartitions.clear()
        try {
            pgConnection?.close()
        } catch (_: Throwable) {
        } finally {
            pgConnection = null
        }
    }

    override fun commit() {
        val conn = pgConnection
        assertOpen()
        assertMutable()
        executionCount = 0
        if (conn != null) {
            val tx = tx
            if (tx != null) {
                try {
                    val transaction = tx.nakshaTx
                    val writeTx = Write()
                        .withOp(CREATE)
                        .withAtomic(true)
                        .withDatabaseId(options.databaseId)
                        .withCatalogId(ADMIN_CATALOG_ID)
                        .withCollectionId(TRANSACTIONS_COL_ID)
                        .withFeature(transaction)
                        .withId(transaction.id)
                        .withAtomic(true)
                    val writeRequest = WriteRequest().add(writeTx)
                    val writer = PgWriter(this, false)
                    writer.execute(writeRequest)
                } catch (t: Throwable) {
                    throw generalException("Failed to save transaction", cause = t)
                } finally {
                    this.tx = null
                }
            }
            try {
                conn.commit()
            } catch (t: Throwable) {
                throw generalException("Failed to commit transaction", cause = t)
            }
            promotePreparedPartitions()
            clear()
        }
    }

    override fun rollback() {
        executionCount = 0
        val conn = pgConnection
        if (conn != null) {
            try {
                conn.rollback()
            } finally {
                clear()
            }
        }
    }

    private var _closed = false

    /**
     * Registered member processors, keyed by member name.
     * Processors are invoked in the order in which they were added.
     *
     * The XYZ metadata processors are registered by default so every write stamps the session-derived members
     * (app_id, author, timestamps); they only fire for collections that declare these members.
     * @since 3.0
     */
    override val processors = MemberProcessorMap()
        .addProcessor(XyzMembers.XyzCreatedAt, XyzProcessors.xyzCreatedAt)
        .addProcessor(XyzMembers.XyzUpdatedAt, XyzProcessors.xyzUpdatedAt)
        .addProcessor(XyzMembers.XyzAppId, XyzProcessors.xyzAppId)
        .addProcessor(XyzMembers.XyzAuthor, XyzProcessors.xyzAuthor)
        .addProcessor(XyzMembers.XyzAuthorTimestamp, XyzProcessors.xyzAuthorTimestamp)
        .addProcessor(XyzMembers.XyzHereTile, XyzProcessors.xyzHereTile)
        .addProcessor(XyzMembers.XyzHash, XyzProcessors.xyzHash)

    override fun isClosed(): Boolean = _closed

    override fun close() {
        if (!_closed) {
            rollback()
            _closed = true
            clear()
        }
    }

    @v30_experimental
    override fun acquireSessionLock(lockId: String): ILock {
        assertOpen()
        return PgLock(this, useConnection(), lockId, true)
    }

    @v30_experimental
    override fun acquireTransactionLock(lockId: String): ILock {
        assertOpen()
        return PgLock(this, useConnection(), lockId, false)
    }

    override fun restoreResultSet(resultSetBytes: ByteArray): ResultSet {
        throw unsupportedOp("ResultSet restoration not yet supported")
    }

    override fun loadTuples(tupleNumbers: ITupleNumberArray, cacheOnly: Boolean): Array<Tuple?> {
        val results = arrayOfNulls<Tuple?>(tupleNumbers.size)
        val load = IntArray(tupleNumbers.size)
        readConnection().use { readConn ->
            val adminCatalog = storage.adminCatalog
            var pending = tupleNumbers.size
            var loadCount = 0
            while (pending > 0) {
                // TODO: We need to support databases.
                var catalog: PgCatalog? = null
                var collection: PgCollection? = null
                for (i in 0 until results.size) {
                    if (load[i] == 0) {
                        // `i` needs loading.
                        val tn: TupleNumber
                        try {
                            tn = tupleNumbers.getTupleNumber(i)
                            if (tn.databaseNumber != storage.defaultDatabaseId.number) throw illegalArg("Invalid database number")
                        } catch (_: Exception) {
                            // We do not load this tuple and treat it as done.
                            load[i] = -1
                            pending--
                            continue
                        }

                        // Try cache first.
                        val tuple = Naksha.cache[tn]
                        if (tuple != null) {
                            results[i] = tuple
                            load[i] = -1
                            pending--
                            continue
                        }

                        if (cacheOnly) {
                            load[i] = -1
                            pending--
                            continue
                        }

                        if (catalog == null) {
                            catalog = adminCatalog.getPgCatalogByNumber(readConn.conn, tn.catalogNumber)
                            if (catalog == null) {
                                load[i] = -1
                                pending--
                                continue
                            }
                        } else if (catalog.id.number.toInt() != tn.catalogNumber) {
                            // We do not load the tuple in this round.
                            continue
                        }
                        if (collection == null) {
                            collection = catalog.getPgCollectionByNumber(readConn.conn, tn.collectionNumber)
                            if (collection == null) {
                                load[i] = -1
                                pending--
                                continue
                            }
                        } else if (collection.id.number.toInt() != tn.collectionNumber) {
                            // We do not load the tuple in this round.
                            continue
                        }
                        // We load the tupe in this round.
                        load[i] = 1
                        loadCount++
                    }
                    if (loadCount > 0) {
                        try {
                            loadTuplesFromCollection(readConn.conn, collection!!, tupleNumbers, load, loadCount, results)
                        } catch (e: Exception) {
                            throw internalError("Failed to load tuples from collection", e)
                        }
                        pending -= loadCount
                    }
                }
            }
        }
        return results
    }

    private fun loadTuplesFromCollection(
        conn: PgConnection,
        collection: PgCollection,
        tupleNumbers: ITupleNumberArray,
        load: IntArray,
        loadCount: Int,
        results: Array<Tuple?>
    ) {
        val HEAD_TABLE = collection.headTable.quotedName
        val HISTORY_TABLE = collection.historyTable.quotedName
        val fn_version_bytes = ByteArray(loadCount * 16)
        val result_index = IntArray(loadCount)
        var row_i = 0
        for (i in 0..< load.size) {
            if (load[i] != 1) continue
            val tn = tupleNumbers.getTupleNumber(i)
            fn_version_bytes.setInt64Be((row_i shl 4), tn.featureNumber)
            fn_version_bytes.setInt64Be((row_i shl 4) + 8, tn.version)
            result_index[row_i++] = i
        }
        if (row_i != result_index.size) throw illegalArg("Found too few tuple-number, expected $loadCount, but got only $row_i")
        val SQL = """
WITH lookup AS (
    SELECT 
        ((get_byte(b, 0) & 255)::bigint << 56) |
        ((get_byte(b, 1) & 255)::bigint << 48) |
        ((get_byte(b, 2) & 255)::bigint << 40) |
        ((get_byte(b, 3) & 255)::bigint << 32) |
        ((get_byte(b, 4) & 255)::bigint << 24) |
        ((get_byte(b, 5) & 255)::bigint << 16) |
        ((get_byte(b, 6) & 255)::bigint << 8)  |
        (get_byte(b, 7) & 255)::bigint        AS $FeatureNumberMember,
        
        ((get_byte(b, 8) & 255)::bigint << 56) |
        ((get_byte(b, 9) & 255)::bigint << 48) |
        ((get_byte(b, 10) & 255)::bigint << 40)|
        ((get_byte(b, 11) & 255)::bigint << 32)|
        ((get_byte(b, 12) & 255)::bigint << 24)|
        ((get_byte(b, 13) & 255)::bigint << 16)|
        ((get_byte(b, 14) & 255)::bigint << 8) |
        (get_byte(b, 15) & 255)::bigint        AS $VersionMember
    FROM (
        SELECT substring($1::bytea FROM g * 16 + 1 FOR 16) AS b
        FROM generate_series(0, octet_length($1::bytea) / 16 - 1) AS g
    ) AS t
), from_head AS (
    SELECT head.* FROM $HEAD_TABLE head
    JOIN lookup l ON (head.$FeatureNumberMember, head.$VersionMember) = (l.$FeatureNumberMember, l.$VersionMember)
), from_hst AS (
    SELECT hst.* FROM $HISTORY_TABLE hst
    JOIN lookup l ON (hst.$FeatureNumberMember, hst.$VersionMember) = (l.$FeatureNumberMember, l.$VersionMember)
)
SELECT * FROM from_head 
UNION ALL 
SELECT * FROM from_hst"""
        collection.catalog.setSearchPath(conn)
        val rows = PgRows().withPgCollection(collection)
        conn.prepare(SQL, arrayOf(PgType.BYTE_ARRAY.string)).use { plan ->
            plan.execute(arrayOf(fn_version_bytes)).fetch().use { cursor ->
                rows.readAll(cursor)
            }
        }
        for (row_i in 0 until rows.size) {
            val tuple = rows[row_i]
            results[result_index[row_i]] = tuple
            if (tuple != null) Naksha.cache.store(tuple)
        }
    }

    override fun getCatalogByNumber(catalogNumber: Int, allowTombstone: Boolean): NakshaCatalog? {
        val catalog = getPgCatalogByNumber(catalogNumber)?.head
        if (allowTombstone || catalog == null) return catalog
        return if (catalog.isDeleted()) null else catalog
    }

    override fun getCollectionByNumber(catalog: NakshaCatalog, collectionNumber: Int, allowTombstone: Boolean): NakshaCollection? {
        val pgCatalog = getPgCatalogByNumber(catalog.id.number.toInt()) ?: return null
        if (pgCatalog.head.isDeleted()) return null
        val collection = getPgCollectionByNumber(pgCatalog, collectionNumber)?.head
        if (allowTombstone || collection == null) return collection
        return if (collection.isDeleted()) null else collection
    }

    /**
     * Returns the [PgCatalog] for the given number.
     * @param catalogNumber the catalog-number.
     * @return the [PgCatalog]; _null_ if the map does not yet exist.
     */
    fun getPgCatalogByNumber(catalogNumber: Int): PgCatalog? {
        val adminCatalog = storage.adminCatalog
        val cachedCatalog = adminCatalog.getPgCatalogByNumber(null, catalogNumber)
        if (cachedCatalog != null) return cachedCatalog
        assertOpen()
        return readConnection().use { adminCatalog.getPgCatalogByNumber(it.conn, catalogNumber) }
    }

    /**
     * Returns the [PgCollection] for the given number.
     * @param pgCatalog the [PgCatalog] in which to search for the collection.
     * @param collectionNumber the collection-number.
     * @return the [PgCollection]; _null_ if the collection does not yet exist.
     */
    fun getPgCollectionByNumber(pgCatalog: PgCatalog, collectionNumber: Int): PgCollection? {
        val cachedCollection = pgCatalog.getPgCollectionByNumber(null, collectionNumber)
        if (cachedCollection != null) return cachedCollection
        assertOpen()
        return readConnection().use { pgCatalog.getPgCollectionByNumber(it.conn, collectionNumber) }
    }

    override fun executeParallel(request: Request): Response = execute(request)

    /**
     * Start time of the session.
     */
    private val start = Base.currentMillis()

    private var _logOptions: SessionOptions? = null

    /**
     * If all queries should be logged.
     * @since 3.0
     */
    internal var logQueries: Boolean = options.logLevel?.contains(PgLogLevel.QUERIES) ?: false
        get() {
            val options = this.options
            if (_logOptions != options) {
                _logOptions = options
                field = options.logLevel?.contains(PgLogLevel.QUERIES) ?: false
            }
            return field
        }
        private set

    /**
     * If all queries should be explained and then the "explain" should be logged.
     * @since 3.0
     */
    internal var logExplain: Boolean = options.logLevel?.contains(PgLogLevel.EXPLAIN) ?: false
        get() {
            val options = this.options
            if (_logOptions != options) {
                _logOptions = options
                field = options.logLevel?.contains(PgLogLevel.EXPLAIN) ?: false
            }
            return field
        }
        private set

    /**
     * Executes an EXPLAIN above the given statement and returns the plain text for logging purpose.
     * @param connection The connection to use to execute to explain.
     * @param verbose If verbose is requested, which means with {@code ANALYZE, BUFFERS}.
     * @param sql The SQL query to explain.
     * @param typeNames The type names, if given the statement will be prepared, which is necessary for queries where type detection fails otherwise.
     * @param args The arguments for the query, same as given to {@code execute}.
     * @return The plain text EXPLAIN above the given statement.
     * @since 11.9.22
     */
    fun explain(connection: PgConnection, verbose: Boolean, sql: String, typeNames: Array<String>?, args: Array<Any?>?) : String {
        val EXPLAIN = (if (verbose) "EXPLAIN (ANALYZE, BUFFERS) " else "EXPLAIN (COSTS false) ") + sql;
        try {
            val c: PgCursor
            if (typeNames == null) {
                c = connection.execute(EXPLAIN, args)
            } else {
                val prepared = connection.prepare(EXPLAIN, typeNames)
                c = prepared.execute(args)
            }
            c.use {
                val sb = StringBuilder()
                while (c.next()) {
                    val map = c.map(PAnyMap::class)
                    for (value in map.values) {
                        sb.append(value).append("\n");
                    }
                }
                return sb.toString();
            }
        } catch (e: Exception) {
            val msg = "Failed to execute 'EXPLAIN $sql'"
            logger.error(msg, e.message)
            return msg
        }
    }

    /**
     * Log some SQL as debug message for a connection.
     * @param sql The message to log.
     * @param args Arguments for placeholders ({@code {}}) within the given message.
     * @since 11.9.22
     */
    fun logAtInfo(sql: String, vararg args: Any?) {
        if (BaseUtil.ENABLE_INFO) {
            val delta = Base.currentMillis() - start
            logger.info("{}@{}:{}ms: $sql", id, connectionId, delta, *args)
        }
    }
}
