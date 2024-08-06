@file:Suppress("OPT_IN_USAGE")

package naksha.psql

import kotlinx.datetime.*
import naksha.base.*
import naksha.base.Platform.PlatformCompanion.logger
import naksha.jbon.JbMapDecoder
import naksha.jbon.JbFeatureDecoder
import naksha.model.*
import naksha.model.NakshaError.NakshaErrorCompanion.EXCEPTION
import naksha.model.NakshaError.NakshaErrorCompanion.ILLEGAL_ARGUMENT
import naksha.model.request.*
import naksha.model.request.WriteRequest
import naksha.model.objects.Transaction
import naksha.psql.executors.PgWriter
import kotlin.js.JsExport
import kotlin.jvm.JvmField

/**
 * A session linked to a PostgresQL database.
 *
 * This object is created when [IStorage.newReadSession] or [IStorage.newWriteSession] are called, create the session is cheap without database access.
 *
 * @constructor Create a new session.
 * @param storage the storage to which this session is bound.
 * @param readOnly if this is a read-only session.
 * @param options the options to use, when opening new database connections.
 */
@JsExport
open class PgSession(
    @JvmField val storage: PgStorage,
    options: SessionOptions?,
    @JvmField val readOnly: Boolean
) : IWriteSession, IReadSession, ISession {

    /**
     * The options when opening new connections. The options are mostly immutable, except for the timeout values, for which there are dedicated setter.
     */
    var options: SessionOptions = options ?: SessionOptions()
        internal set

    override var socketTimeout: Int
        get() = options.socketTimeout
        set(value) {
            options = options.copy(socketTimeout = value)
        }

    override var stmtTimeout: Int
        get() = options.stmtTimeout
        set(value) {
            options = options.copy(stmtTimeout = value)
        }

    override var lockTimeout: Int
        get() = options.lockTimeout
        set(value) {
            options = options.copy(lockTimeout = value)
        }

    override var map: String
        get() = storage.schemaToMapId(options.mapId)
        set(value) {
            options = options.copy(mapId = value)
        }

    /**
     * The PostgresQL database connection currently being used; if any.
     */
    var pgConnection: PgConnection? = null
        private set

    /**
     * Returns the PostgresQL connection used internally. If none is yet acquired, acquires on from the pools and returns it.
     * @return the PostgresQL connection.
     */
    fun usePgConnection(): PgConnection {
        check(!_closed) { "Connection closed" }
        var conn = pgConnection
        if (conn == null) {
            txBeforeStart()
            conn = storage.newConnection(options, readOnly, this::initConnection)
            pgConnection = conn
            txAfterStart(conn)
        }
        return conn
    }

    /**
     * Internally invoked by [usePgConnection] to initialize the connection.
     * @param conn the connection to initialize.
     * @param query the query to executed, can be modified, when overriding this method.
     */
    open fun initConnection(conn: PgConnection, query: String) {
        conn.execute(query).close()
    }

    /**
     * The `uid` counter (unique identifier within a transaction).
     */
    @JvmField
    val uid: AtomicInt = AtomicInt(0)

    /**
     * The current transaction number.
     */
    private var _txn: Int64? = null

    /**
     * The epoch milliseconds of when the transaction started (`transaction_timestamp()`).
     */
    private var _txts: Int64? = null

    /**
     * The current version.
     */
    private var _version: Version? = null

    /**
     * Keeps transaction's counters.
     */
    private var transaction: Transaction? = null

    /**
     * The last error number as SQLState.
     */
    var errNo: String? = null

    /**
     * The last human-readable error message.
     */
    var errMsg: String? = null

    fun reset() {
        clear()
    }

    fun clear() {
        _txn = null
        _txts = null
        uid.set(0)
        errNo = null
        errMsg = null
        transaction = null
    }

    /**
     * Returns the current version (transaction number), if no version is yet generated, acquires a new one from the database.
     * @return The current version (transaction number).
     */
    fun version(): Version {
        if (_version == null) {
            // Start a new transaction.
            val conn = usePgConnection()
            val QUERY = "SELECT nextval($1) as txn, (extract(epoch from transaction_timestamp())*1000)::int8 as time"
            val cursor = conn.execute(QUERY, arrayOf(storage.txnSequenceOid)).fetch()
            cursor.use {
                var txn: Int64 = cursor["txn"]
                val txts: Int64 = cursor["time"]
                var version = Version(txn)
                val txInstant = Instant.fromEpochMilliseconds(txts.toLong())
                val txDate = txInstant.toLocalDateTime(TimeZone.UTC)
                if (version.year() != txDate.year || version.month() != txDate.monthNumber || version.day() != txDate.dayOfMonth) {
                    logger.info("Transaction counter is in wrong day, acquire advisory lock")
                    conn.execute("SELECT pg_advisory_lock($1)", arrayOf(PgUtil.TXN_LOCK_ID)).close()
                    try {
                        val c2 = conn.execute("SELECT nextval($1) as txn", arrayOf(storage.txnSequenceOid)).fetch()
                        c2.use {
                            txn = c2["txn"]
                            version = Version(txn)
                        }
                        if (version.year() != txDate.year || version.month() != txDate.monthNumber || version.day() != txDate.dayOfMonth) {
                            logger.info("Transaction counter is still at wrong day, rollover to next day")
                            // Rollover, we update sequence of the day.
                            version = Version.of(txDate.year, txDate.monthNumber, txDate.dayOfMonth, Int64(0))
                            txn = version.txn
                            conn.execute("SELECT setval($1, $2)", arrayOf(storage.txnSequenceOid, txn + 1)).close()
                        }
                        logger.info("Release advisory lock")
                        conn.execute("SELECT pg_advisory_unlock($1)", arrayOf(PgUtil.TXN_LOCK_ID)).close()
                    } catch(e: Throwable) {
                        logger.error("Fatal exception while holding an advisory lock, terminating connection: {}", e)
                        // This must not happen, to release the advisory lock, we need to terminate the connection!
                        conn.terminate()
                        throw NakshaException(EXCEPTION,
                            "Failed to increment 'txn', exception while holding advisory lock, terminating connection"
                        )
                    }
                }
                _txn = txn
                _txts = txts
                _version = version
                uid.set(0)
            }
        }
        return _version!!
    }

    /**
     * The start time of the version (transaction) in epoch milliseconds.
     * @return the start time of the version (transaction) in epoch milliseconds.
     */
    fun versionTime(): Int64 {
        version()
        return _txts!!
    }

    private var _featureReader: JbFeatureDecoder? = null
    private fun featureReader(): JbFeatureDecoder {
        var reader = _featureReader
        if (reader == null) {
            reader = JbFeatureDecoder()
            _featureReader = reader
        }
        reader.dictManager = storage[storage.defaultSchemaName].dictionaries()
        return reader
    }

    private var _propertiesReader: JbMapDecoder? = null
    private fun propertiesReader(): JbMapDecoder {
        var mapDecoder = _propertiesReader
        if (mapDecoder == null) {
            mapDecoder = JbMapDecoder()
            _propertiesReader = mapDecoder
        }
        mapDecoder.reader.localDict = featureReader().reader.localDict
        mapDecoder.reader.globalDict = featureReader().reader.globalDict
        return mapDecoder
    }

    /**
     * Returns collectionId without partition part.
     * For `topology_p0` it will return `topology`.
     */
//    fun getBaseCollectionId(collectionId: String): String {
//        // Note: "topology_p000" is a partition, but we need collection-id.
//        //        0123456789012
//        // So, in that case we will find an underscore at index 8, so i = length-5!
//        val i = collectionId.lastIndexOf('$')
//        return if (i >= 0 && i == (collectionId.length - 3) && collectionId[i + 1] == 'p') {
//            collectionId.substring(0, i)
//        } else {
//            collectionId
//        }
//    }

    /**
     * Single threaded all-or-nothing bulk write operation.
     * As result there is row with success or error returned.
     */
//    fun write(writeRequest: WriteRequest): Response {
//        val executor = WriteRequestExecutor(this, true)
//        val transactionAction = TransactionAction(transaction(), writeRequest)
//        return try {
//            transactionAction.write()
//            val writeFeaturesResult = executor.write(writeRequest)
//            transactionAction.write()
//            writeFeaturesResult
//        } catch (e: NakshaException) {
//            logger.debug("Supress exception: {}", e)
//            ErrorResponse(NakshaError(e.errNo, e.errMsg))
//        } catch (e: Throwable) {
//            logger.debug("Suppress exception: {}", e.cause ?: e)
//            ErrorResponse(NakshaError(ERR_FATAL, e.cause?.message ?: "Fatal ${e.stackTraceToString()}"))
//        }
//    }

    /**
     * Return the current state of the transaction.
     *
     * If no transaction started yet, starts a new one.
     * @return the current state of the transaction.
     */
    fun transaction(): Transaction {
        var t = transaction
        if (t == null) {
            t = Transaction(version().txn)
            transaction = t
        }
        return t
    }

    override fun execute(request: Request): Response {
        when (request) {
            is WriteRequest -> {
                PgWriter(this, request.writes)
            }

            is ReadRequest -> {
                TODO("ReadRequest not yet implemented")
            }

            else -> throw NakshaException(ILLEGAL_ARGUMENT, "Unknown request")
        }
        throw NakshaException(ILLEGAL_ARGUMENT, "Unknown request")
    }

    /**
     * Invoked before a new transaction starts. This is before even the transaction number has been acquired, called by [usePgConnection].
     */
    open protected fun txBeforeStart() {}

    /**
     * Invoked after a new transaction has been started, so a connection and a transaction number are available, called by
     * [usePgConnection].
     */
    open protected fun txAfterStart(conn: PgConnection) {}

    /**
     * Invoked before a transaction is committed (called by [commit]).
     */
    open protected fun txOnCommit(session: PgConnection) {}

    /**
     * Invoked before a transaction is rolled-back (called by [rollback]).
     */
    open protected fun txOnRollback(session: PgConnection) {}

    override fun commit() {
        val conn = pgConnection
        check(!_closed) { "Connection closed" }
        if (conn != null) {
            txOnCommit(conn)
            this.pgConnection = null
            try {
                conn.commit()
            } finally {
                conn.close()
            }
        }
    }

    override fun rollback() {
        val conn = pgConnection
        check(!_closed) { "Connection closed" }
        if (conn != null) {
            txOnRollback(conn)
            this.pgConnection = null
            try {
                conn.rollback()
            } finally {
                conn.close()
            }
        }
    }

    private var _closed = false

    override fun isClosed(): Boolean = _closed

    override fun close() {
        if (!_closed) {
            rollback()
            _closed = true
            pgConnection?.close()
            pgConnection = null
        }
    }
}
