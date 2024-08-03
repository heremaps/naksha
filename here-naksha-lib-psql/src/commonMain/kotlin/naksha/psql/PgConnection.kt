@file:OptIn(ExperimentalJsExport::class)

package naksha.psql

import naksha.model.SessionOptions
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
    var options: SessionOptions

    /**
     * If auto-commit should be enabled; defaults to false.
     */
    var autoCommit: Boolean

    /**
     * The URI of the database connection.
     * @param showPassword if explicitly _true_, the plain password is part of the URI, otherwise it will be obfuscated.
     * @return the URI of the database connection.
     */
    fun toUri(showPassword: Boolean = false): String

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
     * Rollback the underlying database connection, and return it to the connection pool. Any further invocation of any method of this
     * object will raise a [IllegalStateException] from here on.
     */
    override fun close()

    /**
     * Terminate the connection, what means closing it, and do not return to connection pool!
     */
    fun terminate()
}