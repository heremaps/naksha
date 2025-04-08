@file:Suppress("OPT_IN_USAGE")

package naksha.psql

import naksha.base.*
import naksha.model.*
import naksha.model.NakshaError.NakshaErrorCompanion.ILLEGAL_STATE
import naksha.model.objects.NakshaCollection
import naksha.model.objects.NakshaMap
import naksha.model.request.*
import naksha.model.request.WriteRequest
import naksha.model.objects.NakshaTx
import kotlin.js.JsExport
import kotlin.jvm.JvmField
import kotlin.math.min

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
     * Assert that this session mutable.
     * - Throws [NakshaError.ILLEGAL_STATE] if this session is [closed][isClosed].
     * @since 3.0
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
     * Internally invoked by [useConnection] to initialize the connection.
     * @param conn the connection to initialize.
     * @param query the query to executed, can be modified, when overriding this method.
     * @since 3.0
     */
    protected open fun initConnection(conn: PgConnection, query: String) {
        // This is the same as the default implementation, when init is null, see PgStorage::newConnection
        conn.execute(query).close()
    }

    override val uid: AtomicInt = AtomicInt(0)

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
    internal var tx: StorageTx? = null
        private set

    /**
     * Return the current transaction, if no transaction started yet, starts a new one.
     * @return the current transaction.
     * @since 3.0
     */
    internal fun useTx(): StorageTx {
        assertMutable()
        assertOpen()
        var tx: StorageTx? = this.tx
        if (tx == null) {
            val txn = storage.newConnection(options, false, null).use { conn -> storage.adminMap.newTxn(conn) }
            tx = StorageTx(storage, txn.version, options.appId, options.author, storage.adminMap)
            this.tx = tx
        }
        return tx
    }

    override fun getTransaction(): NakshaTx? = tx?.transaction

    /**
     * Return the current transaction, if no transaction started yet, starts a new one.
     *
     * - Throws [NakshaError.ILLEGAL_STATE] if this is session is [readOnly] or [closed][isClosed].
     * @return the current transaction.
     */
    override fun useTransaction(): NakshaTx = useTx().transaction

    private var executionCount: Int = 0

    override fun execute(request: Request): Response {
        try {
            when (request) {
                is WriteRequest -> {
                    val writer = PgWriter(this, executionCount++ != 0)
                    return writer.execute(request.writes)
                }

                is ReadRequest -> {
                    val reader = PgReader(this, request)
                    val response = reader.execute()
                    if (tx == null) {
                        // If this read was performed on a blank session, without a pending transaction, then we can release the connection.
                        pgConnection?.close()
                        pgConnection = null
                    }
                    return response
                }

                else -> throw illegalArg("Unknown request: ${request::class.simpleName}")
            }
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
        uid.set(0)
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
                    val transaction = tx.transaction
                    val writeTx = Write().createFeature(Naksha.ADMIN_MAP, TRANSACTIONS_COL, transaction)
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

    override fun loadTuples(featureTuples: List<FeatureTuple?>) = loadTuples(featureTuples, 0, featureTuples.size, FETCH_ALL)

    override fun loadTuples(featureTuples: List<FeatureTuple?>, from: Int, to: Int, mode: FetchMode) {
        val missing = featureTuples.subList(from, to).mapNotNull { if (it != null && it.tuple == null) it else null }
        if (missing.isNotEmpty()) {
            (if (mayReadParallel) newReadConnection() else readConnection()).use { readConn ->
                val conn = readConn.conn
                val byCollection = mutableMapOf<String, MutableList<PgRead>>()
                val adminMap = storage.adminMap
                for (featureTuple in missing) {
                    val read = PgRead(conn, adminMap, featureTuple.tupleNumber)
                    read.featureTuple = featureTuple
                    var reads = byCollection[read.groupId]
                    if (reads == null) {
                        reads = ArrayList(min(1000, missing.size))
                        byCollection[read.groupId] = reads
                    }
                    reads.add(read)
                }
                for (entry in byCollection) {
                    loadTuplesFromCollection(conn, entry.value, mode)
                }
            }
        }
    }

    /**
     * Load [Tuple] from a specific collection, can be executed in parallel, when multiple collections are needed. We should make parallel reading optional, we experienced that when used for example in EMR, too many connections can harm. However, the cache could keep objects in Redis or alike, and then read perfectly fine in parallel!
     *
     * @param conn the connection to use for this read.
     * @param reads the reads to perform.
     * @param mode the load-mode
     */
    private fun loadTuplesFromCollection(conn: PgConnection, reads: List<PgRead>, mode: FetchMode) {
        // TODO: We can improve this to load the results as GZIP compressed binary!
        //       Read BINARY.md for more information.
        //       For the sake of delivery, we take the shortcut, and only us ARRAY_AGG
        //       Maybe this is already fast enough?
        if (reads.isEmpty()) throw illegalState("Reads must not be empty")
        val first = reads.first()
        val map = first.map
        val collection = first.collection
        val rows = PgColumnRows()
            .withStorageNumber(map.storage.number)
            .withMapNumber(map.number)
            .withCollectionNumber(collection.number)
            .addColumns(PgColumn.allColumns)
        map.setSearchPath(conn)
        val headTables = first.headTables
        val historyTables = first.historyTables
        val sql = StringBuilder()
        sql.append("WITH result AS(\n")
        var unionAll = false
        val rowNames = rows.names()
        for (headTable in headTables) {
            if (unionAll) sql.append("\tUNION ALL\n") else unionAll = true
            sql.append("SELECT ").append(rowNames).append(" FROM ").append(headTable.quotedName).append(" WHERE tn = ANY(\$1)\n")
        }
        if (historyTables != null) {
            for (hstTable in historyTables) {
                if (unionAll) sql.append("\tUNION ALL\n") else unionAll = true
                sql.append("SELECT ").append(rowNames).append(" FROM ").append(hstTable.quotedName).append(" WHERE tn = ANY(\$1)\n")
            }
        } else {
            val shadowTables = first.shadowTables
            if (shadowTables != null) {
                for (shadowTable in shadowTables) {
                    if (unionAll) sql.append("\tUNION ALL\n") else unionAll = true
                    sql.append("SELECT ").append(rowNames).append(" FROM ").append(shadowTable.quotedName).append(" WHERE tn = ANY(\$1)\n")
                }
            }
        }
        sql.append(")\nSELECT ").append(rows.namesAggregate()).append(" FROM result")
        val SQL = sql.toString()
        val tupleNumbers: Array<Any?> = reads.map { it.tupleNumber!!.toB160() }.toTypedArray()
        conn.prepare(SQL, arrayOf(PgType.BYTE_ARRAY_ARRAY.text)).use { plan ->
            plan.execute(arrayOf(tupleNumbers)).fetch().use { cursor ->
                rows.addAggregated(cursor)
            }
        }
        for (i in 0 until rows.size) {
            val tuple = rows[i] ?: continue
            Naksha.cache.store(tuple)
            val tupleNumber = tuple.tupleNumber
            for (read in reads) {
                if (read.tupleNumber == tupleNumber) {
                    read.featureTuple?.tuple = tuple
                    break
                }
            }
        }
    }

    override fun getMapById(mapId: String): NakshaMap? {
        assertOpen()
        return (if (mayReadParallel) newReadConnection() else readConnection()).use {
            storage.adminMap.getPgMapById(it.conn, mapId)?.head
        }
    }

    /**
     * Returns the [PgMap] for the given id.
     * @param mapId the map-id.
     * @return the [PgMap]; _null_ if the map does not yet exist.
     */
    fun getPgMapById(mapId: String): PgMap? {
        assertOpen()
        return (if (mayReadParallel) newReadConnection() else readConnection()).use {
            storage.adminMap.getPgMapById(it.conn, mapId)
        }
    }

    override fun getMapByNumber(mapNumber: Int): NakshaMap? {
        assertOpen()
        return (if (mayReadParallel) newReadConnection() else readConnection()).use {
            storage.adminMap.getPgMapByNumber(it.conn, mapNumber)?.head
        }
    }

    /**
     * Returns the [PgMap] for the given number.
     * @param mapNumber the map-number.
     * @return the [PgMap]; _null_ if the map does not yet exist.
     */
    fun getPgMapByNumber(mapNumber: Int): PgMap? {
        assertOpen()
        return (if (mayReadParallel) newReadConnection() else readConnection()).use {
            storage.adminMap.getPgMapByNumber(it.conn, mapNumber)
        }
    }

    override fun getCollectionById(map: NakshaMap, collectionId: String): NakshaCollection? {
        assertOpen()
        return (if (mayReadParallel) newReadConnection() else readConnection()).use {
            val pgMap = storage.adminMap.getPgMapById(it.conn, map.id) ?: return null
            pgMap.getPgCollectionById(it.conn, collectionId)?.head
        }
    }

    /**
     * Returns the [PgCollection] for the given id.
     * @param pgMap the [PgMap] in which to search for the collection.
     * @param collectionId the collection-id.
     * @return the [PgCollection]; _null_ if the collection does not yet exist.
     */
    fun getPgCollectionById(pgMap: PgMap, collectionId: String): PgCollection? {
        assertOpen()
        return (if (mayReadParallel) newReadConnection() else readConnection()).use {
            pgMap.getPgCollectionById(it.conn, collectionId)
        }
    }

    override fun getCollectionByNumber(map: NakshaMap, collectionNumber: Int): NakshaCollection? {
        assertOpen()
        return (if (mayReadParallel) newReadConnection() else readConnection()).use {
            val pgMap = storage.adminMap.getPgMapById(it.conn, map.id) ?: return null
            pgMap.getPgCollectionByNumber(it.conn, collectionNumber)?.head
        }
    }

    /**
     * Returns the [PgCollection] for the given number.
     * @param pgMap the [PgMap] in which to search for the collection.
     * @param collectionNumber the collection-number.
     * @return the [PgCollection]; _null_ if the collection does not yet exist.
     */
    fun getPgCollectionByNumber(pgMap: PgMap, collectionNumber: Int): PgCollection? {
        assertOpen()
        return (if (mayReadParallel) newReadConnection() else readConnection()).use {
            pgMap.getPgCollectionByNumber(it.conn, collectionNumber)
        }
    }

    override fun executeParallel(request: Request): Response = execute(request)
}
