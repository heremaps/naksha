@file:Suppress("OPT_IN_USAGE")

package naksha.psql

import naksha.base.*
import naksha.base.Action.Action_C.DELETE
import naksha.base.NakshaError.NakshaErrorCompanion.ILLEGAL_ARGUMENT
import naksha.base.Platform.PlatformCompanion.logger
import naksha.base.Platform.PlatformCompanion.newAtomicInt64
import naksha.model.*
import naksha.base.NakshaError.NakshaErrorCompanion.ILLEGAL_STATE
import naksha.base.Platform.PlatformCompanion.FAL
import naksha.base.Platform.PlatformCompanion.fal
import naksha.base.fn.Fn1
import naksha.model.Naksha.NakshaCompanion.ADMIN_CATALOG_ID
import naksha.model.objects.NakshaCollection
import naksha.model.objects.NakshaCatalog
import naksha.model.objects.XyzMembers
import naksha.model.objects.XyzProcessors
import naksha.model.request.*
import naksha.model.request.WriteRequest
import naksha.model.objects.NakshaTx
import naksha.model.objects.StandardMembers.StandardMembers_C.FN
import naksha.model.objects.StandardMembers.StandardMembers_C.VERSION
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.js.JsExport
import kotlin.jvm.JvmField
import kotlin.jvm.JvmName

