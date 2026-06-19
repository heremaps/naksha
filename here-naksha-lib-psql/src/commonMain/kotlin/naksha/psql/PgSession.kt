@file:Suppress("OPT_IN_USAGE")

package naksha.psql

import naksha.base.*
import naksha.base.Platform.PlatformCompanion.logger
import naksha.base.Platform.PlatformCompanion.longToInt64
import naksha.base.Platform.PlatformCompanion.newAtomicInt64
import naksha.base.PlatformDataViewApi.PlatformDataViewApiCompanion.dataview_set_int64
import naksha.model.*
import naksha.model.NakshaError.NakshaErrorCompanion.ILLEGAL_STATE
import naksha.model.NakshaError.NakshaErrorCompanion.INTERNAL_ERROR
import naksha.model.objects.NakshaCollection
import naksha.model.objects.NakshaCatalog
import naksha.model.objects.NakshaFeature
import naksha.model.request.*
import naksha.model.request.WriteRequest
import naksha.model.objects.NakshaTx
import kotlin.js.JsExport
import kotlin.jvm.JvmField

/**
 * A session linked to a PostgresQL database.
 *
 * This object is created when [IStorage.newReadSession] or [IStorage.newWriteSession] are called, creating a session is cheap to create, without pre-allocation of any database connection, it will be connected on demand.
 * @since 3.0
 */
