package naksha.psql

import naksha.model.SessionOptions

/**
 * An abstract API that grants access to a single Postgres SQL connection.
 *
 * This interface is made in a way, so that it is naturally compatible with [PLV8](https://plv8.github.io/). In Java there is a thin wrapper on top of a JDBC connection. In PLV8 this is a thin wrapper around the native `plv8` SQL engine.
 * @param id a unique connection identifier for logging purpose.
 * @param instance the PostgresQL instance to which this connection belongs.
 * @param options the session options of the connection.
 * @since 3.0
 */
@Suppress("DuplicatedCode")
expect class PgConnection private constructor(id: Long, instance: PgInstance, options: SessionOptions): AutoCloseable {

    /**
     * The unique connection identifier for logging.
     * @since 3.0
     */
    val id: Long

    /**
     * The PostgresQL instance to which this connection belongs.
     * @since 3.0
     */
    val instance: PgInstance

    /**
     * The session options.
     *
     * Changing the options requires a connection update, and therefore requires a database query to be executed in the background.
     * @since 3.0
     */
    val options: SessionOptions

    /**
     * If auto-commit should be enabled; defaults to false.
     * @since 3.0
     */
    var autoCommit: Boolean

    /**
     * The URI of the database connection.
     * @param showPassword if explicitly _true_, the plain password is part of the URI, otherwise it will be obfuscated.
     * @return the URI of the database connection.
     * @since 3.0
     */
    fun toUri(showPassword: Boolean = false): String

    /**
     * Returns the URI of the database connection with the password set to `***`.
     * @return the URI of the database connection with the password set to `***`.
     * @since 3.0
     */
    override fun toString(): String

    /**
     * Execute an SQL query with the given arguments. The placeholder should be **$1** to **$n**.
     * @param sql The SQL query to execute.
     * @param args The arguments to be set at $n position, where $1 is the first array element.
     * @return the cursor.
     * @since 3.0
     */
    fun execute(sql: String, args: Array<Any?>? = null): PgCursor

    /**
     * Prepare the given SQL statement using parameters of the given types.
     * @param sql The SQL query to execute.
     * @param typeNames The name of the types of the arguments, to be at $n position, where $1 is the first array element.
     * @return The prepared plan.
     * @since 3.0
     */
    fun prepare(sql: String, typeNames: Array<String>? = null): PgPlan

    /**
     * Commit the underlying database connection.
     * @since 3.0
     */
    fun commit()

    /**
     * Rollback the underlying database connection.
     * @since 3.0
     */
    fun rollback()

    /**
     * Tests if this connection is closed.
     * @return _true_ if this connection is closed.
     * @since 3.0
     */
    fun isClosed(): Boolean

    /**
     * Rollback the underlying database connection, and return it to the connection pool. Any further invocation of any method of this object will raise a [IllegalStateException] from here on.
     * @since 3.0
     */
    override fun close()

    /**
     * Terminate the connection, what means closing it, and do not return to connection pool!
     * @since 3.0
     */
    fun terminate()
}
