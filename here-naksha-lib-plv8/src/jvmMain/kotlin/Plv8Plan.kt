package com.here.naksha.lib.plv8

import java.sql.Connection
import java.sql.PreparedStatement

/**
 * The Java implementation of a plan.
 */
@Suppress("UNCHECKED_CAST")
class Plv8Plan(internal val query: Plv8SqlQuery, conn: Connection) : IPlv8Plan {
    val stmt: PreparedStatement = query.prepare(conn)
    var closed: Boolean = false

    override fun <T> execute(args: Array<Any?>?): T {
        check(!closed)
        if (!args.isNullOrEmpty()) query.bindArguments(stmt, args)
        val hasResultSet = stmt.execute()
        if (hasResultSet) {
            return Plv8ResultSet(stmt.resultSet).toArray() as T
        }
        return stmt.updateCount as T
    }

    override fun cursor(args: Array<Any?>?): IPlv8Cursor {
        check(!closed)
        if (!args.isNullOrEmpty()) query.bindArguments(stmt, args)
        val hasResultSet = stmt.execute()
        if (hasResultSet) {
            return Plv8Cursor(stmt.resultSet)
        }
        return Plv8Cursor(null)
    }

    override fun free() {
        val closed = this.closed
        if (!closed) {
            this.closed = true
            stmt.close()
        }
    }
}