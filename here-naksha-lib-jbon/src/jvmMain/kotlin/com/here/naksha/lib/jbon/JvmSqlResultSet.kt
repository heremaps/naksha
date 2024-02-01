package com.here.naksha.lib.jbon

import java.sql.ResultSet

/**
 * Create a result-set from the return value of `execute`, which is expected to be either [Int] or [ResultSet].
 */
class JvmSqlResultSet(result : Any) : JvmSqlResult(), ISqlResultSet {
    private val affectedRows : Int
    private val rows : Array<Any>?

    init {
        when (result) {
            is Number -> {
                affectedRows = result.toInt()
                rows = null
            }

            is ResultSet -> {
                affectedRows = -1
                setResultSet(result)
                val list = ArrayList<Any>()
                while (result.next()) {
                    list.add(readRow())
                }
                rows = list.toArray()
            }

            else -> {
                throw IllegalArgumentException("Only Int and ResultSet allowed")
            }
        }
    }

    override fun affectedRows(): Int {
        return affectedRows
    }

    override fun hasRows(): Boolean {
        return rows != null
    }

    override fun rows(): Array<Any> {
        check(rows != null)
        return rows
    }
}