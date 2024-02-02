package com.here.naksha.lib.jbon

import java.sql.Connection
import java.sql.PreparedStatement

/**
 * The Java implementation of a plan.
 */
class JvmSqlPlan(internal val query : JvmSqlQuery, conn:Connection) : JvmSqlResult(), ISqlPlan {
    val stmt : PreparedStatement
    init {
        stmt = query.prepare(conn)
    }
    var closed : Boolean = false

    override fun execute(vararg args: Any?): ISqlResultSet {
        check(!closed)
        query.bindArguments(stmt, args)
        val hasResultSet = stmt.execute()
        if (hasResultSet) {
            return JvmSqlResultSet(stmt.resultSet)
        }
        return JvmSqlResultSet(stmt.updateCount)
    }

    override fun cursor(vararg args: Any?): ISqlCursor {
        check(!closed)
        query.bindArguments(stmt, *args)
        val hasResultSet = stmt.execute()
        if (hasResultSet) {
            return JvmSqlCursor(stmt.resultSet)
        }
        return JvmSqlCursor(null)
    }

    override fun close() {
        val closed = this.closed
        if (!closed) {
            this.closed = true
            stmt.close()
        }
    }
}