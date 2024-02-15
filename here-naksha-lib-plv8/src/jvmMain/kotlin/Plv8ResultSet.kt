package com.here.naksha.lib.plv8

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue
import com.here.naksha.lib.jbon.IMap
import com.here.naksha.lib.jbon.JbSession
import com.here.naksha.lib.jbon.JvmMap
import java.sql.ResultSet
import kotlin.collections.HashMap

/**
 * Internal helper class to handle results as if they were returned by PLV8 engine.
 */
open class Plv8ResultSet(private var rs: ResultSet?) {
    private val columnCount: Int
    private val columnNames: Array<String>?
    private val columnTypes: Array<String>?

    init {
        val rs = this.rs
        if (rs != null) {
            columnCount = rs.metaData.columnCount
            columnNames = Array(columnCount) {
                rs.metaData.getColumnLabel(it + 1)
            }
            columnTypes = Array(columnCount) {
                rs.metaData.getColumnTypeName(it + 1)
            }
        } else {
            columnCount = 0
            columnNames = null
            columnTypes = null
        }
    }

    fun rs(): ResultSet? {
        return rs
    }

    /**
     * Reads the next row into a native map.
     * @return The next row as native map.
     */
    fun readRow(): JvmMap {
        val rs = this.rs
        check(rs != null)
        val columnNames = this.columnNames
        check(columnNames != null)
        val columnTypes = this.columnTypes
        check(columnTypes != null)
        val row = JvmMap()
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
                "jsonb" -> row[name] = JbSession.env!!.parse(rs.getString(i))
                "array" -> row[name] = rs.getArray(i)
                else -> row[name] = rs.getObject(i)
            }
        }
        return row
    }

    /**
     * Convert the while result-set into an array and then close the result-set.
     * @return The result set as array of rows (native maps).
     */
    fun toArray(): Array<HashMap<String, Any?>> {
        val rs = this.rs
        check(rs != null)
        val array = ArrayList<JvmMap>(30)
        while (rs.next()) {
            array.add(readRow())
        }
        close()
        return array.toTypedArray()
    }

    fun close() {
        val rs = this.rs
        if (rs != null) {
            rs.close()
            this.rs = null
        }
    }
}