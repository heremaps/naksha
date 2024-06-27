package naksha.plv8

import naksha.base.AbstractMapProxy

/**
 * Simulates the Postgres [tuple-store](https://github.com/postgres/postgres/blob/master/src/backend/utils/sort/tuplestore.c).
 */
class PsqlTable : naksha.plv8.ITable {
    /**
     * The rows as they have been returned.
     */
    val rows = ArrayList<AbstractMapProxy<String, Any>>()

    override fun returnNext(ret: AbstractMapProxy<String, Any>) {
        rows.add(ret)
    }
}