package com.here.naksha.lib.jbon

import java.sql.ResultSet
import java.util.HashMap

open class JvmSqlResult {
    private var rs : ResultSet? = null
    private var columnCount : Int = 0
    private var columnNames: Array<String>? = null
    private var columnTypes: Array<String>? = null

    internal fun rs() : ResultSet? {
        return rs
    }

    internal fun setResultSet(rs: ResultSet?) {
        if (rs != null) {
            this.rs = rs
            columnCount = rs.metaData.columnCount
            columnNames = Array(columnCount) {
                rs.metaData.getColumnLabel(it + 1)
            }
            columnTypes = Array(columnCount) {
                rs.metaData.getColumnTypeName(it + 1)
            }
        } else {
            this.rs = null
            columnCount = 0
            columnNames = null
            columnTypes = null
        }
    }

    internal fun readRow() : Any {
        val rs = this.rs
        check(rs != null)
        val columnNames = this.columnNames
        check(columnNames != null)
        val columnTypes = this.columnTypes
        check(columnTypes != null)
        val row = HashMap<String, Any?>()
        var i = 0
        while (i < columnNames.size) {
            val name = columnNames[i]
            val type = columnTypes[i]
            // See: https://www.postgresql.org/message-id/AANLkTinsk4rwT7v-751bwQkgTN1rkA=8uE-jk69nape-@mail.gmail.com
            i++
            when (type) {
                "null" -> row[name] = null
                "text", "varchar", "character", "char", "json", "uuid", "inet", "cidr", "macaddr", "xml", "internal",
                "point", "line", "lseg", "box", "path", "polygon", "circle", "int4range", "int8range", "numrange",
                "tsrange", "tstzrange", "daterange" -> row[name] = rs.getString(i)
                "smallint", "int2" -> row[name] = rs.getShort(i).toInt()
                "integer", "int4" -> row[name] = rs.getInt(i)
                "bigint", "int8" -> row[name] = rs.getLong(i)
                "real" -> row[name] = rs.getFloat(i)
                "double precision" -> row[name] = rs.getFloat(i)
                "numeric" -> row[name] = rs.getBigDecimal(i)
                "boolean" -> row[name] = rs.getBoolean(i)
                "timestamp" -> row[name] = rs.getTimestamp(i)
                "date" -> row[name] = rs.getDate(i)
                "bytea" -> row[name] = rs.getBytes(i)
                "jsonb" -> row[name] = JbSession.get().parse(rs.getString(i))
                "array" -> row[name] = rs.getArray(i)
                else -> row[name] = rs.getObject(i)
            }
            i++
        }
        return row
    }
}