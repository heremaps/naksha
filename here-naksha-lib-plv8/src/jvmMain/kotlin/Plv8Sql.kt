package com.here.naksha.lib.plv8

import com.here.naksha.lib.jbon.IMap
import java.io.Closeable
import java.security.MessageDigest
import java.sql.Connection

/**
 * Java JDBC binding to grant access to PostgresQL.
 */
@Suppress("MemberVisibilityCanBePrivate", "UNCHECKED_CAST")
class Plv8Sql(var conn: Connection?) : IPlv8Sql, Closeable {
    override fun newTable(): ITable {
        return Plv8Table()
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
                return if (stmt.execute(sql)) Plv8ResultSet(stmt.resultSet).toArray() else stmt.updateCount
            }
        }
        val query = Plv8SqlQuery(sql, null)
        val stmt = query.prepare(conn)
        stmt.use {
            if (!args.isNullOrEmpty()) query.bindArguments(stmt, args)
            return if (stmt.execute()) Plv8ResultSet(stmt.resultSet).toArray() else stmt.updateCount
        }
    }

    override fun prepare(sql: String, typeNames: Array<String>?): IPlv8Plan {
        val conn = this.conn
        check(conn != null)
        return Plv8Plan(Plv8SqlQuery(sql, typeNames), conn)
    }

    override fun close() {
        conn?.close()
        conn = null
    }

    @OptIn(ExperimentalStdlibApi::class)
    override fun md5(text: String): String {
        return MessageDigest.getInstance("MD5").digest(text.toByteArray()).toHexString()
    }

    override fun <T> cast(o: Any): T {
        return o as T
    }

    override fun <T> readCol(row: Any, name: String): T {
        return (row as Map<String, *>)[name] as T
    }

}