/**
 * A session linked to a PostgresQL database.
 *
 * This object is created when [IStorage.newReadSession] or [IStorage.newWriteSession] are called. A session is cheap to create, it does not pre-allocate any database connection, it will be connected on demand _(lazy)_.
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
): PgCache(), IWriteSession, IReadSession, ISession {

    companion object PgSession_C {
        private val nextSessionId = newAtomicInt64(0L)
        private const val ONE = 1L

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
     *
     * This is not the version or anything related, it just a JVM unique integer that can be used to keep track of sessions in maps.
     * @since 3.0
     */
    val id: Long = nextSessionId.getAndAdd(ONE)

    override val storage: PgStorage = pgStorage

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
        if (_closed.get()) throw illegalState("Connection closed")
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

    /** The mutable connection used, if any. */
    private val _conn = AtomicRef<PgConnection>(null)
    /** The current mutation connection, used to track advisory-lock ownership. */
    internal val pgConnection: PgConnection?
        get() = _conn.get()
    /** The mutable connection lock to be acquired before using the mutable connection. We only allow single threaded use of single connection. */
    private val _connLock = Platform.newLock()

    /**
     * Executes some code using either a shared connection or a new temporary read-only connection.
     *
     * This call synchronizes the action if needed, but allows multiple parallel read-only connections, when read-only is requested and no mutation connection was yet requested.
     * @param readOnly if a read-only connection is wanted.
     * @param action the operation to execute, first argument will be the [PgConnection]; **do not close the connection!**.
     * @return some optional result of the execution.
     * @since 3.0
     * @see useConn
     */
    fun <Z> useConnNonNull(readOnly: Boolean, action: Fn1<Z, PgConnection>): Z {
        if (!readOnly && this.readOnly) throw illegalArg("Can't return mutable connection for read-only session")
        var conn = _conn.get()
        if (!readOnly || conn != null) {
            // When either mutation is wanted or only one shared connection is available, only allow one thread at a time.
            _connLock.acquire().use {
                if (conn == null) {
                    // The user asked for a mutable connection, but we have none, so we start a new shared connection.
                    // Beware, we do not know what kind of changes the user does!
                    // Therefore, we can't close the connection after execution, even when no `tx` was created.
                    // The use could still have made some preparations, that are needed for following reads or writes.
                    conn = storage.newConnection(options, readOnly, this::initConnection)
                    _conn.set(conn)
                }
                return action.call(conn)
            }
        }
        // If read-only was requested, and there is no shared connection, we can run in parallel.
        // Note: This requires that the cache now operates thread safe!
        return storage.newConnection(options, readOnly, this::initConnection).use { conn ->
            action.call(conn)
        }
    }

    /**
     * Executes some code using either a shared connection or a new temporary read-only connection.
     *
     * This call synchronizes the action if needed, but allows multiple parallel read-only connections, when read-only is requested and no mutation connection was yet requested.
     * @param readOnly if a read-only connection is wanted.
     * @param action the operation to execute, first argument will be the [PgConnection]; **do not close the connection!**.
     * @return some optional result of the execution.
     * @since 3.0
     * @see useConnNonNull
     */
    fun <Z> useConn(readOnly: Boolean, action: Fn1<Z?, PgConnection>): Z? {
        if (!readOnly && this.readOnly) throw illegalArg("Can't return mutable connection for read-only session")
        var conn = _conn.get()
        if (!readOnly || conn != null) {
            // When either mutation is wanted or only one shared connection is available, only allow one thread at a time.
            _connLock.acquire().use {
                if (conn == null) {
                    // The user asked for a mutable connection, but we have none, so we start a new shared connection.
                    // Beware, we do not know what kind of changes the user does!
                    // Therefore, we can't close the connection after execution, even when no `tx` was created.
                    // The use could still have made some preparations, that are needed for following reads or writes.
                    conn = storage.newConnection(options, readOnly, this::initConnection)
                    _conn.set(conn)
                }
                return action.call(conn)
            }
        }
        // If read-only was requested, and there is no shared connection, we can run in parallel.
        // Note: This requires that the cache now operates thread safe!
        return storage.newConnection(options, readOnly, this::initConnection).use { conn ->
            action.call(conn)
        }
    }

    /**
     * If a connection is backing this session currently, return the [id][PgConnection.id] of the [connection][PgConnection], otherwise `null`.
     * @since 3.0
     */
    val connectionId: Long?
        get() = _conn.get()?.id

    /**
     * Internally invoked by [useConn] and [useConnNonNull] to initialize the connection.
     * @param conn the connection to initialize.
     * @param query the query to executed, can be modified, when overriding this method.
     * @since 3.0
     */
    protected open fun initConnection(conn: PgConnection, query: String) {
        // This is the same as the default implementation, when init is null, see PgStorage::newConnection
        conn.execute(query).close()
    }

    /**
     * The current transaction; if any.
     * @since 3.0
     */
    private var tx: NakshaTx? = null

    /**
     * Tests if the current session does have potentially pending modifications; therefore, requires [commit] or [rollback] and [getTransaction] does not return `null`.
     * @since 3.0
     */
    @get:JvmName("isModified")
    val isModified: Boolean
        get() = tx != null

    override fun getTransaction(): NakshaTx? = tx

    /**
     * Return the current transaction, if no transaction started yet, starts a new one.
     * @return the current transaction.
     * @throws NakshaException with error [ILLEGAL_STATE][NakshaError.ILLEGAL_STATE] if this is session is [readOnly] or [closed][isClosed].
     */
    override fun useTransaction(): NakshaTx {
        assertMutable()
        assertOpen()
        var tx: NakshaTx? = this.tx
        if (tx == null) {
            tx = useConnNonNull(readOnly = false) { conn ->
                val pgTxn = storage.database.new_version(conn)
                val tx = NakshaTx()
                tx.setVersion(pgTxn.version)
                tx.properties.xyz["appId"] = options.appId
                tx.properties.xyz["author"] = options.author
                tx
            }
            this.tx = tx
        }
        return tx
    }

    private var executionCount: Int = 0

    override fun executeRead(request: ReadRequest): Response {
        val readFeatures = when (request) {
            is ReadFeatures -> request
            is ReadCollections -> request.toReadFeatures()
            is ReadMaps -> request.toReadFeatures()
            else -> throw illegalArg("Unsupported read request ${request::class.simpleName}")
        }
        val catalogName = readFeatures.catalogId ?: throw illegalArg("Read request does not specify a catalog")
        val collectionName = readFeatures.collectionId ?: throw illegalArg("Read request does not specify a collection")
        val catalogId = Id(catalogName)
        val collectionId = Id(collectionName)
        try {
            val reader = PgReader(this, readFeatures)
            val response = reader.execute()
            if (response is SuccessResponse) {
                response.filterResults(*request.resultFilters.filterNotNull().toTypedArray())
            }
            return response
        } catch (t: Throwable) {
            logger.info("PgSession: execute failed", t)
            val nakshaException = PgExceptionMapper.map(t)
            val error = nakshaException.error
            when (error.code) {
                NakshaError.CATALOG_NOT_FOUND -> {
                    // Remove catalog from cache, so we re-read it from database.
                    // This includes cache reset of all collections.
                    invalidatePgCatalog(catalogId.intValue)
                }
                NakshaError.COLLECTION_NOT_FOUND -> {
                    // Remove the collection from cache, so we re-read it from database.
                    invalidatePgCollection(catalogId.intValue, collectionId.intValue)
                }
            }
            return ErrorResponse(nakshaException.error)
        }
    }

    override fun executeWrite(request: WriteRequest): Response {
        try {
            val writer = PgWriter(this, executionCount++ != 0)
            val response = writer.execute(request.writes)
            if (response is SuccessResponse) {
                response.filterResults(*request.resultFilters.filterNotNull().toTypedArray())
            }
            return response
        } catch (t: Throwable) {
            logger.info("PgSession: execute failed", t)
            val nakshaException = PgExceptionMapper.map(t)
            val error = nakshaException.error
            when (error.code) {
                NakshaError.CATALOG_NOT_FOUND -> {
                    val catalog = error.catalog
                    if (catalog != null) {
                        invalidatePgCatalog(Id(catalog).intValue)
                    }
                }
                NakshaError.COLLECTION_NOT_FOUND -> {
                    val catalog = error.catalog
                    val collection = error.collection
                    if (catalog != null && collection != null) {
                        invalidatePgCollection(Id(catalog).intValue, Id(collection).intValue)
                    }
                }
            }
            return ErrorResponse(nakshaException.error)
        }
    }

    @Deprecated("Please use executeRead or executeWrite")
    override fun execute(request: Request): Response {
        return if (request is ReadRequest) executeRead(request)
          else if (request is WriteRequest) executeWrite(request)
          else ErrorResponse(NakshaError(ILLEGAL_ARGUMENT, "Unknown request type: ${request::class.simpleName}"))
    }

    /**
     * Reset the session into the initial state.
     */
    private fun clearTxAndCloseConnection() {
        executionCount = 0
        tx = null
        _connLock.acquire().use {
            val conn = _conn.get()
            if (conn != null) try {
                conn.close()
            } catch (t: Throwable) {
                logger.warn("${fal(2)}Closing connection failed", t)
            } finally {
                _conn.set(null)
            }
        }
    }

    override fun commit() {
        assertOpen()
        assertMutable()
        _connLock.acquire().use {
            val conn = this._conn.get()
            val tx = this.tx

            if (conn == null) {
                if (tx == null) return
                throw illegalState("${FAL}The connection is closed, but there is an open connection.")
            }
            try { // conn != null
                if (tx != null) {
                    // Update the transaction log, we're committing an open transaction.
                    val writeTx = Write().createFeature(ADMIN_CATALOG_ID, TRANSACTIONS_COL, tx)
                    val writeRequest = WriteRequest().add(writeTx)
                    val writer = PgWriter(this, false)
                    writer.execute(writeRequest.writes)
                }
                conn.commit()
            } catch (t: Throwable) {
                throw generalException("Failed to commit transaction", cause = t)
            } finally {
                clearTxAndCloseConnection()
            }
        }
    }

    override fun rollback() {
        clearTxAndCloseConnection()
    }

    private val _closed = AtomicBool(false)

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

    override fun isClosed(): Boolean = _closed.get()

    override fun close() {
        if (_closed.compareAndSet(expect = false, update = true)) {
            rollback() // implicitly calls —> clearTxAndCloseConnection()
        }
    }

    @v30_experimental
    override fun acquireSessionLock(lockId: String): ILock {
        assertOpen()
        return useConnNonNull(readOnly = false) { conn -> PgLock(this, conn, lockId, true) }
    }

    @v30_experimental
    override fun acquireTransactionLock(lockId: String): ILock {
        assertOpen()
        return useConnNonNull(readOnly = false) { conn -> PgLock(this, conn, lockId, false) }
    }

    override val parent: PgCache
        get() = storage.database
    override val pgAdmin: PgCatalog
        get() = storage.database.pgAdmin
    override val pgAdminCollections: PgCollection
        get() = storage.database.pgAdminCollections
    override val pgAdminTransactions: PgCollection
        get() = storage.database.pgAdminTransactions
    override val pgAdminCatalogs: PgCollection
        get() = storage.database.pgAdminCatalogs
    override val pgAdminBooks: PgCollection
        get() = storage.database.pgAdminBooks

    override fun loadTuples(featureTuples: List<FeatureTuple?>, from: Int, to: Int) {
        val missing = featureTuples.subList(from, to).mapNotNull { if (it != null && it.tuple == null) it else null }
        if (missing.isNotEmpty()) {
            val byCollection: MutableMap<PgCollection, Int> = mutableMapOf()
            val pairs: MutableList<Pair<PgCollection, FeatureTupleList>> = mutableListOf()
            useConn(readOnly = true) { conn ->
                for (featureTuple in missing) {
                    val tn = featureTuple.tupleNumber
                    val collection: PgCollection = getPgCollection(tn.catalogNumber, tn.collectionNumber, conn) ?: continue
                    val index = byCollection[collection]
                    val list = if (index != null) pairs[index].second else {
                        byCollection[collection] = pairs.size
                        val list = FeatureTupleList()
                        pairs.add(Pair(collection, list))
                        list
                    }
                    list.add(featureTuple)
                }
            }
            // We grouped what we need to load by collection.
            if (!options.parallel || _conn.get() != null) {
                // User explicitly requested a single-connection, or we know we have a mutable connection.
                // In the second case, this would anyway use a mutex, so parallel runs are not possible.
                useConn(readOnly = true) { conn ->
                    for ((collection, featureTuples) in pairs) {
                        loadTuplesFromCollection(conn, collection, featureTuples)
                    }
                }
                return
            }
            // Load all tuple in parallel, one thread per collection.
            val input = pairs.toTypedArray()
            val output = arrayOfNulls<Any>(input.size)
            Platform.runParallel(input, output) { pair ->
                useConn(readOnly = true) { conn ->
                    val collection = pair.first
                    val featureTuples = pair.second
                    val tn = collection.tupleNumber
                    val catalog = getPgCatalog(tn.catalogNumber, conn)?: return@useConn null
                    conn.execute(catalog.SET_searchPath).close()
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
     * @return the number of loaded tuple.
     */
    private fun loadTuplesFromCollection(conn: PgConnection, collection: PgCollection, featureTuples: FeatureTupleList): Int {
        if (featureTuples.isEmpty()) return 0
        val bytes = ByteArray(featureTuples.size * 16)
        for (i in 0..< featureTuples.size) {
            val tn = featureTuples[i]?.tupleNumber ?: throw internalError("featureTuples[$i] is null")
            // Note: We do the math by intention. The CPU is very good at math, it's basically free.
            //       However, memory access is really slow, by doing it this way, the CPU can reorder
            //       the memory access, and the JIT can unroll the loop.

            // Set version, this way allows the CPU to reorder or execute in parallel to the following.
            bytes.setInt64Be((i shl 4) + 8, tn.version)

            // We need to byte-swap the feature-number and then invert the sign-bit afterward.
            // For this we write LE, then read BE, flip the sign bit, and write back in BE.
            // This is a cross-platform code hack, because there is no CPU instrution for byte-swap in JavaScript.
            val offset = i shl 4 // shl 4 is the same as *16
            val fn = tn.featureNumber
            bytes.setInt64Le(offset, fn)
            val fn_swapped = bytes.getInt64Be(offset)
            bytes.setInt64Be(offset, fn_swapped xor Long.MIN_VALUE)
            // Note: `a xor 0 = a`
            //       `a xor 1 = ~a`
            // Long.MIN_VALUE is binary `100000000....`, therefore, only sign bit is inverted.
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
        (get_byte(b, 7) & 255)::bigint        AS $FN,
        
        ((get_byte(b, 8) & 255)::bigint << 56) |
        ((get_byte(b, 9) & 255)::bigint << 48) |
        ((get_byte(b, 10) & 255)::bigint << 40)|
        ((get_byte(b, 11) & 255)::bigint << 32)|
        ((get_byte(b, 12) & 255)::bigint << 24)|
        ((get_byte(b, 13) & 255)::bigint << 16)|
        ((get_byte(b, 14) & 255)::bigint << 8) |
        (get_byte(b, 15) & 255)::bigint        AS $VERSION
    FROM (
        SELECT substring($1::bytea FROM g * 16 + 1 FOR 16) AS b
        FROM generate_series(0, octet_length($1::bytea) / 16 - 1) AS g
    ) AS t
)
SELECT t.* FROM ${collection.quotedName} t
JOIN lookup ON (lookup.$FN, lookup.$VERSION) = (t.$FN, t.$VERSION)"""
        // We expect that seach-path was set by caller!
        val rows = PgRows().withCollection(collection)
        conn.prepare(SQL, arrayOf(PgType.BYTE_ARRAY.text)).use { plan ->
            plan.execute(arrayOf(bytes)).fetch().use { cursor ->
                rows.readAll(cursor)
            }
        }
        // TupleHeapCache holds tuples as SoftReference, so a just-stored tuple can be GC'd before we read it
        // back via cache[tupleNumber], returning null under memory pressure. The local map keeps a
        // strong reference as a workaround.
        val byTupleNumber = HashMap<TupleNumber, Tuple>(rows.size)
        for (i in 0 until rows.size) {
            val tuple = rows[i] ?: continue
            Naksha.cache.store(tuple)
            byTupleNumber[tuple.tupleNumber] = tuple
        }
        var found = 0
        for (i in 0..< featureTuples.size) {
            val featureTuple = featureTuples[i] ?: throw internalError("featureTuples[$i] is null")
            val tuple = byTupleNumber[featureTuple.tupleNumber] ?: Naksha.cache[featureTuple.tupleNumber]
            if (tuple != null) {
                featureTuple.tuple = tuple
                found++
            }
        }
        return found
    }

    override fun getCatalogById(catalogId: String, allowTombstone: Boolean): NakshaCatalog?
        = getCatalogByNumber(Id.numberToInt(Id.textToNumber(catalogId)), allowTombstone)

    override fun getCatalogByNumber(catalogNumber: Int, allowTombstone: Boolean): NakshaCatalog? {
        return useConn(readOnly = true) { conn ->
            val sessionCatalog = getPgCatalog(catalogNumber, conn)
            if (sessionCatalog == null) null
            else if (!allowTombstone && (sessionCatalog.naksha.tupleNumber?.isTombstone ?: false)) null
            // TODO: WARNING, this method returns a cached mutable instance of NakshaCatalog!
            //       We need to change our classes in `lib-base` so that we can have read-only objects!
            //       We MUST change this, but always returning a copy is expensive too.
            else sessionCatalog.naksha // .tuple.decodeCatalog()
        }
    }

    override fun getCollectionById(catalog: NakshaCatalog, collectionId: String, allowTombstone: Boolean): NakshaCollection?
        = getCollectionByNumber(catalog, Id.numberToInt(Id.textToNumber(collectionId)), allowTombstone)

    override fun getCollectionByNumber(catalog: NakshaCatalog, collectionNumber: Int, allowTombstone: Boolean): NakshaCollection? {
        return useConn(readOnly = true) { conn ->
            val catalogNumber = Id.numberToInt(Id.textToNumber(catalog.id))
            val collection = getPgCollection(catalogNumber, collectionNumber, conn)
            if (collection == null) null
            else if (!allowTombstone && collection.tuple.action == DELETE) null
            // TODO: WARNING, this method returns a cached mutable instance of NakshaCollection!
            //       We need to change our classes in `lib-base` so that we can have read-only objects!
            //       We MUST change this, but always returning a copy is expensive too.
            else collection.naksha // .tuple.decodeCollection()
        }
    }

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
