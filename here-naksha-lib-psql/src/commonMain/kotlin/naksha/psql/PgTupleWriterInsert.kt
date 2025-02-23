package naksha.psql

/**
 * Execute INSERTS into a collection.
 * @since 3.0
 * @see [PgTupleWriter]
 */
internal class PgTupleWriterInsert(session: PgSession, collection: PgCollection, writes: List<PgTupleWrite>)
    : PgTupleWriteBase(session, collection, writes)
{
    private fun plan(conn: PgConnection, collection: PgCollection): PgPlan {
        val cols = PgColumn.allColumns
        val names = cols.joinToString(",") { it.name }
        val SQL = """
WITH new_row AS (
  SELECT * FROM UNNEST(
    \$${cols.joinToString(",") { (it.i + 1).toString() }}
  ) AS t(
    $names
  )
)
INSERT INTO ${collection.head.quotedName} ($names)
SELECT * FROM new_row
"""
        return conn.prepare(SQL, cols.map { it.type.text + "[]" }.toTypedArray())
    }

    fun execute(conn: PgConnection) {
        val plan = plan(conn, collection)
        plan.execute(toDataArray()).close()
    }
}