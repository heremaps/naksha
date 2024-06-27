package com.here.naksha.lib.plv8

import naksha.plv8.IPgCursor
import java.sql.ResultSet

/**
 * Wraps a native JDBC result-set.
 */
class JvmPgCursor(resultSet: ResultSet?) : JvmPlv8ResultSet(resultSet), IPgCursor {

    override fun fetch(): Any? {
        val rs = rs()
        return if (rs != null && rs.next()) readRow() else null
    }

    override fun move(nrow: Int) {
        val rs = rs()
        if (rs != null && nrow != 0) rs.relative(nrow)
    }
}