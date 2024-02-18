package com.here.naksha.lib.plv8

import java.sql.Connection
import java.sql.PreparedStatement

/**
 * The Java implementation of a plan.
 */
class JvmPlv8Plan(internal val query: JvmPlv8SqlQuery, conn: Connection) : IPlv8Plan {
    val stmt: PreparedStatement = query.prepare(conn)
    var closed: Boolean = false

    override fun execute(args: Array<Any?>?): Any {
        check(!closed)
        if (!args.isNullOrEmpty()) query.bindArguments(stmt, args)
        val hasResultSet = stmt.execute()
        if (hasResultSet) {
            return JvmPlv8ResultSet(stmt.resultSet).toArray()
        }
        return stmt.updateCount
    }

    override fun cursor(args: Array<Any?>?): IPlv8Cursor {
        check(!closed)
        if (!args.isNullOrEmpty()) query.bindArguments(stmt, args)
        val hasResultSet = stmt.execute()
        if (hasResultSet) {
            return JvmPlv8Cursor(stmt.resultSet)
        }
        return JvmPlv8Cursor(null)
    }

    override fun free() {
        val closed = this.closed
        if (!closed) {
            this.closed = true
            stmt.close()
        }
    }
}