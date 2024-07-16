@file:OptIn(ExperimentalJsExport::class)

package naksha.psql

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic

/**
 * An abstract API that grants access to a single Postgres SQL connection. This interface is made in a way, so that it is naturally
 * compatible with [PLV8](https://plv8.github.io/). In Java there is a thin wrapper on top of a JDBC connection. In PLV8 this is a thin
 * wrapper around the native `plv8` SQL engine.
 */
@Suppress("DuplicatedCode")
@JsExport
interface PgConnection : AutoCloseable {
    /**
     * The session options. Changing the options requires a connection update, and therefore requires a database query to be executed in the background.
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
     * Calculate the MD5 hash above the given byte array. In [PLV8 extension](https://plv8.github.io/) this is implemented as `digest(bytes,'md5')`.
     * @param bytes the bytes above which to calculate the MD5 hash.
     * @return the MD5 hash as 16-byte long array (128-bit).
     */
    fun md5(bytes: ByteArray): ByteArray

    /**
     * Calculate the MD5 hash above the "C.UTF8" encoded bytes of the given string. In [PLV8 extension](https://plv8.github.io/) this is implemented as `digest(text,'md5')`.
     * @param text the text to hash, will be converted into "C.UTF8" encoding, before calculating the hash.
     * @return the MD5 hash as 16-byte long array (128-bit).
     */
    @JsName("md5_string")
    fun md5(text: String): ByteArray

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
     * Returns the partition number for the given amount of partitions.
     * @param conn the connection to use for hashing.
     * @param id the feature-id for which to return the partition-id.
     * @param partitions the number of partitions (1 to 256).
     * @return The partition number as value between 0 and part (exclusive).
     */
    fun partitionNumber(conn: PgConnection, id: String, partitions: Int): Int

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