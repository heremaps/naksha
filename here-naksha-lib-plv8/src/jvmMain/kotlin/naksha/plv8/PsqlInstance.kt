package com.here.naksha.lib.plv8.naksha.plv8

import org.postgresql.PGProperty
import org.postgresql.PGProperty.*
import org.postgresql.jdbc.PgConnection
import org.postgresql.util.HostSpec
import java.sql.ResultSet
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min

/**
 * Information about a PostgresQL instance.
 */
class PsqlInstance {
    companion object {
        private const val EXPECTED_URL_FORMAT = "jdbc:postgresql://{host}[:{port}]/{db}?user={user}&password={password}"

        // TODO: Implement auto-closer of idle connections
        // TODO: Implement keep-alive of idle connections
        private val instancePool = ConcurrentHashMap<String, PsqlInstance>()

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
            existing = instancePool.putIfAbsent(url, i)
            return existing ?: i
        }
    }

    private constructor(host: String, port: Int = 5432, database: String, user: String, password: String, readOnly: Boolean = false) {
        this.hostSpec = HostSpec(host, port)
        this.database = database
        this.user = user
        this.password = password
        this.readOnly = readOnly
        this.url = "jdbc:postgresql://$host${if (port == 5432) "" else ":$port"}/$database?user=$user&password=$password"
    }

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
        this.url = url
    }

    /**
     * The host specification.
     */
    val hostSpec: HostSpec

    /**
     * The host to connect to.
     */
    val host: String
        get() = hostSpec.host

    /**
     * The port to connect to.
     */
    val port: Int
        get() = hostSpec.port

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
     * The JDBC url.
     */
    val url: String

    // TODO: Implement connection pool!

    /**
     * Returns a new connection from the pool, when it is closed, it will be returned to the pool.
     * @param options the connection options.
     * @throws IllegalArgumentException If the instance is read-only and a write-connection is requested.
     */
    fun getConnection(options: PsqlConnectOptions): PsqlConnection {
        if (readOnly) require(options.readOnly) { "Failed to open a write connection to read-replica" }
        val props = Properties()
        props.setProperty(PG_DBNAME.getName(), database)
        props.setProperty(USER.getName(), user)
        props.setProperty(PASSWORD.getName(), password)
        props.setProperty(BINARY_TRANSFER.getName(), "true")
        if (readOnly) props.setProperty(READ_ONLY.getName(), "true")
        props.setProperty(CONNECT_TIMEOUT.getName(), min(Int.MAX_VALUE, (options.connectTimeout / 1000L).toInt()).toString())
        props.setProperty(SOCKET_TIMEOUT.getName(), min(Int.MAX_VALUE, (options.socketTimeout / 1000L).toInt()).toString())
        //props.setProperty(CANCEL_SIGNAL_TIMEOUT.getName(), min(Int.MAX_VALUE, (? / 1000L).toDouble()).toString())
        //props.setProperty(RECEIVE_BUFFER_SIZE.getName(), receiveBufferSize.toString())
        //props.setProperty(SEND_BUFFER_SIZE.getName(), sendBufferSize.toString())
        props.setProperty(REWRITE_BATCHED_INSERTS.getName(), "true")
        val conn = PgConnection(arrayOf(hostSpec), Properties(), url)
        conn.setAutoCommit(false)
        conn.setReadOnly(options.readOnly)
        conn.setHoldability(ResultSet.CLOSE_CURSORS_AT_COMMIT)
        return PsqlConnection(this, conn, options)
    }

    override fun equals(other: Any?): Boolean = other is PsqlInstance && url == other.url
    override fun hashCode(): Int = url.hashCode()
    override fun toString(): String = url
}