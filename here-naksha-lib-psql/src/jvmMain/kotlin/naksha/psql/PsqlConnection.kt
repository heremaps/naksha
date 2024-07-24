package naksha.psql

import naksha.base.GZip
import java.lang.ref.WeakReference
import java.security.MessageDigest

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

    companion object PsqlConnectionCompanion {
        private val md5 = ThreadLocal.withInitial { MessageDigest.getInstance("MD5") }
    }

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
     * @throws IllegalStateException if the connection was closed.
     */
    val jdbc: org.postgresql.jdbc.PgConnection
        get() {
            val c = _jdbc
            check(c != null) { "Connection is closed" }
            return c
        }

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

    override fun md5(bytes: ByteArray): ByteArray {
        val md5 = md5.get()
        md5.reset()
        md5.update(bytes)
        return md5.digest()
    }

    override fun md5(text: String): ByteArray {
        return md5(text.toByteArray(Charsets.UTF_8))
    }

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
     * Returns the partition number for the given amount of partitions.
     * @param conn the connection to use for hashing.
     * @param id the feature-id for which to return the partition-id.
     * @param partitions the number of partitions (1 to 256).
     * @return The partition number as value between 0 and part (exclusive).
     */
    override fun partitionNumber(conn: PgConnection, id: String, partitions: Int): Int {
        require(partitions in 1..256) { "Invalid number of partitions, expect a value between 1 and 256, found: $partitions" }
        // SQL: get_byte(digest(id,'md5'),0);
        val hash = conn.md5(id)
        return (hash[0].toInt() and 0xff) % partitions
    }

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
}