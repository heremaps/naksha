package com.here.naksha.lib.plv8

import naksha.base.Int64
import naksha.plv8.IPlv8Cursor
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.Types

/**
 * The Java implementation of a plan.
 */
class JvmPlv8Plan(internal val query: JvmPlv8SqlQuery, conn: Connection) : naksha.plv8.IPlv8Plan {
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

    internal fun setString(parameterIndex: Int, value: String?) {
        stmt.setString(parameterIndex, value)
    }

    internal fun setBytes(parameterIndex: Int, value: ByteArray?) {
        stmt.setBytes(parameterIndex, value)
    }

    internal fun setLong(parameterIndex: Int, value: Int64?) {
        if (value == null) stmt.setNull(parameterIndex, Types.BIGINT) else stmt.setLong(parameterIndex, value.toLong())
    }

    internal fun setInt(parameterIndex: Int, value: Int?) {
        if (value == null) stmt.setNull(parameterIndex, Types.INTEGER) else stmt.setInt(parameterIndex, value)
    }

    internal fun setShort(parameterIndex: Int, value: Short?) {
        if (value == null) stmt.setNull(parameterIndex, Types.SMALLINT) else stmt.setShort(parameterIndex, value)
    }

    internal fun addBatch() {
        stmt.addBatch()
    }

    internal fun executeBatch(): IntArray {
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