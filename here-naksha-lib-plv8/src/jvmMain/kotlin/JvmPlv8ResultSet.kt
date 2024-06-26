package com.here.naksha.lib.plv8

import naksha.base.JvmInt64
import naksha.base.ObjectProxy
import java.sql.ResultSet

/**
 * Internal helper class to handle results as if they were returned by PLV8 engine.
 */
open class JvmPlv8ResultSet(private var rs: ResultSet?) {
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
    fun readRow(): ObjectProxy {
        val rs = this.rs
        check(rs != null)
        val columnNames = this.columnNames
        check(columnNames != null)
        val columnTypes = this.columnTypes
        check(columnTypes != null)
        val row = ObjectProxy()
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

                "smallint", "int2" -> row[name] = getPrimitiveNullable(i, rs, rs::getShort)
                "integer", "int4", "xid4", "oid" -> row[name] = getPrimitiveNullable(i, rs, rs::getInt)
                "bigint", "int8", "xid8" -> row[name] = getPrimitiveNullable(i, rs, rs::getLong)?.let { JvmInt64(it) }
                "real" -> row[name] = getPrimitiveNullable(i, rs, rs::getFloat)
                "double precision" -> row[name] = getPrimitiveNullable(i, rs, rs::getFloat)
                "numeric" -> row[name] = rs.getBigDecimal(i)
                "boolean" -> row[name] = getPrimitiveNullable(i, rs, rs::getBoolean)
                "timestamp" -> row[name] = rs.getTimestamp(i)
                "date" -> row[name] = rs.getDate(i)
                "bytea" -> row[name] = rs.getBytes(i)
                "jsonb" -> TODO("implement parse") // Jb.env.parse(rs.getString(i))
                "array" -> row[name] = rs.getArray(i)
                else -> row[name] = rs.getObject(i)
            }
        }
        return row
    }

    private fun <T> getPrimitiveNullable(idx: Int, rs: ResultSet, getter: (Int) -> T): T? {
        val primitive: T = getter(idx)
        return if (rs.wasNull()) {
            null
        } else {
            primitive
        }
    }

    /**
     * Convert the while result-set into an array and then close the result-set.
     * @return The result set as array of rows (native maps).
     */
    fun toArray(): Array<ObjectProxy> {
        val rs = this.rs
        check(rs != null)
        val array = ArrayList<ObjectProxy>(30)
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
