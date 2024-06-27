package naksha.psql

import naksha.base.GZip
import naksha.base.Int64
import naksha.base.ObjectProxy
import naksha.jbon.*
import naksha.psql.*
import org.postgresql.jdbc.PgConnection

/**
 * A PostgresQL session (basically, a connection to the database).
 * @constructor Creates a new connection wrapper.
 * @property instance the instance to which the connection is bound, will return into the connection pool of this instance,
 * when released.
 * @param conn the JDBC connection that backs this connection.
 * @property options the connection options.
 */
class PsqlSession(val instance: PsqlInstance, conn: PgConnection, options: PgSessionOptions) : PgSession, AutoCloseable {

    override var options: PgSessionOptions = options
        set(value) {
            // TODO: Update when statement-timeout, lock-timeout or others are updated!
            field = value
        }

    init {
        // TODO: Set the statement- and lock-timeout. Do other initialization work!
        conn.autoCommit = false
    }

    private var _pgConnection: PgConnection? = conn

    /**
     * The JDBC connection backing this Postgres connection.
     * @throws IllegalStateException If the connection was closed.
     */
    val pgConnection: PgConnection
        get() {
            val c = _pgConnection
            check(c != null)
            return c
        }

    private var dbInfo: PgDbInfo? = null

    override fun info(): PgDbInfo {
        var dbInfo = this.dbInfo
        if (dbInfo == null) {
            dbInfo = PgDbInfo(this)
            this.dbInfo = dbInfo
        }
        return dbInfo
    }

    override fun affectedRows(any: Any): Int? = if (any is Int) any else null

    @Suppress("UNCHECKED_CAST")
    override fun rows(any: Any): Array<ObjectProxy>? = if (any is Array<*>) {
        (any as Array<Any>).map { (it as ObjectProxy) }.toTypedArray()
    } else null

    override fun execute(sql: String, args: Array<Any?>?): Any {
        val conn = pgConnection
        if (args.isNullOrEmpty()) {
            val stmt = conn.createStatement()
            stmt.use {
                return if (stmt.execute(sql)) PsqlResultSet(stmt.resultSet).toArray() else stmt.updateCount
            }
        }
        val query = PsqlQuery(sql)
        val stmt = query.prepare(conn)
        stmt.use {
            if (args.isNotEmpty()) query.bindArguments(stmt, args)
            return if (stmt.execute()) PsqlResultSet(stmt.resultSet).toArray() else stmt.updateCount
        }
    }

    override fun prepare(sql: String, typeNames: Array<String>?): PgPlan = PsqlPlan(PsqlQuery(sql), pgConnection)

    override fun executeBatch(plan: PgPlan, bulkParams: Array<Array<Param>>): IntArray {
        plan as PsqlPlan
        for (singleQueryParams in bulkParams) {
            for (p in singleQueryParams) {
                when (p.type) {
                    SQL_BYTE_ARRAY -> plan.setBytes(p.idx, p.value as ByteArray?)
                    SQL_STRING -> plan.setString(p.idx, p.value as String?)
                    SQL_INT16 -> plan.setShort(p.idx, p.value as Short?)
                    SQL_INT32 -> plan.setInt(p.idx, p.value as Int?)
                    SQL_INT64 -> plan.setLong(p.idx, p.value as Int64?)
                }
            }
            plan.addBatch()
        }

        return plan.executeBatch()
    }

    override fun gzipCompress(raw: ByteArray): ByteArray = GZip.gzip(raw)

    override fun gzipDecompress(raw: ByteArray): ByteArray = GZip.gunzip(raw)

    /**
     * Commit all changes done in the current transaction.
     */
    override fun commit() {
        execute("commit")
    }

    /**
     * Rollback (revert) all changes done in the current transaction.
     */
    override fun rollback() {
        execute("rollback")
    }

    /**
     * Tests if the connection is closed.
     * @return _true_ if the connection is closed.
     */
    fun isClosed(): Boolean = _pgConnection == null

    override fun close() {
        val c = _pgConnection
        this._pgConnection = null
        if (c != null) {
            pgConnection.rollback()
            // TODO: Return the connection to pool of instance, instead of just closing it!
            pgConnection.close()
        }
    }
}