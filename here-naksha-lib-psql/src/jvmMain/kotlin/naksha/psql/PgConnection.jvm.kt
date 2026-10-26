package naksha.psql

import naksha.base.NakshaException
import naksha.base.illegalState
import naksha.model.SessionOptions
import java.lang.ref.WeakReference

actual class PgConnection private actual constructor(id: Long, instance: PgInstance, options: SessionOptions): AutoCloseable {

    /**
     * Internal Java-only constructor to create a new instance with an underlying real JDBC connection.
     * @since 3.0
     */
    internal constructor(id: Long, instance: PgInstance, options: SessionOptions, jdbc: org.postgresql.jdbc.PgConnection)
        : this(id, instance, options) {
        _jdbcConn = jdbc
    }

    private var _jdbcConn: org.postgresql.jdbc.PgConnection? = jdbc

    /**
     * The JDBC connection backing this PSQL connection.
     * @throws NakshaException with [ILLEGAL_STATE][naksha.base.NakshaError.ILLEGAL_STATE], if the connection is closed.
     */
    val jdbc: org.postgresql.jdbc.PgConnection
        get() = _jdbcConn ?: throw illegalState("Connection is closed")

    actual val id: Long = id
    actual val instance: PgInstance = instance
    actual val options: SessionOptions = options

    /**
     * The weak-reference to this session.
     * @since 3.0
     */
    val weakRef = WeakReference(this)

    actual fun execute(sql: String, args: Array<Any?>?): PgCursor {
        val conn = jdbc
        try {
            val stmt = if (args.isNullOrEmpty()) {
                // no args execute
                val stmt = conn.createStatement()
                stmt.execute(sql)
                stmt
            } else {
                val query = PsqlQuery(sql ,null)
                val stmt = query.prepare(conn)
                if (args.isNotEmpty()) query.bindArguments(stmt, args)
                stmt.execute()
                stmt
            }
            return PgCursor(stmt, true)
        } catch (throwable: Throwable) {
            throw PgExceptionMapper.map(throwable, sql)
        }
    }

    actual fun prepare(sql: String, typeNames: Array<String>?): PgPlan {
        try {
            return PgPlan(PsqlQuery(sql, typeNames), jdbc)
        } catch (throwable: Throwable) {
            throw PgExceptionMapper.map(throwable, sql)
        }
    }

    actual var autoCommit: Boolean
        get() {
            return try {
                jdbc.autoCommit
            } catch (throwable: Throwable) {
                throw PgExceptionMapper.map(throwable)
            }
        }
        set(value) {
            try {
                jdbc.autoCommit = value
            } catch (throwable: Throwable) {
                throw PgExceptionMapper.map(throwable)
            }
        }

    actual fun commit() {
        try {
            jdbc.commit()
        } catch (throwable: Throwable) {
            throw PgExceptionMapper.map(throwable)
        }
    }

    actual fun rollback() {
        try {
            jdbc.rollback()
        } catch (throwable: Throwable) {
            throw PgExceptionMapper.map(throwable)
        }
    }

    actual fun isClosed(): Boolean = _jdbcConn == null

    actual override fun close() {
        val jdbcConn = _jdbcConn
        this._jdbcConn = null
        if (jdbcConn != null) {
            try {
                if (jdbcConn.isClosed) {
                    instance.connectionPool.remove(id)
                    return
                }
                if (!jdbcConn.autoCommit) {
                    jdbcConn.rollback()
                } else {
                    jdbcConn.autoCommit = false
                }
                val pooledConn = instance.connectionPool[id]
                if (pooledConn != null) {
                    pooledConn.connection.compareAndSet(weakRef, null)
                }
            } catch (throwable: Throwable) {
                instance.connectionPool.remove(id)
                throw PgExceptionMapper.map(throwable)
            }
        }
    }

    actual override fun toString(): String = "${instance}#$id"

    actual fun terminate() {
        val pgConnection = _jdbcConn
        this._jdbcConn = null
        if (pgConnection != null) {
            try {
                // Remove the connection from the pool and close it
                instance.connectionPool.remove(id)
                pgConnection.close()
            } catch (throwable: Throwable) {
                throw PgExceptionMapper.map(throwable)
            }
        }
    }

    actual fun toUri(showPassword: Boolean): String = if (showPassword) jdbc.url else toString()
}
