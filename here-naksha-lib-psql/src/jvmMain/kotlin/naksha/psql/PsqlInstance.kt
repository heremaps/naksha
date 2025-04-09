package naksha.psql

import naksha.base.AtomicInt
import naksha.base.Platform.PlatformCompanion.logger
import naksha.base.fn.Fx2
import naksha.model.SessionOptions
import org.postgresql.PGProperty.*
import org.postgresql.util.HostSpec
import org.postgresql.util.PSQLException
import java.lang.ref.WeakReference
import java.sql.ResultSet
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.max
import kotlin.math.min

/**
 * Information about a PostgresQL instance.
 */
class PsqlInstance(private val config: PgInstanceConfig) : PgInstance {
    companion object {
        private const val EXPECTED_URL_FORMAT = "jdbc:postgresql://{host}[:{port}]/{db}?user={user}&password={password}"

        private val instancePool = ConcurrentHashMap<String, PsqlInstance>()
        private val connCounter = AtomicLong(1)

        /**
         * Return the _Postgres Server Instance_ for the given connection data.
         * @param host the host of the PostgresQL server.
         * @param port the post of the PostgresQL server.
         * @param database the database to connect to.
         * @param user the user to authenticate against the server.
         * @param password the password to authenticate against the server.
         * @param readOnly if the server is a read-replicate _(read-only instance)_.
         */
        @JvmStatic
        @JvmOverloads
        fun get(host: String, port: Int = 5432, database: String, user: String, password: String, readOnly: Boolean = false): PsqlInstance
            = get(PgInstanceConfig()
                .withHost(host).withPort(port)
                .withDb(database).withUser(user).withPassword(password)
                .withReadOnly(readOnly))

        /**
         * Return the _Postgres Server Instance_ for the given connection data.
         * @param uri the [JDBC connection string](https://jdbc.postgresql.org/documentation/use/) of the PostgresQL server, for example `jdbc:postgresql://localhost:5432/testdb?user=fred&password=secret&ssl=true`.
         */
        @JvmStatic
        fun get(uri: String): PsqlInstance
            = get(PgInstanceConfig.fromUri(uri))

        /**
         * Return the _Postgres Server Instance_ for the given instance configuration.
         * @param instanceConfig the instance configuration.
         * @return the instance.
         */
        fun get(instanceConfig: PgInstanceConfig): PsqlInstance {
            var existing = instancePool[instanceConfig.toString()]
            if (existing != null) return existing
            val i = PsqlInstance(instanceConfig)
            existing = instancePool.putIfAbsent(i.url, i)
            return existing ?: i
        }
    }

    internal data class PooledPgConnection(
        val jdbcConn: org.postgresql.jdbc.PgConnection,
        val id: Long = connCounter.getAndDecrement(),
        val idle: AtomicInteger = AtomicInteger(0),
        val connection: AtomicReference<WeakReference<PsqlConnection>?> = AtomicReference(),
        var e: Exception? = null
    ) {
        fun setSession(session: PsqlConnection): Boolean {
            if (this.connection.compareAndSet(null, session.weakRef)) {
                e = Exception()
                idle.set(0)
                return true
            }
            return false
        }
    }

    /**
     * All open connections (the connection pool).
     */
    internal val connectionPool = ConcurrentHashMap<Long, PooledPgConnection>()

    /**
     * The host specification.
     */
    val hostSpec: HostSpec = HostSpec(config.host, config.port)

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
        get() = config.db

    /**
     * The user to authenticate with.
     */
    override val user: String
        get() = config.user

    /**
     * The password to authenticate with.
     */
    override val password: String
        get() = config.password

    /**
     * If the instance is a read-replica (read-only instance).
     */
    override val readOnly: Boolean
        get() = config.readOnly

    /**
     * The JDBC url. **Beware** that this URL does contain the password in clear text.
     */
    override val url: String
        get() = config.toString()

    override var connectionLimit: Int
        get() = config.connectionLimit
        set(value) {
            config.connectionLimit = min(8192, max(0, value))
        }

    override fun openConnection(options: SessionOptions, readOnly: Boolean, init: Fx2<PgConnection, String>?): PsqlConnection {
        if (this.readOnly) require(readOnly) { "Failed to open a write connection to read-replica" }
        var psqlConn: PsqlConnection?

        val poolEnum = connectionPool.elements()
        while (poolEnum.hasMoreElements()) {
            val pooledConn = poolEnum.nextElement()
            val connectionRef = pooledConn.connection.get()
            if (connectionRef != null) {
                psqlConn = connectionRef.get()
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
                pooledConn.jdbcConn.isReadOnly = readOnly
                // We need to ensure that this connection is not dead, we use the initialization for this test.
                try {
                    val query = """SET SESSION search_path TO "naksha~admin", hint_plan, public, topology;
SET SESSION work_mem = '64MB';
SET SESSION idle_in_transaction_session_timeout = '30s';"""
                    if (init != null) init.call(psqlConn, query) else psqlConn.execute(query).close()
                } catch (_: Exception) {
                    connectionPool.remove(pooledConn.id)
                    continue
                }
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
        jdbcConn.setReadOnly(readOnly)
        jdbcConn.setHoldability(ResultSet.CLOSE_CURSORS_AT_COMMIT)

        val pooledConn = PooledPgConnection(jdbcConn)
        pooledConn.jdbcConn.isReadOnly = readOnly
        psqlConn = PsqlConnection(this, pooledConn.id, pooledConn.jdbcConn, options)
        check(pooledConn.setSession(psqlConn))
        check(connectionPool.putIfAbsent(pooledConn.id, pooledConn) == null)
        return psqlConn
    }

    override fun equals(other: Any?): Boolean = other is PsqlInstance && url == other.url
    override fun hashCode(): Int = url.hashCode()

    /**
     * Returns the JDBC URL of this instance.
     * @return the JDBC URL of this instance with obfuscated password.
     */
    override fun toString(): String = url
}