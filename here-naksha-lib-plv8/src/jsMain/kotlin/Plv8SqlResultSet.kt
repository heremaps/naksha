package com.here.naksha.lib.plv8

import com.here.naksha.lib.jbon.ISqlResultSet

class Plv8SqlResultSet(private val rows : Any, private val affectedRows) : ISqlResultSet {
    override fun affectedRows(): Int {
        TODO("Not yet implemented")
    }

    override fun hasRows(): Boolean {
        TODO("Not yet implemented")
    }

    override fun rows(): Array<Any> {
        TODO("Not yet implemented")
    }
}