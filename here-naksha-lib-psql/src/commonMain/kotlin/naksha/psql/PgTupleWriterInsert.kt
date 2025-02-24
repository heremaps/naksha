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
    ${cols.joinToString(",") { "\$${(it.i + 1)}" }}
  ) AS t(
    $names
  )
)
INSERT INTO ${collection.head.quotedName} ($names)
SELECT * FROM new_row
"""
        val argTypes = cols.map { it.type.text + "[]" }.toTypedArray()
        return conn.prepare(SQL, argTypes)
    }

    fun execute(conn: PgConnection) {
        val plan = plan(conn, collection)
        val array = toDataArray()
        plan.execute(array).close()
    }
}