package com.here.naksha.lib.plv8.naksha.plv8

import naksha.base.ObjectProxy
import naksha.plv8.IPgConnection
import naksha.plv8.IPgPlan
import naksha.plv8.Param
import naksha.plv8.PgDbInfo
import org.postgresql.jdbc.PgConnection

/**
 * A PostgresQL connection.
 * @constructor Creates a new connection wrapper.
 * @property instance the instance to which the connection is bound, will return into the connection pool of this instance,
 * when released.
 * @param conn the JDBC connection that backs this connection.
 * @property options the connection options.
 */
class PsqlConnection(val instance: PsqlInstance, conn: PgConnection, val options: PsqlConnectOptions) :
    IPgConnection, AutoCloseable {

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

    override fun info(): PgDbInfo {
        TODO("Not yet implemented")
    }

    override fun affectedRows(any: Any): Int? {
        TODO("Not yet implemented")
    }

    override fun rows(any: Any): Array<ObjectProxy>? {
        TODO("Not yet implemented")
    }

    override fun execute(sql: String, args: Array<Any?>?): Any {
        TODO("Not yet implemented")
    }

    override fun prepare(sql: String, typeNames: Array<String>?): IPgPlan {
        TODO("Not yet implemented")
    }

    override fun executeBatch(plan: IPgPlan, bulkParams: Array<Array<Param>>): IntArray {
        TODO("Not yet implemented")
    }

    override fun gzipCompress(raw: ByteArray): ByteArray {
        TODO("Not yet implemented")
    }

    override fun gzipDecompress(compressed: ByteArray): ByteArray {
        TODO("Not yet implemented")
    }

    /**
     * Commit all changes done in the current transaction.
     */
    fun commit() {
        execute("commit")
    }

    /**
     * Rollback (revert) all changes done in the current transaction.
     */
    fun rollback() {
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
            // pgConnection.rollback()
            pgConnection.close()
            // TODO: Return _connection to pool of instance!
        }
    }
}