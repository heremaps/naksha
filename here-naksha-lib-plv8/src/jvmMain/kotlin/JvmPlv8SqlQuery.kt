package com.here.naksha.lib.plv8

import naksha.base.JvmInt64
import java.sql.Connection
import java.sql.PreparedStatement
import java.util.ArrayList
import java.util.HashMap

class JvmPlv8SqlQuery(query: String) {

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
                        val afterNext = if (charIndex +1 < query.length) query[charIndex +1] else ';'
                        val dollar:Int = if (afterNext in '0' .. '9') {
                            "$next$afterNext".toInt()
                        } else {
                            next - '0'
                        }

                        check(dollar in 1..99)
                        var indices = dollarToIndices[dollar]
                        if (indices == null) {
                            indices = ArrayList()
                            dollarToIndices[dollar] = indices
                        }
                        sb.append('?')
                        indices.add(index++)
                        charIndex++
                        if (dollar > 9) {
                            charIndex++
                        }
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
                is JvmInt64 -> stmt.setLong(index, arg.toLong())
                is Float -> stmt.setFloat(index, arg)
                is Double -> stmt.setDouble(index, arg)
                is String -> stmt.setString(index, arg)
                is ByteArray -> stmt.setBytes(index, arg)
                is Array<*> -> {
                    if (arg.size == 0) throw IllegalArgumentException("Can't detect type of empty array")
                    val testValue = arg[0]
                    when (testValue) {
                        is Boolean -> stmt.setArray(index, stmt.connection.createArrayOf("bool", arg))
                        is Short -> stmt.setArray(index, stmt.connection.createArrayOf("int2", arg))
                        is Int -> stmt.setArray(index, stmt.connection.createArrayOf("int4", arg))
                        is Long -> stmt.setArray(index, stmt.connection.createArrayOf("int8", arg))
                        is JvmInt64 -> stmt.setArray(index, stmt.connection.createArrayOf("int8", arg))
                        is Float -> stmt.setArray(index, stmt.connection.createArrayOf("real", arg))
                        is Double -> stmt.setArray(index, stmt.connection.createArrayOf("double precision", arg))
                        is String -> stmt.setArray(index, stmt.connection.createArrayOf("text", arg))
                        is ByteArray -> stmt.setArray(index, stmt.connection.createArrayOf("bytea", arg))
                        else -> throw IllegalArgumentException("Auto detection of array-type failed due to unknown first element")
                    }
                }
                null -> stmt.setNull(index, 0)
                else -> throw IllegalArgumentException("args[" + (index - 1) + "]")
            }
        }
    }

    fun bindArguments(stmt: PreparedStatement, args: Array<Any?>) {
        var i = 0
        while (i < args.size) {
            val arg = args[i]
            val indices = dollarToIndices[i + 1]
            check(indices != null) { "Indices must not be null" }
            setArgument(stmt, arg, indices)
            i++
        }
    }

    fun prepare(conn: Connection): PreparedStatement {
        return conn.prepareStatement(sql)
    }
}