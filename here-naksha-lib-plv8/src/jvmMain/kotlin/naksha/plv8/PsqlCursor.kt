package naksha.plv8

import naksha.plv8.PgCursor
import java.sql.ResultSet

/**
 * Wraps a native JDBC result-set.
 */
class PsqlCursor(resultSet: ResultSet?) : PsqlResultSet(resultSet), PgCursor {

    override fun fetch(): Any? {
        val rs = rs()
        return if (rs != null && rs.next()) readRow() else null
    }

    override fun move(nrow: Int) {
        val rs = rs()
        if (rs != null && nrow != 0) rs.relative(nrow)
    }
}