@JsExport
open class PgSession(
    /**
     * The storage to which the session is bound.
     * @since 3.0
     */
    pgStorage: PgStorage,

    /**
     * The session options to use, if any specific.
     * @since 3.0
     */
    options: SessionOptions?,

    /**
     * If this session is read-only.
     * @since 3.0
     */
    @JvmField val readOnly: Boolean
) : IWriteSession, IReadSession, ISession {

    companion object PgSession_C {
        private val nextSessionId = newAtomicInt64(longToInt64(0L))
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

    override val storage = pgStorage

    /**
     * Assert that this session mutable and not closed.
     * - Throws [NakshaError.ILLEGAL_STATE] if this session is [readOnly] or [closed][isClosed].
     * @since 3.0
     */
    fun assertMutable() {
        if (readOnly) throw NakshaException(ILLEGAL_STATE, "Failed to acquire mutable connection from read-only session")
        assertOpen()
    }

    /**
     * Assert that this session is closed.
     * @since 3.0
     * @throws NakshaException with [ILLEGAL_STATE] if this session is [closed][isClosed].
     */
    fun assertOpen() {
        if (_closed) throw NakshaException(ILLEGAL_STATE, "Connection closed")
    }

    private var optionsValue: SessionOptions = options ?: SessionOptions()

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
            tx = PgTx(storage, txn.version, options.appId, options.author, storage.adminCatalog, this)
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
                    writer.execute(request.writes)
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
            if (response is SuccessResponse) {
                response.filterResults(*request.resultFilters.filterNotNull().toTypedArray())
            }
            return response
        } catch (t: Throwable) {
            val nakshaException = PgExceptionMapper.map(t)
            nakshaException.error.print()
            return ErrorResponse(nakshaException.error)
        }
    }

    /**
     * Reset the session into the initial state.
     */
    private fun clear() {
        error = null
        tx = null
        try {
            pgConnection?.close()
        } catch (ignore: Throwable) {
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
                    val writeTx = Write().createFeature(Naksha.ADMIN_CATALOG_ID, TRANSACTIONS_COL, transaction)
                    val writeRequest = WriteRequest().add(writeTx)
                    // TODO: Should we use a savepoint here?
                    val writer = PgWriter(this, false)
                    writer.execute(writeRequest.writes)
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
     * @since 3.0
     */
    override val processors = MemberProcessorMap()

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

    // TODO: We should only have one method being this one!
    override fun loadTuples(featureTuples: List<FeatureTuple?>) = loadTuples(featureTuples, 0, featureTuples.size, FETCH_ALL)

    override fun loadTuples(featureTuples: List<FeatureTuple?>, from: Int, to: Int, mode: FetchMode) {
        val missing = featureTuples.subList(from, to).mapNotNull { if (it != null && it.tuple == null) it else null }
        if (missing.isNotEmpty()) {
            (if (mayReadParallel) newReadConnection() else readConnection()).use { readConn ->
                val conn = readConn.conn
                val adminCatalog = storage.adminCatalog
                val byCollection: MutableMap<PgCollection, FeatureTupleList> = mutableMapOf()
                for (featureTuple in missing) {
                    val tn = featureTuple.tupleNumber
                    val collection: PgCollection = adminCatalog.getPgCollectionByNumber(conn, tn.collectionNumber) ?: continue
                    var list = byCollection[collection]
                    if (list == null) {
                        list = FeatureTupleList()
                        byCollection[collection] = list
                    }
                    list.add(featureTuple)
                }
                for ((collection, featureTuples) in byCollection) {
                    loadTuplesFromCollection(conn, collection, featureTuples)
                }
            }
        }
    }

    /**
     * Load [Tuple] from a specific collection, can be executed in parallel, when multiple collections are needed. We should make parallel reading optional, we experienced that when used for example in EMR, too many connections can harm. However, the cache could keep objects in Redis or alike, and then read perfectly fine in parallel!
     *
     * @param conn the connection to use for this read.
     * @param collection the collection to read.
     * @param featureTuples the tuple to load.
     */
    private fun loadTuplesFromCollection(conn: PgConnection, collection: PgCollection, featureTuples: FeatureTupleList): Int {
        if (featureTuples.isEmpty()) return 0
        val HEAD_TABLE = collection.headTable.quotedName
        val HISTORY_TABLE = collection.historyTable.quotedName
        // Generate input array
        val fn_version_bytes = ByteArray(featureTuples.size * 16)
        val view = Platform.newDataView(fn_version_bytes)
        for (i in 0..< featureTuples.size) {
            val tn = featureTuples[i]?.tupleNumber ?: throw NakshaException(INTERNAL_ERROR, "featureTuples[$i] is null")
            // Note: We do the math by intention. The CPU is very good at math, it's basically free.
            //       However, memory access is really slow, by doing it this way, the CPU can reorder
            //       the memory access, and the JIT can unroll the loop.
            dataview_set_int64(view, i*16, tn.featureNumber)
            dataview_set_int64(view, i*16 + 8, tn.version)
        }
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
        (get_byte(b, 7) & 255)::bigint        AS fn,
        
        ((get_byte(b, 8) & 255)::bigint << 56) |
        ((get_byte(b, 9) & 255)::bigint << 48) |
        ((get_byte(b, 10) & 255)::bigint << 40)|
        ((get_byte(b, 11) & 255)::bigint << 32)|
        ((get_byte(b, 12) & 255)::bigint << 24)|
        ((get_byte(b, 13) & 255)::bigint << 16)|
        ((get_byte(b, 14) & 255)::bigint << 8) |
        (get_byte(b, 15) & 255)::bigint        AS version
    FROM unnest($1::bytea[]) AS t(b)
), from_head AS (
    SELECT head.* FROM $HEAD_TABLE head 
    JOIN lookup l ON (head.fn, head.version) = (l.fn, l.version)
), from_hst AS (
    SELECT hst.* FROM $HISTORY_TABLE hst 
    JOIN lookup l ON (hst.fn, hst.version) = (l.fn, l.version)
)
SELECT * FROM from_head 
UNION ALL 
SELECT * FROM from_hst"""
        collection.catalog.setSearchPath(conn)
        val rows = PgRows().withCollection(collection)
        conn.prepare(SQL, arrayOf(PgType.BYTE_ARRAY_ARRAY.text)).use { plan ->
            plan.execute(arrayOf(fn_version_bytes)).fetch().use { cursor ->
                rows.readAll(cursor)
            }
        }
        // Copy tuples into cache, which allows us in the next loop to read the tuple back from the cache.
        for (i in 0 until rows.size) {
            val tuple = rows[i] ?: continue
            Naksha.cache.store(tuple)
        }
        var found = 0
        for (i in 0..< featureTuples.size) {
            val featureTuple = featureTuples[i] ?: throw NakshaException(INTERNAL_ERROR, "featureTuples[$i] is null")
            val tn = featureTuple.tupleNumber
            val tuple = Naksha.cache[tn]
            if (tuple != null) {
                // TODO: We need a cache for global books!
                //       We can simply say that the global books need to be committed, before they can be used.
                //       Additionally, they are immutable, onces stored, they can not be deleted nor mutated.
                //       Therefore, if a
                val feature: NakshaFeature = tuple.decodeFeature(null)
                featureTuple.feature = feature
                if (featureTuple.id != feature.id) {
                    // This must not happen!
                    val tn = featureTuple.tupleNumber
                    throw NakshaException(
                        INTERNAL_ERROR,
                        "The `id` of feature-tuple '${feature.id}' does not match that of the loaded tuple: ${feature.id}, feature-number: '${tn.featureNumber}', version: ${tn.version}, collection: ${collection.id}"
                    )
                }
                found++
            }
        }
        return found
    }

    override fun getCatalogById(catalogId: String): NakshaCatalog? = getPgCatalogById(catalogId)?.head

    /**
     * Returns the [PgCatalog] for the given id.
     * @param catalogId the catalog-id.
     * @return the [PgCatalog]; _null_ if the map does not yet exist.
     */
    fun getPgCatalogById(catalogId: String): PgCatalog? {
        val adminCatalog = storage.adminCatalog
        val cachedCatalog = adminCatalog.getPgCatalogById(null, catalogId)
        if (cachedCatalog != null) return cachedCatalog
        assertOpen()
        return (if (mayReadParallel) newReadConnection() else readConnection()).use {
            adminCatalog.getPgCatalogById(it.conn, catalogId)
        }
    }

    override fun getCatalogByNumber(catalogNumber: Int): NakshaCatalog? = getPgCatalogByNumber(catalogNumber)?.head

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
        return (if (mayReadParallel) newReadConnection() else readConnection()).use {
            adminCatalog.getPgCatalogByNumber(it.conn, catalogNumber)
        }
    }

    override fun getCollectionById(catalog: NakshaCatalog, collectionId: String): NakshaCollection? {
        val pgCatalog = getPgCatalogById(catalog.id) ?: return null
        return getPgCollectionById(pgCatalog, collectionId)?.head
    }

    /**
     * Returns the [PgCollection] for the given id.
     * @param pgCatalog the [PgCatalog] in which to search for the collection.
     * @param collectionId the collection-id.
     * @return the [PgCollection]; _null_ if the collection does not yet exist.
     */
    fun getPgCollectionById(pgCatalog: PgCatalog, collectionId: String): PgCollection? {
        val cachedCollection = pgCatalog.getPgCollectionById(null, collectionId)
        if (cachedCollection != null) return cachedCollection
        assertOpen()
        return (if (mayReadParallel) newReadConnection() else readConnection()).use {
            pgCatalog.getPgCollectionById(it.conn, collectionId)
        }
    }

    override fun getCollectionByNumber(catalog: NakshaCatalog, collectionNumber: Int): NakshaCollection? {
        val pgCatalog = getPgCatalogById(catalog.id) ?: return null
        return getPgCollectionByNumber(pgCatalog, collectionNumber)?.head
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
        return (if (mayReadParallel) newReadConnection() else readConnection()).use {
            pgCatalog.getPgCollectionByNumber(it.conn, collectionNumber)
        }
    }

    override fun executeParallel(request: Request): Response = execute(request)

    /**
     * Start time of the session.
     */
    private val start = Platform.currentMillis()

    private var _logOptions: SessionOptions? = null

    /**
     * If all queries should be logged.
     * @since 3.0
     */
    internal var logQueries: Boolean = options?.logLevel?.contains(PgLogLevel.QUERIES) ?: false
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
    internal var logExplain: Boolean = options?.logLevel?.contains(PgLogLevel.EXPLAIN) ?: false
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
                    val map = c.map(AnyObject::class)
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
        if (PlatformUtil.ENABLE_INFO) {
            val delta = Platform.currentMillis() - start
            logger.info("{}@{}:{}ms: $sql", id, connectionId, delta, *args)
        }
    }
}
