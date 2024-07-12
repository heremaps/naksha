@file:OptIn(ExperimentalJsExport::class)

package naksha.psql

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * An abstract API that grants access to a single Postgres SQL connection. This interface is made in a way, so that it is naturally
 * compatible with [PLV8](https://plv8.github.io/). In Java there is a thin wrapper on top of a JDBC connection. In PLV8 this is a thin
 * wrapper around the native `plv8` SQL engine.
 */
@Suppress("DuplicatedCode")
@JsExport
interface PgConnection : AutoCloseable {
    /**
     * Returns general information about the database to which this API grants access.
     */
    fun info(): PgInfo

    /**
     * The session options.
     */
    var options: PgOptions

    /**
     * Execute an SQL query with the given arguments. The placeholder should be **$1** to **$n**.
     * @param sql The SQL query to execute.
     * @param args The arguments to be set at $n position, where $1 is the first array element.
     * @return the cursor.
     */
    fun execute(sql: String, args: Array<Any?>? = null): PgCursor

    /**
     * Prepare the given SQL statement using parameters of the given types.
     * @param sql The SQL query to execute.
     * @param typeNames The name of the types of the arguments, to be at $n position, where $1 is the first array element.
     * @return The prepared plan.
     */
    fun prepare(sql: String, typeNames: Array<String>? = null): PgPlan

    /**
     * Use this database connection to compress bytes using `gzip`. This requires a database connection for some implementations, for
     * example when execute in [PLV8 extension](https://plv8.github.io/), where it will need to perform a `select gzip(...)` query for this.
     * @param raw the bytes to compress.
     * @return The deflated (compressed) bytes.
     * @throws UnsupportedOperationException if the platform does not support this operation.
     */
    fun gzip(raw: ByteArray): ByteArray

    /**
     * Use this database connection to decompress bytes using `gzip`. This requires a database connection for some implementations, for
     * example when execute in [PLV8 extension](https://plv8.github.io/), where it will need to perform a `select gunzip(...)` query for
     * this.
     * @param compressed the bytes to decompress.
     * @return the inflated (decompressed) bytes.
     * @throws UnsupportedOperationException if the platform does not support this operation.
     */
    fun gunzip(compressed: ByteArray): ByteArray

    /**
     * Commit the underlying database connection.
     */
    fun commit()

    /**
     * Rollback the underlying database connection.
     */
    fun rollback()

    /**
     * Tests if this connection is closed.
     * @return _true_ if this connection is closed.
     */
    fun isClosed(): Boolean

    /**
     * Rollback the underlying database connection and return it to the connection pool. Any further invocation of any method of this
     * object will raise a [IllegalStateException] from here on.
     */
    override fun close()
}