package com.here.naksha.lib.jbon

import java.sql.ResultSet

/**
 * Wraps a native JDBC result-set.
 */
class JvmSqlCursor(resultSet: ResultSet?) : JvmSqlResult(), ISqlCursor {
    init {
        setResultSet(resultSet)
    }

    override fun pos(): Int {
        val rs = this.rs()
        if (rs != null) {
            return rs.row - 1
        }
        return -1
    }

    override fun next(): Any? {
        val rs = this.rs() ?: return null
        if (rs.next()) {
            return readRow()
        }
        return null
    }

    override fun fetch(amount: Int): Array<Any> {
        TODO("Not yet implemented")
    }

    override fun moveBy(by: Int) {
        TODO("Not yet implemented")
    }

    override fun moveTo(pos: Int) {
        TODO("Not yet implemented")
    }

    override fun close() {
        val rs = rs()
        if (rs != null) {
            rs.close()
            setResultSet(null)
        }
    }
}