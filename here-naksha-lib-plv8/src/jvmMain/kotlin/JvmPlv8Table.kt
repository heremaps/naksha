package com.here.naksha.lib.plv8

import naksha.base.P_Map

/**
 * Simulates the Postgres [tuple-store](https://github.com/postgres/postgres/blob/master/src/backend/utils/sort/tuplestore.c).
 */
class JvmPlv8Table : naksha.plv8.ITable {
    /**
     * The rows as they have been returned.
     */
    val rows = ArrayList<P_Map<String, Any>>()

    override fun returnNext(ret: P_Map<String, Any>) {
        rows.add(ret)
    }
}