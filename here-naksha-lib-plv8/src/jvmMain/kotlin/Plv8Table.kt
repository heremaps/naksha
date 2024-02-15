package com.here.naksha.lib.plv8

/**
 * Simulates the Postgres [tuple-store](https://github.com/postgres/postgres/blob/master/src/backend/utils/sort/tuplestore.c).
 */
class Plv8Table : ITable {
    /**
     * The rows as they have been returned.
     */
    val rows = ArrayList<Any>()

    override fun returnNext(vararg columns: Pair<String, Any?>) {
        rows.add(columns)
    }
}