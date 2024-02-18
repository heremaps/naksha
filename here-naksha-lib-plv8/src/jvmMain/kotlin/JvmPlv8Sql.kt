package com.here.naksha.lib.plv8

import java.io.Closeable
import java.sql.Connection

/**
 * Java JDBC binding to grant access to PostgresQL.
 */
@Suppress("MemberVisibilityCanBePrivate", "UNCHECKED_CAST")
class JvmPlv8Sql(var conn: Connection?) : IPlv8Sql, Closeable {
    override fun newTable(): ITable {
        return JvmPlv8Table()
    }

    override fun affectedRows(any: Any): Int? {
        return if (any is Int) any else null
    }

    override fun rows(any: Any): Array<Any>? {
        return if (any is Array<*>) any as Array<Any> else null
    }

    override fun execute(sql: String, args: Array<Any?>?): Any {
        val conn = this.conn
        check(conn != null)
        if (args.isNullOrEmpty()) {
            val stmt = conn.createStatement()
            stmt.use {
                return if (stmt.execute(sql)) JvmPlv8ResultSet(stmt.resultSet).toArray() else stmt.updateCount
            }
        }
        val query = JvmPlv8SqlQuery(sql)
        val stmt = query.prepare(conn)
        stmt.use {
            if (!args.isNullOrEmpty()) query.bindArguments(stmt, args)
            return if (stmt.execute()) JvmPlv8ResultSet(stmt.resultSet).toArray() else stmt.updateCount
        }
    }

    override fun prepare(sql: String, typeNames: Array<String>?): IPlv8Plan {
        val conn = this.conn
        check(conn != null)
        return JvmPlv8Plan(JvmPlv8SqlQuery(sql), conn)
    }

    override fun close() {
        conn?.close()
        conn = null
    }
}