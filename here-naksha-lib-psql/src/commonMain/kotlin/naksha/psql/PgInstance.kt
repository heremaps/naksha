package naksha.psql

import kotlin.js.JsExport

/**
 * Information about a PostgresQL instance.
 */
@Suppress("OPT_IN_USAGE")
@JsExport
interface PgInstance {
    /**
     * The host to connect to.
     */
    val host: String

    /**
     * The port to connect to.
     */
    val port: Int

    /**
     * The database to open.
     */
    val database: String

    /**
     * The user to authenticate with.
     */
    val user: String

    /**
     * The password to authenticate with.
     */
    val password: String

    /**
     * If the instance is a read-replica (read-only instance).
     */
    val readOnly: Boolean

    /**
     * The JDBC url. **Beware** that this URL does contain the password in clear text. If the URL with obfuscated password is needed, please
     * use [toString].
     *
     * Example: `jdbc:postgresql://{host}[:{port}]/{db}?user={user}&password={password}`
     */
    val url: String

    /**
     * Returns a session from the session pool or opens a new session. When the returned session is closed, it will be returned to the
     * session pool of the instance.
     * @param options the session options.
     * @return the session.
     * @throws IllegalArgumentException if the instance is read-only (read-replica) and a write-session is requested.
     */
    fun openConnection(options: PgOptions): PgConnection
}