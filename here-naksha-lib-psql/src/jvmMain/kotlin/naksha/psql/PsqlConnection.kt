package naksha.psql

import naksha.base.GZip
import java.lang.ref.WeakReference
import java.sql.ResultSet
import java.sql.Statement

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
            // TODO: Update when statement-timeout, lock-timeout or others are updated!
            field = value
        }

    init {
        // TODO: Set the statement- and lock-timeout. Do other initialization work!
        jdbc.autoCommit = false
    }

    /**
     * The weak-reference to this session.
     */
    val weakRef = WeakReference(this)

    private var _jdbc: org.postgresql.jdbc.PgConnection? = jdbc

    /**
     * The JDBC connection backing this PSQL connection.
     * @throws IllegalStateException if the connection was closed.
     */
    val jdbc: org.postgresql.jdbc.PgConnection
        get() {
            val c = _jdbc
            check(c != null) { "Connection is closed" }
            return c
        }

    private var dbInfo: PgInfo? = null

    /**
     * Returns general information about the database to which this API grants access.
     */
    override fun info(): PgInfo {
        var dbInfo = this.dbInfo
        if (dbInfo == null) {
            dbInfo = PgInfo(this, options.schema)
            this.dbInfo = dbInfo
        }
        return dbInfo
    }

    /**
     * Execute an SQL query with the given arguments. The placeholder should be **$1** to **$n**.
     * @param sql The SQL query to execute.
     * @param args The arguments to be set at $n position, where $1 is the first array element.
     * @return the cursor.
     */
    override fun execute(sql: String, args: Array<Any?>?): PsqlCursor {
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

        var rs: ResultSet? = stmt.resultSet
        // refer to getMoreResults() documentation to see how to detect end of result sets.
        // iterate to last result set.
        while (!(!stmt.getMoreResults(Statement.KEEP_CURRENT_RESULT) && (stmt.updateCount == -1))) {
            rs = stmt.resultSet
        }
        return if (rs != null) {
            PsqlCursor(rs, true)
        } else {
            val cursor = PsqlCursor(stmt.updateCount)
            stmt.close()
            cursor
        }
    }

    /**
     * Prepare the given SQL statement using parameters of the given types.
     * @param sql The SQL query to execute.
     * @param typeNames The name of the types of the arguments, to be at $n position, where $1 is the first array element.
     * @return The prepared plan.
     */
    override fun prepare(sql: String, typeNames: Array<String>?): PgPlan = PsqlPlan(PsqlQuery(sql), jdbc)

    /**
     * Use this database connection to compress bytes using `gzip`. This requires a database connection for some implementations, for
     * example when execute in [PLV8 extension](https://plv8.github.io/), where it will need to perform a `select gzip(...)` query for this.
     * @param raw the bytes to compress.
     * @return The deflated (compressed) bytes.
     * @throws UnsupportedOperationException if the platform does not support this operation.
     */
    override fun gzip(raw: ByteArray): ByteArray = GZip.gzip(raw)

    /**
     * Use this database connection to decompress bytes using `gzip`. This requires a database connection for some implementations, for
     * example when execute in [PLV8 extension](https://plv8.github.io/), where it will need to perform a `select gunzip(...)` query for
     * this.
     * @param compressed the bytes to decompress.
     * @return the inflated (decompressed) bytes.
     * @throws UnsupportedOperationException if the platform does not support this operation.
     */
    override fun gunzip(compressed: ByteArray): ByteArray = GZip.gunzip(compressed)

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
}