package naksha.psql

import naksha.base.MapProxy

/**
 * Simulates the Postgres [tuple-store](https://github.com/postgres/postgres/blob/master/src/backend/utils/sort/tuplestore.c).
 */
@Deprecated("We need this for triggers I think, or?")
class PsqlTable : naksha.psql.ITable {
    /**
     * The rows as they have been returned.
     */
    val rows = ArrayList<MapProxy<String, Any>>()

    override fun returnNext(ret: MapProxy<String, Any>) {
        rows.add(ret)
    }
}