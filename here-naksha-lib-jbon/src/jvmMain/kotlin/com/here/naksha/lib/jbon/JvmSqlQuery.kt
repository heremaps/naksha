package com.here.naksha.lib.jbon

import java.sql.Connection
import java.sql.PreparedStatement
import java.util.ArrayList
import java.util.HashMap

class JvmSqlQuery(query: String) {

    /**
     * We map "$1" to a list of positions (`1..n`) in the prepared statement. For example
     * `SELECT * FROM table WHERE a = $1 OR b = $1`. In that case, we replace it with
     * `SELECT * FROM table WHERE a = ? OR b = ?` and the map holds one entry 1 and a
     * list of two elements 1 and 2.
     */
    val dollarToIndices: HashMap<Int, ArrayList<Int>> = HashMap()
    val sql: String

    init {
        val sb = StringBuilder()
        var index = 1
        var charIndex = 0
        while (charIndex < query.length) {
            val c = query[charIndex++]
            if (c == '$') {
                if (charIndex < query.length) {
                    val next = query[charIndex]
                    if (next in '1'..'9') {
                        val dollar = next - '0'
                        check(dollar in 1..9)
                        var indices = dollarToIndices[dollar]
                        if (indices == null) {
                            indices = ArrayList()
                            dollarToIndices[dollar] = indices
                        }
                        sb.append('?')
                        indices.add(index++)
                        charIndex++
                        continue
                    }
                }
            }
            sb.append(c)
        }
        sql = sb.toString()
    }

    private fun setArgument(stmt: PreparedStatement, arg: Any?, indices: ArrayList<Int>) {
        var i = 0
        while (i < indices.size) {
            val index = indices[i++]
            when (arg) {
                is Boolean -> stmt.setBoolean(index, arg)
                is Short -> stmt.setShort(index, arg)
                is Int -> stmt.setInt(index, arg)
                is Long -> stmt.setLong(index, arg)
                is Float -> stmt.setFloat(index, arg)
                is Double -> stmt.setDouble(index, arg)
                is String -> stmt.setString(index, arg)
                else -> throw IllegalArgumentException("args[" + (index - 1) + "]")
            }
        }
    }

    fun bindArguments(stmt: PreparedStatement, args: Array<Any?>) {
        var i = 0
        while (i < args.size) {
            val arg = args[i]
            val indices = dollarToIndices[i + 1]
            check(indices != null)
            setArgument(stmt, arg, indices)
            i++
        }
    }

    fun prepare(conn: Connection): PreparedStatement {
        return conn.prepareStatement(sql)
    }
}