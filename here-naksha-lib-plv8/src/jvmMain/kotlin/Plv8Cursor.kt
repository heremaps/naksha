package com.here.naksha.lib.plv8

import com.here.naksha.lib.jbon.IMap
import java.sql.ResultSet

/**
 * Wraps a native JDBC result-set.
 */
class Plv8Cursor(resultSet: ResultSet?) : Plv8ResultSet(resultSet), IPlv8Cursor {

    override fun fetch(): Any? {
        val rs = rs()
        return if (rs != null && rs.next()) readRow() else null
    }

    override fun move(nrow: Int) {
        val rs = rs()
        if (rs != null && nrow != 0) rs.relative(nrow)
    }
}