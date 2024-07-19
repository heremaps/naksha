package naksha.psql

import naksha.base.Fnv1a32
import naksha.base.Platform.PlatformCompanion.logger
import org.postgresql.PGProperty.*
import org.postgresql.util.HostSpec
import java.lang.ref.WeakReference
import java.sql.ResultSet
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.min

/**
 * Information about a PostgresQL instance.
 */
class PsqlInstance : PgInstance {
    companion object {
        private const val EXPECTED_URL_FORMAT = "jdbc:postgresql://{host}[:{port}]/{db}?user={user}&password={password}"

        private val instancePool = ConcurrentHashMap<String, PsqlInstance>()
        private val connCounter = AtomicLong(1)

        @JvmStatic
        fun get(host: String, port: Int = 5432, database: String, user: String, password: String, readOnly: Boolean = false): PsqlInstance {
            val i = PsqlInstance(host, port, database, user, password, readOnly)
            val existing = instancePool.putIfAbsent(i.url, i)
            return existing ?: i
        }

        @JvmStatic
        fun get(url: String): PsqlInstance {
            var existing = instancePool[url]
            if (existing != null) return existing
            val i = PsqlInstance(url)
            existing = instancePool.putIfAbsent(i.url, i)
            return existing ?: i
        }
    }

    private constructor(host: String, port: Int = 5432, database: String, user: String, password: String, readOnly: Boolean = false) {
        this.hostSpec = HostSpec(host, port)
        this.database = database
        this.user = user
        this.password = password
        this.readOnly = readOnly
    }

    internal class PooledPgConnection(
        val jdbcConn: org.postgresql.jdbc.PgConnection,
        val id: Long = connCounter.getAndDecrement(),
        val session: AtomicReference<WeakReference<PsqlConnection>?> = AtomicReference(),
        var e: Exception? = null
    ) {
        fun setSession(session: PsqlConnection): Boolean {
            if (this.session.compareAndSet(null, session.weakRef)) {
                e = Exception()
                return true
            }
            return false
        }
    }


    /**
     * All open connections (the connection pool).
     */
    internal val connectionPool = ConcurrentHashMap<Long, PooledPgConnection>()

    private constructor(url: String) {
        // TODO: Improve parsing
        require(url.startsWith("jdbc:postgresql://")) { "The given URL should be like: $EXPECTED_URL_FORMAT" }
        val i = url.indexOf("?")
        require(i > 0) { "Missing query parameters, URL should be like: $EXPECTED_URL_FORMAT" }
        val params = url.substring(i + 1, url.length)
            .split("&")
            .map { it.split("=") }
            .groupBy({ it[0] }, { it[1] })
        val parts = url.substring("jdbc:postgresql://".length, i).split(':', '/')
        require(parts.size == 2 || parts.size == 3) { "The database URL is not well formatted, should be: $EXPECTED_URL_FORMAT" }
        val user = params["user"]?.get(0)
        require(user != null) { "Missing user parameter: '&user={user}'" }
        val password = params["password"]?.get(0)
        require(password != null) { "Missing password parameter: '&password={password}'" }
        val host: String = parts[0]
        val port: Int = if (parts.size == 2) 5432 else parts[1].toInt()
        val database: String = if (parts.size == 2) parts[1] else parts[2]
        this.hostSpec = HostSpec(host, port)
        this.database = database
        this.user = user
        this.password = password
        this.readOnly = params.contains("readOnly") || params.contains("readonly")
    }

    /**
     * The host specification.
     */
    val hostSpec: HostSpec

    /**
     * The host to connect to.
     */
    override val host: String
        get() = hostSpec.host

    /**
     * The port to connect to.
     */
    override val port: Int
        get() = hostSpec.port

    /**
     * The database to open.
     */
    override val database: String

    /**
     * The user to authenticate with.
     */
    override val user: String

    /**
     * The password to authenticate with.
     */
    override val password: String

    /**
     * If the instance is a read-replica (read-only instance).
     */
    override val readOnly: Boolean

    private var _url: String? = null

    /**
     * The JDBC url. **Beware** that this URL does contain the password in clear text.
     */
    override val url: String
        get() {
            var url = _url
            if (url == null) {
                url = "jdbc:postgresql://$host${if (port == 5432) "" else ":$port"}/$database?user=$user&password=$password"
                _url = url
            }
            return url
        }

