@file:OptIn(ExperimentalJsExport::class)

package naksha.plv8

import naksha.base.ObjectProxy
import naksha.model.NakshaContext
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * An abstract API that grants access to a single Postgres SQL connection. This interface is made in a way, so that it is
 * naturally compatible with [PLV8](https://plv8.github.io/). In Java there is a thin wrapper on top of a JDBC connection.
 */
@Suppress("DuplicatedCode")
@JsExport
interface PgSession : AutoCloseable {
    /**
     * Returns general information about the database to which this API grants access.
     */
    fun info(): PgDbInfo

    /**
     * The session options.
     */
    var options: PgSessionOptions

    /**
     * Tests if the given parameter stores the number of affected rows and if it does, returns
     * the number. If not, returns _null_.
     * @param any The object to test.
     * @return The number of affected rows or _null_.
     */
    fun affectedRows(any: Any): Int?

    /**
     * Tests if the given parameter is an array of rows, and if it is, returns the rows
     * as array of native maps.
     * @param any The object to test.
     * @return The array of native maps or _null_, if _any_ is no valid rows.
     */
    fun rows(any: Any): Array<ObjectProxy>?

    /**
     * Execute an SQL query with the given arguments. The placeholder should be **$1** to **$n**.
     * @param sql The SQL query to execute.
     * @param args The arguments to be set at $n position, where $1 is the first array element.
     * @return Either the [number of affected rows][affectedRows] or the [fetched rows][rows].
     */
    fun execute(sql: String, args: Array<Any?>? = null): Any

    /**
     * Prepare the given SQL statement using parameters of the given types.
     * @param sql The SQL query to execute.
     * @param typeNames The name of the types of the arguments, to be at $n position, where $1 is the first array element.
     * @return The prepared plan.
     */
    fun prepare(sql: String, typeNames: Array<String>? = null):PgPlan

    fun executeBatch(plan:PgPlan, bulkParams: Array<Array<Param>>): IntArray

    /**
     * Compress bytes using GZip.
     * @param raw The bytes to compress.
     * @return The deflated (compressed) bytes.
     */
    fun gzipCompress(raw: ByteArray): ByteArray

    /**
     * Decompress bytes compressed by GZip.
     * @param compressed The bytes to decompress.
     * @return The inflated (decompress) bytes.
     */
    fun gzipDecompress(compressed: ByteArray): ByteArray

    /**
     * Commit the underlying database connection.
     */
    fun commit()

    /**
     * Rollback the underlying database connection.
     */
    fun rollback()

    /**
     * Rollback the underlying database connection and return it to the connection pool. Any further invocation of any method of this
     * object will raise a [IllegalStateException] from here on.
     */
    override fun close()
}