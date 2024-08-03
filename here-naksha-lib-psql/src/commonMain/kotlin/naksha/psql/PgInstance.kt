package naksha.psql

import naksha.base.AtomicInt
import naksha.model.SessionOptions
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
     * The maximum amount of connections this instance can handle.
     *
     * There is no guarantee that a change of the limit is persisted, the instance can silently reject them. The client can check this, by reading the value, after writing it to verify, if the limit change. However, the recommendation is, that applications set the limit ones and then just use the value, whatever it is.
     *
     * All master instances must at least support admin-connections additionally to this limit.
     */
    var connectionLimit: Int

    /**
     * Returns a connection from the connection pool or opens a new connection. When the returned connection is closed, it will be
     * returned to the connection pool of the instance.
     *
     * - Throws [naksha.model.NakshaError.TOO_MANY_CONNECTIONS], if no more connections are available.
     * - Throws [naksha.model.NakshaError.ILLEGAL_ARGUMENT], if the instance is read-only (read-replica). and a write-connection is requested.
     * @param options the connection options.
     * @param readOnly if the connection should be read-only.
     * @return the connection.
     */
    fun openConnection(options: SessionOptions, readOnly: Boolean): PgConnection
}