    // TODO: Implement session (aka connection) pool!

    /**
     * Returns a connection from the connection pool or opens a new connection. When the returned connection is closed, it will be
     * returned to the instance connection pool.
     * @param options the connection options.
     * @return the connection.
     * @throws IllegalArgumentException if the instance is read-only (read-replica) and a write-connection is requested.
     */
    override fun openConnection(options: PgOptions): PsqlConnection {
        if (readOnly) require(options.readOnly) { "Failed to open a write connection to read-replica" }
        var psqlConn: PsqlConnection?

        val poolEnum = connectionPool.elements()
        while (poolEnum.hasMoreElements()) {
            val pooledConn = poolEnum.nextElement()
            val sessionRef = pooledConn.session.get()
            if (sessionRef != null) {
                psqlConn = sessionRef.get()
                if (psqlConn == null) {
                    logger.warn("Found PostgresQL database connection that was not closed: {}", pooledConn.e?.stackTraceToString())
                    connectionPool.remove(pooledConn.id, pooledConn)
                    try {
                        pooledConn.jdbcConn.close()
                    } catch (_: Exception) {
                    }
                }
                continue
            }
            // Idle connection found.
            psqlConn = PsqlConnection(this, pooledConn.id, pooledConn.jdbcConn, options)
            if (pooledConn.setSession(psqlConn)) {
                pooledConn.jdbcConn.isReadOnly = options.readOnly
                return psqlConn
            }
            // Concurrent allocation, another thread was faster, go on.
        }

        val props = Properties()
        props.setProperty(PG_DBNAME.getName(), database)
        props.setProperty(USER.getName(), user)
        props.setProperty(PASSWORD.getName(), password)
        props.setProperty(BINARY_TRANSFER.getName(), "true")
        props.setProperty(CONNECT_TIMEOUT.getName(), min(Int.MAX_VALUE, (options.connectTimeout / 1000L).toInt()).toString())
        props.setProperty(SOCKET_TIMEOUT.getName(), min(Int.MAX_VALUE, (options.socketTimeout / 1000L).toInt()).toString())
        //props.setProperty(CANCEL_SIGNAL_TIMEOUT.getName(), min(Int.MAX_VALUE, (? / 1000L).toDouble()).toString())
        //props.setProperty(RECEIVE_BUFFER_SIZE.getName(), receiveBufferSize.toString())
        //props.setProperty(SEND_BUFFER_SIZE.getName(), sendBufferSize.toString())
        props.setProperty(REWRITE_BATCHED_INSERTS.getName(), "true")
        val jdbcConn = org.postgresql.jdbc.PgConnection(arrayOf(hostSpec), props, url)
        jdbcConn.setAutoCommit(false)
        jdbcConn.setReadOnly(options.readOnly)
        jdbcConn.setHoldability(ResultSet.CLOSE_CURSORS_AT_COMMIT)

        val pooledConn = PooledPgConnection(jdbcConn)
        pooledConn.jdbcConn.isReadOnly = options.readOnly
        psqlConn = PsqlConnection(this, pooledConn.id, pooledConn.jdbcConn, options)
        psqlConn.execute("""
            SET SESSION search_path TO ${options.schema}, public, topology;
            SET SESSION enable_seqscan = OFF;
        """.trimIndent()).close()
        check(pooledConn.setSession(psqlConn))
        check(connectionPool.putIfAbsent(pooledConn.id, pooledConn) == null)
        return psqlConn
    }

    override fun equals(other: Any?): Boolean = other is PsqlInstance && url == other.url
    override fun hashCode(): Int = url.hashCode()
    private var _string: String? = null

    /**
     * Returns the JDBC URL of this instance, but the password is obfuscated (replace with a FNV1a hash).
     * @return the JDBC URL of this instance with obfuscated password.
     */
    override fun toString(): String {
        var string = _string
        if (string == null) {
            val pwdHash = Fnv1a32.string(0, password)
            string = "jdbc:postgresql://$host${if (port == 5432) "" else ":$port"}/$database?user=$user&password=$pwdHash"
            _string = string
        }
        return string
    }
}