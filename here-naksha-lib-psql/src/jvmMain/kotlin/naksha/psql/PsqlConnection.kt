package naksha.psql

import naksha.model.NakshaError.NakshaErrorCompanion.ILLEGAL_STATE
import naksha.model.NakshaException
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
    val id: Long,
    jdbc: org.postgresql.jdbc.PgConnection,
    options: PgOptions
) : PgConnection, AutoCloseable {

    override var options: PgOptions = options
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
        val stmt = if (args.isNullOrEmpty()) {
            // no args execute
            val stmt = conn.createStatement()
            stmt.execute(sql)
            stmt
        } else {
            val query = PsqlQuery(sql)
            val stmt = query.prepare(conn)
            if (args.isNotEmpty()) query.bindArguments(stmt, args)
            stmt.execute()
            stmt
        }
        return PsqlCursor(stmt, true)
    }

    /**
     * Prepare the given SQL statement using parameters of the given types.
     * @param sql The SQL query to execute.
     * @param typeNames The name of the types of the arguments, to be at $n position, where $1 is the first array element.
     * @return The prepared plan.
     */
    override fun prepare(sql: String, typeNames: Array<String>?): PgPlan = PsqlPlan(PsqlQuery(sql), jdbc)

    /**
     * Commit all changes done in the current transaction.
     */
    override fun commit() {
        jdbc.commit()
    }

    /**
     * Rollback (revert) all changes done in the current transaction.
     */
    override fun rollback() {
        jdbc.rollback()
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
            if (!pgConnection.autoCommit) {
                pgConnection.rollback()
            } else {
                pgConnection.autoCommit = false
            }
            instance.connectionPool[id]?.session?.compareAndSet(weakRef, null)
        }
    }

    override fun toString(): String = "${instance}#$id"

    override fun terminate() {
        val pgConnection = _jdbc
        this._jdbc = null
        if (pgConnection != null) {
            // Remove the connection from the pool and close it
            instance.connectionPool.remove(id)
            pgConnection.close()
        }
    }
}