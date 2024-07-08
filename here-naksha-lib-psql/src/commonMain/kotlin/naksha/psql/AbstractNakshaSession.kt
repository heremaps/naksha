package naksha.psql

import naksha.model.IReadSession
import naksha.model.ISession
import naksha.model.IWriteSession
import naksha.model.NakshaContext
import kotlin.js.JsExport

/**
 * The abstract Naksha Session based upon a PostgresQL storage.
 * @property storage the storage to which this session is bound.
 * @property context the context with which to initialize new sessions. Changing the options, will only affect new sessions.
 * @param options the options when opening new connections.
 */
@Suppress("OPT_IN_USAGE")
@JsExport
abstract class AbstractNakshaSession(
    val storage: PgStorage,
    override var context: NakshaContext,
    options: PgOptions
) : IWriteSession, IReadSession, ISession {

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
            // TODO: if pgConnection is not null, update live
        }

    override var stmtTimeout: Int
        get() = options.stmtTimeout
        set(value) {
            options = options.copy(stmtTimeout = value)
            // TODO: if pgConnection is not null, update live
        }

    override var lockTimeout: Int
        get() = options.lockTimeout
        set(value) {
            options = options.copy(lockTimeout = value)
            // TODO: if pgConnection is not null, update live
        }

    /**
     * The PostgresQL database connection currently being used; if any. **Beware**, do not modify!
     */
    var pgConnection: PgConnection? = null

    /**
     * Returns the PostgresQL connection used internally. If none is yet acquired, acquires on from the pools and returns it.
     * @return the PostgresQL connection.
     */
    fun usePgConnection(): PgConnection {
        check(!_closed)
        var conn = pgConnection
        if (conn == null) {
            pgSessionBeforeStart()
            conn = storage.newConnection(context, options)
            pgConnection = conn
            pgSessionAfterStart(conn)
        }
        return conn
    }

    /**
     * Invoked before a new session is started (called by [usePgConnection] method).
     */
    protected abstract fun pgSessionBeforeStart()

    /**
     * Invoked after a new session is started (called by [usePgConnection] method).
     */
    protected abstract fun pgSessionAfterStart(session: PgConnection)

    /**
     * Invoked before a session is committed (called by [commit]).
     */
    protected abstract fun pgSessionOnCommit(session: PgConnection)

    /**
     * Invoked before a session is rolled-back (called by [rollback]).
     */
    protected abstract fun pgSessionOnRollback(session: PgConnection)

    override fun commit() {
        val conn = pgConnection
        check(!_closed)
        if (conn != null) {
            pgSessionOnCommit(conn)
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
        check(!_closed)
        if (conn != null) {
            pgSessionOnRollback(conn)
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
            _closed = true
            pgConnection?.close()
            pgConnection = null
        }
    }
}