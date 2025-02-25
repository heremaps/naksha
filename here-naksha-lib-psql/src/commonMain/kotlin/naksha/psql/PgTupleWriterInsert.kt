package naksha.psql

import naksha.model.Tuple

/**
 * Execute INSERTS into a collection.
 * @since 3.0
 * @see [PgTupleWriter]
 */
internal class PgTupleWriterInsert(session: PgSession, collection: PgCollection, writes: List<PgTupleWrite>)
    : PgTupleWriteBase(session, collection, writes)
{

    /**
     * Create a single array that contains all values stored in the [Tuple] that should be modified, plus the version as provided by the write instruction.
     * @since 3.0
     */
    fun allColumnValues() : Array<Any?> = arrayOf(
        txn_next, updated_at, created_at, author_ts,
        cv0, cv1, cv2, cv3,
        hash, here_tile, flags, cc,
        tn, prev_tn, base_tn,
        id, app_id, author, origin, target, ft,
        cs0, cs1, cs2, cs3,
        tags, ref_point, geo, feature, attachment
    )

    private fun plan(conn: PgConnection, collection: PgCollection): PgPlan {
        val SQL = """
WITH new_row AS (SELECT * FROM UNNEST(${allColumnPlaceholders}) AS t($allColumnNames))
INSERT INTO ${collection.head.quotedName} ($allColumnNames)
SELECT * FROM new_row
"""
        return conn.prepare(SQL, allColumnTypeNames)
    }

    fun execute(conn: PgConnection) {
        val plan = plan(conn, collection)
        val array = allColumnValues()
        plan.execute(array).close()
    }
}