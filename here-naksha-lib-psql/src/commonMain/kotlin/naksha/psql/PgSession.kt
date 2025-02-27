@file:Suppress("OPT_IN_USAGE")

package naksha.psql

import naksha.base.*
import naksha.jbon.JbDictionary
import naksha.model.*
import naksha.model.NakshaError.NakshaErrorCompanion.EXCEPTION
import naksha.model.NakshaError.NakshaErrorCompanion.ILLEGAL_ARGUMENT
import naksha.model.NakshaError.NakshaErrorCompanion.ILLEGAL_STATE
import naksha.model.objects.NakshaCollection
import naksha.model.objects.NakshaMap
import naksha.model.request.*
import naksha.model.request.WriteRequest
import naksha.model.objects.NakshaTx
import naksha.psql.executors.PgReader
import naksha.psql.executors.PgWriter
import naksha.psql.executors.write.InstantWriteExecutor
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
        var tx = this.tx
        if (tx == null) {
            val txn = storage.adminMap.newTxn(useConnection())
            tx = StorageTx(Version(txn.number), options.appId, options.author, storage.adminMap)
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

    override fun execute(request: Request): Response {
        when (request) {
            is WriteRequest -> {
                try {
                    useTransaction()
                    val writer = PgTupleWriter(this)
                    return writer.execute(request.writes)
                } catch (t: Throwable) {
                    val nakshaException = PgExceptionMapper.map(t)
                    nakshaException.error.print()
                    return ErrorResponse(nakshaException.error)
                }
            }

            is ReadRequest -> {
                val response = PgReader(this, request).execute()
                if (tx == null) {
                    // If this read was performed on a blank session, without a pending transaction, then we can release the connection.
                    pgConnection?.close()
                    pgConnection = null
                }
                return response
            }

            else -> throw NakshaException(ILLEGAL_ARGUMENT, "Unknown request")
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
        if (conn != null) {
            val tx = tx
            if (tx != null) {
                try {
                    val writeTxReq = WriteRequest()
                    val writeTx = Write()
                    writeTxReq.add(writeTx)
                    //writeTx.createFeature(null, TRANSACTIONS_COL, useTransaction())
                    PgWriter(this, writeTxReq, InstantWriteExecutor(this)).execute()
                } catch (e: Throwable) {
                    throw NakshaException(EXCEPTION, "Failed to save transaction", cause = e)
                }
            }
            try {
                conn.commit()
            } catch (e: Throwable) {
                throw NakshaException(EXCEPTION, "Failed to commit", cause = e)
            }
            clear()
        }
    }

    override fun rollback() {
        val conn = pgConnection
        assertOpen()
        assertMutable()
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

    override fun loadTuples(featureTuples: List<FeatureTuple?>, from: Int, to: Int, mode: FetchMode) {
        // We are the storage, do not load from us ;-)
        Naksha.cache.load(featureTuples, from, to, loadFromStorage = false)
        val missing = featureTuples.subList(from, to).mapNotNull { if (it != null && it.tuple == null) it else null }
        if (missing.isNotEmpty()) {
            (if (mayReadParallel) newReadConnection() else readConnection()).use { readConn ->
                val conn = readConn.conn
                val byCollection = missing.groupBy {
                    val pgMap = storage.adminMap.getPgMapByNumber(conn, it.tupleNumber.mapNumber) ?: return@groupBy "NULL"
                    pgMap.getPgCollectionByNumber(conn, it.tupleNumber.collectionNumber) ?: return@groupBy "NULL"
                }
                for (entry in byCollection) {
                    val key = entry.key
                    val pgCollection = if (key is PgCollection) key else continue
                    val tupleFeatures = entry.value
                    loadTuplesFromCollection(conn, pgCollection, tupleFeatures, mode)
                }
            }
        }
    }

    /**
     * Load [Tuple] from a specific collection, can be executed in parallel, when multiple collections are needed. We should make parallel reading optional, we experienced that when used for example in EMR, too many connections can harm. However, the cache could keep objects in Redis or alike, and then read perfectly fine in parallel!
     *
     * @param conn the connection to use for this read.
     * @param pgCollection the collection to read from.
     * @param tupleFeatures the features to load from this collection (pre-filtered).
     * @param mode the load-mode
     */
    private fun loadTuplesFromCollection(conn: PgConnection, pgCollection: PgCollection, tupleFeatures: List<FeatureTuple>, mode: FetchMode) {
        // TODO: We can improve this to load the results as GZIP compressed binary!
        //       Read BINARY.md for more information.
        //       For the sake of delivery, we take the shortcut, and only us ARRAY_AGG
        //       Maybe this is already fast enough?
        val rows = PgColumnRows()
            .withStorageNumber(pgCollection.storage.number)
            .withMapNumber(pgCollection.map.number)
            .withCollectionNumber(pgCollection.number)
            .addColumns(PgColumn.allColumns)
        pgCollection.map.setSearchPath(conn)
        val historyTable = pgCollection.historyTable
        var SQL = "SELECT ${rows.namesAggregate()} FROM ${pgCollection.headTable.quotedName} WHERE tn = ANY($1)"
        if (historyTable != null)
            SQL += "UNION ALL SELECT ${rows.names()} FROM ${historyTable.quotedName} WHERE tn = ANY($1)"
        val tupleNumbers: Array<Any?> = tupleFeatures.map { it.tupleNumber.toB160() }.toTypedArray()
        conn.prepare(SQL, arrayOf(PgType.BYTE_ARRAY_ARRAY.text)).use { plan ->
            plan.execute(tupleNumbers).fetch().use { cursor ->
                rows.addAll(cursor)
            }
        }
        for (i in 0 until rows.size) {
            val tuple = rows[i] ?: continue
            Naksha.cache.store(tuple)
            val tupleNumber = tuple.tupleNumber
            for (tupleFeature in tupleFeatures) {
                if (tupleFeature.tupleNumber == tupleNumber) {
                    tupleFeature.tuple = tuple
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

    override fun getEncodingFlags(feature: Any?, context: Any?): Flags = storage.adminMap.getEncodingFlags(feature, context)

    override fun getDictionary(id: String): JbDictionary? = storage.adminMap.getDictionary(id)

    override fun getEncodingDictionary(feature: Any?, context: Any?): JbDictionary? = storage.adminMap.getEncodingDictionary(feature, context)
}
