package naksha.psql

import naksha.base.Platform.PlatformCompanion.logger
import naksha.base.fn.Fx2
import naksha.model.SessionOptions
import org.postgresql.PGProperty.*
import org.postgresql.util.HostSpec
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
        //private const val EXPECTED_URL_FORMAT = "jdbc:postgresql://{host}[:{port}]/{db}?user={user}&password={password}"

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
        val id: Long = connCounter.getAndIncrement(),
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

    private fun toSeconds(millis: Int, minMillis: Int = 1000): Int = max(0, max(minMillis, millis) / 1000)

    /**
     * This variable specifies the order in which schemas are searched when an object (table, data type, function, etc.) is referenced by a simple name with no schema specified. When there are objects of identical names in different schemas, the one found first in the search path is used. An object that is not in any of the schemas in the search path can only be referenced by specifying its containing schema with a qualified (dotted) name.
     *
     * The value for search_path must be a comma-separated list of schema names. Any name that is not an existing schema, or is a schema for which the user does not have USAGE permission, is silently ignored.
     *
     * If one of the list items is the special name $user, then the schema having the name returned by CURRENT_USER is substituted, if there is such a schema and the user has USAGE permission for it. (If not, $user is ignored.)
     *
     * The system catalog schema, pg_catalog, is always searched, whether it is mentioned in the path or not. If it is mentioned in the path then it will be searched in the specified order. If pg_catalog is not in the path then it will be searched before searching any of the path items.
     *
     * Likewise, the current session's temporary-table schema, pg_temp_nnn, is always searched if it exists. It can be explicitly listed in the path by using the alias pg_temp. If it is not listed in the path then it is searched first (even before pg_catalog). However, the temporary schema is only searched for relation (table, view, sequence, etc.) and data type names. It is never searched for function or operator names.
     *
     * When objects are created without specifying a particular target schema, they will be placed in the first valid schema named in search_path. An error is reported if the search path is empty.
     *
     * The default value for this parameter is "$user", public. This setting supports shared use of a database (where no users have private schemas, and all share use of public), private per-user schemas, and combinations of these. Other effects can be obtained by altering the default search path setting, either globally or per-user.
     *
     * For more information on schema handling, see Section 5.10. In particular, the default configuration is suitable only when the database has a single user or a few mutually-trusting users.
     *
     * The current effective value of the search path can be examined via the SQL function current_schemas (see Section 9.27). This is not quite the same as examining the value of search_path, since current_schemas shows how the items appearing in search_path were resolved.
     *
     * - [search_path](https://www.postgresql.org/docs/current/runtime-config-client.html#GUC-SEARCH-PATH)
     */
    private val search_path = "search_path"

    /**
     * Terminate any session that has been idle (that is, waiting for a client query) within an open transaction for longer than the specified amount of time. If this value is specified without units, it is taken as milliseconds. A value of zero (the default) disables the timeout.
     *
     * This option can be used to ensure that idle sessions do not hold locks for an unreasonable amount of time. Even when no significant locks are held, an open transaction prevents vacuuming away recently-dead tuples that may be visible only to this transaction; so remaining idle for a long time can contribute to table bloat. See Section 24.1 for more details.
     *
     * - [idle_in_transaction_session_timeout](https://www.postgresql.org/docs/current/runtime-config-client.html#GUC-IDLE-IN-TRANSACTION-SESSION-TIMEOUT)
     */
    private val idle_in_transaction_session_timeout = "idle_in_transaction_session_timeout" // 1 second minimum

    /**
     * Terminate any session that has been idle (that is, waiting for a client query), but not within an open transaction, for longer than the specified amount of time. If this value is specified without units, it is taken as milliseconds. A value of zero (the default) disables the timeout.
     *
     * Unlike the case with an open transaction, an idle session without a transaction imposes no large costs on the server, so there is less need to enable this timeout than idle_in_transaction_session_timeout.
     *
     * Be wary of enforcing this timeout on connections made through connection-pooling software or other middleware, as such a layer may not react well to unexpected connection closure. It may be helpful to enable this timeout only for interactive sessions, perhaps by applying it only to particular users.
     *
     * - [idle_session_timeout](https://www.postgresql.org/docs/current/runtime-config-client.html#GUC-IDLE-SESSION-TIMEOUT)
     */
    private val idle_session_timeout = "idle_session_timeout"

    /**
     * Terminate any session that spans longer than the specified amount of time in a transaction. The limit applies both to explicit transactions (started with BEGIN) and to an implicitly started transaction corresponding to a single statement. If this value is specified without units, it is taken as milliseconds. A value of zero (the default) disables the timeout.
     *
     * If transaction_timeout is shorter or equal to idle_in_transaction_session_timeout or statement_timeout then the longer timeout is ignored.
     *
     * Setting transaction_timeout in postgresql.conf is not recommended because it would affect all sessions.
     *
     * - [transaction_timeout](https://www.postgresql.org/docs/current/runtime-config-client.html#GUC-TRANSACTION-TIMEOUT)
     */
    private val transaction_timeout = "transaction_timeout"
    // TODO: This is new in PostgresQL 17, therefore we keep track of it and should use it, when possible

    /**
     * Abort any statement that takes more than the specified amount of time. If log_min_error_statement is set to ERROR or lower, the statement that timed out will also be logged. If this value is specified without units, it is taken as milliseconds. A value of zero (the default) disables the timeout.
     *
     * The timeout is measured from the time a command arrives at the server until it is completed by the server. If multiple SQL statements appear in a single simple-query message, the timeout is applied to each statement separately. (PostgreSQL versions before 13 usually treated the timeout as applying to the whole query string.) In extended query protocol, the timeout starts running when any query-related message (Parse, Bind, Execute, Describe) arrives, and it is canceled by completion of an Execute or Sync message.
     *
     * Setting statement_timeout in postgresql.conf is not recommended because it would affect all sessions.
     *
     * - [statement_timeout](https://www.postgresql.org/docs/current/runtime-config-client.html#GUC-STATEMENT-TIMEOUT)
     */
    private val statement_timeout = "statement_timeout"

    /**
     * Abort any statement that waits longer than the specified amount of time while attempting to acquire a lock on a table, index, row, or other database object. The time limit applies separately to each lock acquisition attempt. The limit applies both to explicit locking requests (such as LOCK TABLE, or SELECT FOR UPDATE without NOWAIT) and to implicitly-acquired locks. If this value is specified without units, it is taken as milliseconds. A value of zero (the default) disables the timeout.
     *
     * Unlike statement_timeout, this timeout can only occur while waiting for locks. Note that if statement_timeout is nonzero, it is rather pointless to set lock_timeout to the same or larger value, since the statement timeout would always trigger first. If log_min_error_statement is set to ERROR or lower, the statement that timed out will be logged.
     *
     * Setting lock_timeout in postgresql.conf is not recommended because it would affect all sessions.
     *
     * - [lock_timeout](https://www.postgresql.org/docs/current/runtime-config-client.html#GUC-LOCK-TIMEOUT)
     */
    private val lock_timeout = "lock_timeout"

    /**
     * Sets the base maximum amount of memory to be used by a query operation (such as a sort or hash table) before writing to temporary disk files. If this value is specified without units, it is taken as kilobytes. The default value is four megabytes (4MB). Note that a complex query might perform several sort and hash operations at the same time, with each operation generally being allowed to use as much memory as this value specifies before it starts to write data into temporary files. Also, several running sessions could be doing such operations concurrently. Therefore, the total memory used could be many times the value of work_mem; it is necessary to keep this fact in mind when choosing the value. Sort operations are used for ORDER BY, DISTINCT, and merge joins. Hash tables are used in hash joins, hash-based aggregation, memoize nodes and hash-based processing of IN subqueries.
     *
     * Hash-based operations are generally more sensitive to memory availability than equivalent sort-based operations. The memory limit for a hash table is computed by multiplying work_mem by hash_mem_multiplier. This makes it possible for hash-based operations to use an amount of memory that exceeds the usual work_mem base amount.
     *
     * - [work_mem](https://www.postgresql.org/docs/current/runtime-config-resource.html#GUC-WORK-MEM)
     */
    private val work_mem = "work_mem"

    /**
     * We need to ensure that this connection is not dead, we use the initialization for this test.
     * @param psqlConn the PostgresQL connection to test and initialize.
     * @param options the [SessionOptions] to be used with this connection.
     * @param readOnly if the session should be used read-only.
     * @param init the optional init function to be invoked.
     * @return the connection and if the initialization went `ok` _(true)_.
     */
    private fun initPsqlConnection(
        psqlConn: PsqlConnection,
        options: SessionOptions,
        readOnly: Boolean,
        init: Fx2<PgConnection, String>?): Pair<PsqlConnection, Boolean>
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
            psqlConn = PsqlConnection(this, pooledConn.id, pooledConn.jdbcConn, options)
            if (pooledConn.setSession(psqlConn)) {
                val (conn, ok) = initPsqlConnection(psqlConn, options, readOnly, init)
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
        props.setProperty(PG_DBNAME.getName(), database)
        props.setProperty(USER.getName(), user)
        props.setProperty(PASSWORD.getName(), password)
        props.setProperty(BINARY_TRANSFER.getName(), "true")
        props.setProperty(CONNECT_TIMEOUT.getName(), min(Int.MAX_VALUE, (options.connectTimeout / 1000L).toInt()).toString())
        props.setProperty(SOCKET_TIMEOUT.getName(), min(Int.MAX_VALUE, max(1, (options.socketTimeout / 1000L).toInt())).toString())
        //props.setProperty(CANCEL_SIGNAL_TIMEOUT.getName(), min(Int.MAX_VALUE, (? / 1000L).toDouble()).toString())
        //props.setProperty(RECEIVE_BUFFER_SIZE.getName(), receiveBufferSize.toString())
        //props.setProperty(SEND_BUFFER_SIZE.getName(), sendBufferSize.toString())
        props.setProperty(REWRITE_BATCHED_INSERTS.getName(), "true")
        // https://www.reddit.com/r/PostgreSQL/comments/o4ptz9/on_expensive_count_and_similar_queries_even_with/
        props.setProperty(PREFER_QUERY_MODE.getName(), "extendedForPrepared") // "simple" preferred, but not possible for prepared statements, to encourage parallelism
        val jdbcConn = org.postgresql.jdbc.PgConnection(arrayOf(hostSpec), props, url)
        val pooledConn = PooledPgConnection(jdbcConn)
        psqlConn = PsqlConnection(this, pooledConn.id, pooledConn.jdbcConn, options)
        check(pooledConn.setSession(psqlConn))
        check(connectionPool.putIfAbsent(pooledConn.id, pooledConn) == null)
        val (conn, ok) = initPsqlConnection(psqlConn, options, readOnly, init)
        require(ok) { "Failed to initialize connection" }
        logger.info("Use new pooled connection: ${pooledConn.id}")
        return conn
    }

    override fun equals(other: Any?): Boolean = other is PsqlInstance && url == other.url
    override fun hashCode(): Int = url.hashCode()

    /**
     * Returns the JDBC URL of this instance.
     * @return the JDBC URL of this instance with obfuscated password.
     */
    override fun toString(): String = url
}
