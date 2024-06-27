package naksha.psql

import naksha.base.AbstractMapProxy

/**
 * Simulates the Postgres [tuple-store](https://github.com/postgres/postgres/blob/master/src/backend/utils/sort/tuplestore.c).
 */
class PsqlTable : naksha.psql.ITable {
    /**
     * The rows as they have been returned.
     */
    val rows = ArrayList<AbstractMapProxy<String, Any>>()

    override fun returnNext(ret: AbstractMapProxy<String, Any>) {
        rows.add(ret)
    }
}