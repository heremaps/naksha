package com.here.naksha.lib.plv8

import com.here.naksha.lib.jbon.BigInt64
import com.here.naksha.lib.jbon.toLong
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

    override fun setString(parameterIndex: Int, value: String?) {
        stmt.setString(parameterIndex, value)
    }

    override fun setBytes(parameterIndex: Int, value: ByteArray?) {
        stmt.setBytes(parameterIndex, value)
    }

    override fun setLong(parameterIndex: Int, value: BigInt64?) {
        stmt.setLong(parameterIndex, value?.toLong() ?: 0)
    }

    override fun setInt(parameterIndex: Int, value: Int?) {
        stmt.setInt(parameterIndex, value ?: 0)
    }

    override fun setShort(parameterIndex: Int, value: Short?) {
        stmt.setShort(parameterIndex, value ?: 0)
    }

    override fun addBatch() {
        stmt.addBatch()
    }

    override fun executeBatch(): IntArray {
        return stmt.executeBatch()
    }

    override fun free() {
        val closed = this.closed
        if (!closed) {
            this.closed = true
            stmt.close()
        }
    }
}