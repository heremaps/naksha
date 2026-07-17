package naksha.psql

import naksha.base.Int64
import naksha.base.NakshaError.NakshaErrorCompanion.ILLEGAL_STATE
import naksha.base.NakshaException
import naksha.model.SessionOptions
import java.lang.ref.WeakReference

/**
 * A thin wrapper around a JDBC PostgresQL connection, which implements the [PgConnection] interface.
 * @constructor Creates a new [PgConnection] wrapper.
 * @property instance the PostgresQL instance to which the connection is bound, the wrapper will return the JDBC connection into the
 * connection pool of this instance, when the wrapper is closed.
 * @property id the unique identifier of the JDBC connection, used to pool it in its instance pool [PsqlInstance.connectionPool].
 * @param jdbc the JDBC connection that backs this connection.
 * @property options the connection options.
 */
class PsqlConnection internal constructor(
    val instance: PsqlInstance,
    override val id: Int64,
    jdbc: org.postgresql.jdbc.PgConnection,
    options: SessionOptions
) : PgConnection, AutoCloseable {

    override var options: SessionOptions = options
        set(value) {
            //field = value
            //schemaInfo = null
            TODO("Update when statement-timeout, lock-timeout or others are updated!")
        }

    /**
     * The weak-reference to this session.
     */
    val weakRef = WeakReference(this)

    private var _jdbc: org.postgresql.jdbc.PgConnection? = jdbc

    /**
     * The JDBC connection backing this PSQL connection.
     * - Throws [ILLEGAL_STATE] if the connection is closed.
     */
    val jdbc
        get() = _jdbc ?: throw NakshaException(ILLEGAL_STATE, "Connection is closed")

    /**
     * Execute an SQL query with the given arguments. The placeholder should be **$1** to **$n**.
     * @param sql The SQL query to execute.
     * @param args The arguments to be set at $n position, where $1 is the first array element.
     * @return the cursor.
     */
    override fun execute(sql: String, args: Array<Any?>?): PgCursor {
        val conn = jdbc
        try {
            val stmt = if (args.isNullOrEmpty()) {
                // no args execute
                val stmt = conn.createStatement()
                stmt.execute(sql)
                stmt
            } else {
                val query = PsqlQuery(sql ,null)
                val stmt = query.prepare(conn)
                if (args.isNotEmpty()) query.bindArguments(stmt, args)
                stmt.execute()
                stmt
            }
            return PsqlCursor(stmt, true)
        } catch (throwable: Throwable) {
            throw PgExceptionMapper.map(throwable, sql)
        }
    }

    /**
     * Prepare the given SQL statement using parameters of the given types.
     * @param sql The SQL query to execute.
     * @param typeNames The name of the types of the arguments, to be at $n position, where $1 is the first array element.
     * @return The prepared plan.
     */
    override fun prepare(sql: String, typeNames: Array<String>?): PgPlan {
        try {
            return PsqlPlan(PsqlQuery(sql, typeNames), jdbc)
        } catch (throwable: Throwable) {
            throw PgExceptionMapper.map(throwable, sql)
        }
    }

    override var autoCommit: Boolean
        get() {
            return try {
                jdbc.autoCommit
            } catch (throwable: Throwable) {
                throw PgExceptionMapper.map(throwable)
            }
        }
        set(value) {
            try {
                jdbc.autoCommit = value
            } catch (throwable: Throwable) {
                throw PgExceptionMapper.map(throwable)
            }
        }

    /**
     * Commit all changes done in the current transaction.
     */
    override fun commit() {
        try {
            jdbc.commit()
        } catch (throwable: Throwable) {
            throw PgExceptionMapper.map(throwable)
        }
    }

    /**
     * Rollback (revert) all changes done in the current transaction.
     */
    override fun rollback() {
        try {
            jdbc.rollback()
        } catch (throwable: Throwable) {
            throw PgExceptionMapper.map(throwable)
        }
    }

    /**
     * Tests if this connection is closed.
     * @return _true_ if this connection is closed.
     */
    override fun isClosed(): Boolean = _jdbc == null

    /**
     * Rollback the underlying database connection and return it to the connection pool. Any further invocation of any method of this
     * object will raise a [IllegalStateException] from here on.
     */
    override fun close() {
        val pgConnection = _jdbc
        this._jdbc = null
        if (pgConnection != null) {
            try {
                if (pgConnection.isClosed) {
                    instance.connectionPool.remove(id)
                    return
                }
                if (!pgConnection.autoCommit) {
                    pgConnection.rollback()
                } else {
                    pgConnection.autoCommit = false
                }
                instance.connectionPool[id]?.connection?.compareAndSet(weakRef, null)
            } catch (throwable: Throwable) {
                instance.connectionPool.remove(id)
                throw PgExceptionMapper.map(throwable)
            }
        }
    }

    override fun toString(): String = "${instance}#$id"

    override fun terminate() {
        val pgConnection = _jdbc
        this._jdbc = null
        if (pgConnection != null) {
            try {
                // Remove the connection from the pool and close it
                instance.connectionPool.remove(id)
                pgConnection.close()
            } catch (throwable: Throwable) {
                throw PgExceptionMapper.map(throwable)
            }
        }
    }

    override fun toUri(showPassword: Boolean): String = if (showPassword) jdbc.url else toString()
}