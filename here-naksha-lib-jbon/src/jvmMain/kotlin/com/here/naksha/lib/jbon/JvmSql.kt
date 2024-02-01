package com.here.naksha.lib.jbon

import com.here.naksha.lib.core.AbstractTask.State
import java.sql.Connection
import java.sql.Statement

/**
 * Java JDBC binding to grant access to PostgresQL. Should be placed into [com.here.naksha.lib.jbon.JvmSession.sqlApi].
 */
@Suppress("MemberVisibilityCanBePrivate")
open class JvmSql(val conn: Connection) : ISql {

    override fun execute(sql: String, args: Array<Any?>): ISqlResultSet {
        if (args.isEmpty()) {
            val stmt = conn.createStatement()
            try {
                val hasResultSet = stmt.execute(sql)
                if (hasResultSet) {
                    return JvmSqlResultSet(stmt.resultSet)
                }
                return JvmSqlResultSet(stmt.updateCount)
            } finally {
                stmt.close()
            }
        }
        val query = JvmSqlQuery(sql)
        val stmt = query.prepare(conn)
        try {
            query.bindArguments(stmt, args)
            val hasResultSet = stmt.execute()
            if (hasResultSet) {
                return JvmSqlResultSet(stmt.resultSet)
            }
            return JvmSqlResultSet(stmt.updateCount)
        } finally {
            stmt.close()
        }
    }

    override fun prepare(sql: String, typeNames: Array<String>): ISqlPlan {
        return JvmPlan(JvmSqlQuery(sql), conn)
    }
}