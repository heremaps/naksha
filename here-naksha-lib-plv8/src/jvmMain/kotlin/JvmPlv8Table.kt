package com.here.naksha.lib.plv8

import com.here.naksha.lib.jbon.IMap

/**
 * Simulates the Postgres [tuple-store](https://github.com/postgres/postgres/blob/master/src/backend/utils/sort/tuplestore.c).
 */
class JvmPlv8Table : ITable {
    /**
     * The rows as they have been returned.
     */
    val rows = ArrayList<IMap>()

    override fun returnNext(ret: IMap) {
        rows.add(ret)
    }
}