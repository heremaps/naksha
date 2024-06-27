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
 * @property options the options when opening new connections. Changing the options, will only affect new sessions.
 */
@Suppress("OPT_IN_USAGE")
@JsExport
abstract class AbstractNakshaSession(
    val storage: PgStorage,
    override var context: NakshaContext,
    var options: PgSessionOptions
) : IWriteSession, IReadSession, ISession {

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

    /**
     * The PostgresQL database session.
     */
    private var _pgSession: PgSession? = null

    /**
     * Returns the PostgresQL session or open a new one.
     * @return the PostgresQL session.
     */
    internal fun pgSession(): PgSession {
        check(!_closed)
        var conn = _pgSession
        if (conn == null) {
            pgSessionBeforeStart()
            conn = storage.openSession(context, options)
            _pgSession = conn
            pgSessionAfterStart(conn)
        }
        return conn
    }

    /**
     * Invoked before a new session is started (called by [pgSession] method).
     */
    protected abstract fun pgSessionBeforeStart()

    /**
     * Invoked after a new session is started (called by [pgSession] method).
     */
    protected abstract fun pgSessionAfterStart(session: PgSession)

    /**
     * Invoked before a session is committed (called by [commit]).
     */
    protected abstract fun pgSessionOnCommit(session: PgSession)

    /**
     * Invoked before a session is rolled-back (called by [rollback]).
     */
    protected abstract fun pgSessionOnRollback(session: PgSession)

    override fun commit() {
        val conn = _pgSession
        check(!_closed)
        if (conn != null) {
            pgSessionOnCommit(conn)
            this._pgSession = null
            try {
                conn.commit()
            } finally {
                conn.close()
            }
        }
    }

    override fun rollback() {
        val conn = _pgSession
        check(!_closed)
        if (conn != null) {
            pgSessionOnRollback(conn)
            this._pgSession = null
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
            _pgSession?.close()
            _pgSession = null
        }
    }
}