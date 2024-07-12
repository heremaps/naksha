package naksha.psql

import naksha.model.IReadSession
import naksha.model.ISession
import naksha.model.IWriteSession
import kotlin.js.JsExport

/**
 * The abstract PostgresQL session based upon a PostgresQL storage.
 * @property storage the storage to which this session is bound.
 * @param options the options to be used, when opening new connections.
 */
@Suppress("OPT_IN_USAGE")
@JsExport
abstract class AbstractSession<T>(val storage: PgStorage, options: PgOptions) : IWriteSession, IReadSession, ISession, PgTx {

    /**
     * The options when opening new connections. The options are mostly immutable, except for the timeout values, for which there are
     * dedicated setter.
     */
    var options: PgOptions = options
        internal set

    override var socketTimeout: Int
        get() = options.socketTimeout
        set(value) {
            options = options.copy(socketTimeout = value)
            // TODO: if pgConnection is not null, update
        }

    override var stmtTimeout: Int
        get() = options.stmtTimeout
        set(value) {
            options = options.copy(stmtTimeout = value)
            // TODO: if pgConnection is not null, update
        }

    override var lockTimeout: Int
        get() = options.lockTimeout
        set(value) {
            options = options.copy(lockTimeout = value)
            // TODO: if pgConnection is not null, update
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
            // TODO: Start new transaction
            conn = storage.newConnection(options, this::initConnection)
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
     * Invoked before a new transaction starts. This is before even the transaction number has been acquired, called by [usePgConnection].
     */
    protected abstract fun txBeforeStart()

    /**
     * Invoked after a new transaction has been started, so a connection and a transaction number are available, called by
     * [usePgConnection].
     */
    protected abstract fun txAfterStart(conn: PgConnection)

    /**
     * Invoked before a transaction is committed (called by [commit]).
     */
    protected abstract fun txOnCommit(session: PgConnection)

    /**
     * Invoked before a transaction is rolled-back (called by [rollback]).
     */
    protected abstract fun txOnRollback(session: PgConnection)

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