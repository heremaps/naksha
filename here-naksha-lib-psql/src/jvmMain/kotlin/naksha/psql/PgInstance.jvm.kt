package naksha.psql

import naksha.base.Platform.PlatformCompanion.logger
import naksha.base.fn.Fx2
import naksha.base.illegalArg
import naksha.model.SessionOptions
import org.postgresql.PGProperty
import org.postgresql.util.HostSpec
import java.lang.ref.WeakReference
import java.sql.ResultSet
import java.util.Properties
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.max
import kotlin.math.min


/**
 * Information about a PostgresQL instance.
 */
actual class PgInstance private actual constructor(private val config: PgInstanceConfig) {
    actual companion object PgInstance_C {
        private val instancePool = ConcurrentHashMap<String, PgInstance>()
        private val connId = AtomicLong(1)

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
        fun get(host: String, port: Int = 5432, database: String, user: String, password: String, readOnly: Boolean = false): PgInstance
                = of(PgInstanceConfig()
            .withHost(host).withPort(port)
            .withDb(database).withUser(user).withPassword(password)
            .withReadOnly(readOnly))

        /**
         * Return the _Postgres Server Instance_ for the given connection data.
         * @param uri the [JDBC connection string](https://jdbc.postgresql.org/documentation/use/) of the PostgresQL server, for example `jdbc:postgresql://localhost:5432/testdb?user=fred&password=secret&ssl=true`.
         */
        @JvmStatic
        fun get(uri: String): PgInstance = of(PgInstanceConfig.fromUri(uri))

        @JvmStatic
        actual fun of(config: PgInstanceConfig): PgInstance {
            var existing = instancePool[config.toString()]
            if (existing != null) return existing
            val i = PgInstance(config)
            existing = instancePool.putIfAbsent(i.url, i)
            return existing ?: i
        }
    }

    internal data class PooledPgConnection(
        val jdbcConn: org.postgresql.jdbc.PgConnection,
        val id: Long,
        val idle: AtomicInteger = AtomicInteger(0),
        val connection: AtomicReference<WeakReference<PgConnection>?> = AtomicReference(),
        var e: Exception? = null
    ) {
        fun setSession(session: PgConnection): Boolean {
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
    actual val host: String
        get() = hostSpec.host

    /**
     * The port to connect to.
     */
    actual val port: Int
        get() = hostSpec.port

    /**
     * The database to open.
     */
    actual val database: String
        get() = config.db

    /**
     * The user to authenticate with.
     */
    actual val user: String
        get() = config.user

    /**
     * The password to authenticate with.
     */
    actual val password: String
        get() = config.password

    /**
     * If the instance is a read-replica (read-only instance).
     */
    actual val readOnly: Boolean
        get() = config.readOnly

    /**
     * The JDBC url. **Beware** that this URL does contain the password in clear text.
     */
    actual val url: String
        get() = config.toString()

    actual var connectionLimit: Int
        get() = config.connectionLimit
        set(value) {
            config.connectionLimit = min(8192, max(0, value))
        }

    private fun toSeconds(millis: Int, minMillis: Int = 1000): Int = max(0, max(minMillis, millis) / 1000)

    /**
     * We need to ensure that this connection is not dead, we use the initialization for this test.
     * @param psqlConn the PostgresQL connection to test and initialize.
     * @param options the [SessionOptions] to be used with this connection.
     * @param readOnly if the session should be used read-only.
     * @param init the optional init function to be invoked.
     * @return the connection and if the initialization went `ok` _(true)_.
     */
    private fun initPgConnection(
        psqlConn: PgConnection,
        options: SessionOptions,
        readOnly: Boolean,
        init: Fx2<PgConnection, String>?): Pair<PgConnection, Boolean>
    {
        val jdbcConn = psqlConn.jdbc
        try {
            jdbcConn.autoCommit = false
            jdbcConn.holdability = ResultSet.CLOSE_CURSORS_AT_COMMIT
            jdbcConn.isReadOnly = readOnly
            jdbcConn.defaultFetchSize = 1000
            if (jdbcConn.networkTimeout != options.socketTimeout) {
                jdbcConn.setNetworkTimeout(null, max(5_000, options.socketTimeout))
            }
            // We need to ensure that, when reusing session, we fall back to defaults for most commonly modified session properties!
            val query = """SET SESSION $search_path TO "naksha~admin", hint_plan, public, topology;
SET SESSION $work_mem = '128MB';
SET SESSION $idle_session_timeout = '600s';
SET SESSION $statement_timeout = '${toSeconds(options.stmtTimeout)}s';
SET SESSION $lock_timeout = '${toSeconds(options.lockTimeout, 0)}s';
SET SESSION $idle_in_transaction_session_timeout = '${toSeconds(options.idleTxTimeout)}s';
SET SESSION enable_async_append = on;
SET SESSION max_parallel_workers = 128;
SET SESSION max_parallel_workers_per_gather = 16;
SET SESSION parallel_setup_cost = 1;   
SET SESSION parallel_tuple_cost = 0.01;
SET SESSION enable_partition_pruning = on;
SET SESSION enable_partitionwise_join = on;
SET SESSION enable_partitionwise_aggregate = on;
SET SESSION enable_gathermerge = on;
SET SESSION enable_seqscan = off;
SET SESSION enable_bitmapscan = on;
SET SESSION enable_indexscan = on;
SET SESSION enable_indexonlyscan = on;
SET SESSION enable_nestloop = on;
SET SESSION enable_sort = off;
SET SESSION pg_hint_plan.enable_hint = on;
"""
            // TODO: We need to fix this for our docker container, it complains about that the table does not exist
            //       SET SESSION pg_hint_plan.enable_hint_table = on;

            //TODO and these 2 cause extremely long collection creation time, and they might not even be relevant to postgres planner?
            //   SET SESSION min_parallel_table_scan_size = 0;
            //   SET SESSION min_parallel_index_scan_size = 0;

            // Note: bitmap scans can be become really bad, when combined with gather!
            // We can tune things later, changing the session manually to other values, as anyway every session will reset to default now!
            if (init != null) init.call(psqlConn, query) else psqlConn.execute(query).close()
            return Pair(psqlConn, true)
        } catch (e: Exception) {
            logger.info("Connection ${psqlConn.id} failed to initialize, reason: {}", e.message)
            return Pair(psqlConn, false)
        }
    }

    actual fun openConnection(options: SessionOptions, readOnly: Boolean, init: Fx2<PgConnection, String>?): PgConnection {
        if (this.readOnly && !readOnly) throw illegalArg("Failed to open a write connection to read-replica")
        var pgConn: PgConnection?

        val poolEnum = connectionPool.elements()
        while (poolEnum.hasMoreElements()) {
            val pooledConn = poolEnum.nextElement()
            val connectionRef = pooledConn.connection.get()
            if (connectionRef != null) {
                pgConn = connectionRef.get()
                if (pgConn == null) {
                    logger.warn("Remove idle pooled connection (${pooledConn.id}), because it was not closed: {}", pooledConn.e?.stackTraceToString())
                    connectionPool.remove(pooledConn.id, pooledConn)
                    try {
                        pooledConn.jdbcConn.close()
                    } catch (_: Exception) {
                    }
                }
                continue
            }
            // Idle connection found.
            pgConn = PgConnection(pooledConn.id, this, options, pooledConn.jdbcConn)
            if (pooledConn.setSession(pgConn)) {
                val (conn, ok) = initPgConnection(pgConn, options, readOnly, init)
                if (!ok) {
                    logger.info("Remove connection ${pooledConn.id} from pool, because it is broken")
                    connectionPool.remove(pooledConn.id)
                    continue
                }
                logger.info("Use existing pooled connection: ${pooledConn.id}")
                return conn
            }
            // Concurrent allocation, another thread was faster, go on.
        }

        val props = Properties()
        props.setProperty(PGProperty.PG_DBNAME.getName(), database)
        props.setProperty(PGProperty.USER.getName(), user)
        props.setProperty(PGProperty.PASSWORD.getName(), password)
        props.setProperty(PGProperty.BINARY_TRANSFER.getName(), "true")
        props.setProperty(PGProperty.CONNECT_TIMEOUT.getName(), min(Int.MAX_VALUE, (options.connectTimeout / 1000L).toInt()).toString())
        props.setProperty(PGProperty.SOCKET_TIMEOUT.getName(), min(Int.MAX_VALUE, max(1, (options.socketTimeout / 1000L).toInt())).toString())
        //props.setProperty(CANCEL_SIGNAL_TIMEOUT.getName(), min(Int.MAX_VALUE, (? / 1000L).toDouble()).toString())
        //props.setProperty(RECEIVE_BUFFER_SIZE.getName(), receiveBufferSize.toString())
        //props.setProperty(SEND_BUFFER_SIZE.getName(), sendBufferSize.toString())
        props.setProperty(PGProperty.REWRITE_BATCHED_INSERTS.getName(), "true")
        // https://www.reddit.com/r/PostgreSQL/comments/o4ptz9/on_expensive_count_and_similar_queries_even_with/
        props.setProperty(PGProperty.PREFER_QUERY_MODE.getName(), "extendedForPrepared") // "simple" preferred, but not possible for prepared statements, to encourage parallelism
        val jdbcConn = org.postgresql.jdbc.PgConnection(arrayOf(hostSpec), props, url)
        val pooledConn = PooledPgConnection(jdbcConn, connId.getAndAdd(1))
        pgConn = PgConnection(pooledConn.id, this, options, pooledConn.jdbcConn)
        check(pooledConn.setSession(pgConn))
        check(connectionPool.putIfAbsent(pooledConn.id, pooledConn) == null)
        val (conn, ok) = initPgConnection(pgConn, options, readOnly, init)
        require(ok) { "Failed to initialize connection" }
        logger.info("Use new pooled connection: ${pooledConn.id}")
        return conn
    }

    override fun equals(other: Any?): Boolean = other is PgInstance && url == other.url
    override fun hashCode(): Int = url.hashCode()

    /**
     * Returns the JDBC URL of this instance.
     * @return the JDBC URL of this instance with obfuscated password.
     */
    override fun toString(): String = url
}
