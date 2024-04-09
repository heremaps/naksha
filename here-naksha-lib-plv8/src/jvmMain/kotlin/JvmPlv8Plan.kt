package com.here.naksha.lib.plv8

import com.here.naksha.lib.jbon.BigInt64
import com.here.naksha.lib.jbon.SQL_INT64
import com.here.naksha.lib.jbon.toLong
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.Types

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
        if (value == null) stmt.setNull(parameterIndex, Types.BIGINT) else stmt.setLong(parameterIndex, value.toLong())
    }

    override fun setInt(parameterIndex: Int, value: Int?) {
        if (value == null) stmt.setNull(parameterIndex, Types.INTEGER) else stmt.setInt(parameterIndex, value)
    }

    override fun setShort(parameterIndex: Int, value: Short?) {
        if (value == null) stmt.setNull(parameterIndex, Types.SMALLINT) else stmt.setShort(parameterIndex, value)
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