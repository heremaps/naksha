package com.here.naksha.lib.plv8

import com.here.naksha.lib.jbon.ISql
import com.here.naksha.lib.jbon.ISqlPlan

class Plv8Sql : ISql {
    override fun execute(sql: String, args: Array<Any?>): Plv8SqlResultSet {
        TODO("Not yet implemented")
    }

    override fun prepare(sql: String, typeNames: Array<String>): ISqlPlan {
        TODO("Not yet implemented")
    }
}