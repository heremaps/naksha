package com.here.naksha.lib.jbon

import java.io.Closeable
import java.sql.Connection

/**
 * Java JDBC binding to grant access to PostgresQL. Should be placed into [com.here.naksha.lib.jbon.JvmSession.sqlApi].
 */
@Suppress("MemberVisibilityCanBePrivate")
open class JvmSql(val conn: Connection) : ISql, Closeable {

    override fun execute(sql: String, vararg args: Any?): ISqlResultSet {
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
            query.bindArguments(stmt, *args)
            val hasResultSet = stmt.execute()
            if (hasResultSet) {
                return JvmSqlResultSet(stmt.resultSet)
            }
            return JvmSqlResultSet(stmt.updateCount)
        } finally {
            stmt.close()
        }
    }

    override fun prepare(sql: String, vararg typeNames: String): ISqlPlan {
        return JvmSqlPlan(JvmSqlQuery(sql), conn)
    }

    override fun close() {
        conn.close()
    }
}