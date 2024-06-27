package naksha.plv8

import naksha.base.Int64
import naksha.plv8.PgCursor
import naksha.plv8.PgPlan
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.Types

/**
 * The Java implementation of a plan.
 */
class PsqlPlan(internal val query: PsqlQuery, conn: Connection) : PgPlan {
    val stmt: PreparedStatement = query.prepare(conn)
    var closed: Boolean = false

    override fun execute(args: Array<Any?>?): Any {
        check(!closed)
        if (!args.isNullOrEmpty()) query.bindArguments(stmt, args)
        val hasResultSet = stmt.execute()
        if (hasResultSet) {
            return PsqlResultSet(stmt.resultSet).toArray()
        }
        return stmt.updateCount
    }

    override fun cursor(args: Array<Any?>?): PgCursor {
        check(!closed)
        if (!args.isNullOrEmpty()) query.bindArguments(stmt, args)
        val hasResultSet = stmt.execute()
        if (hasResultSet) {
            return PsqlCursor(stmt.resultSet)
        }
        return PsqlCursor(null)
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

    override fun close() {
        val closed = this.closed
        if (!closed) {
            this.closed = true
            stmt.close()
        }
    }
}