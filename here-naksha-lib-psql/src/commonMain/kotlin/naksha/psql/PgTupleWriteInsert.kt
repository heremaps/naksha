package naksha.psql

import naksha.psql.PgColumn.PgColumnCompanion.allColumns

/**
 * Execute INSERTS into a collection.
 * @since 3.0
 * @see [PgTupleWriter]
 */
internal class PgTupleWriteInsert(session: PgSession, collection: PgCollection, writes: List<PgTupleWrite>)
    : PgTupleWriteBase(session, collection, writes)
{
    init {
        rows.addColumns(allColumns)
        var i = 0
        for (write in writes) {
            val tuple = write.tuple
            if (tuple != null) rows[i++] = tuple
        }
    }

    private fun plan(conn: PgConnection, collection: PgCollection): PgPlan {
        // TODO: we need to delete shadow states, if shadow is available
        val SQL = """WITH new_row AS (
  SELECT * FROM UNNEST(${rows.placeholders()}) AS t(${rows.names()})
)
INSERT INTO ${collection.headTable.quotedName} (${rows.names()})
SELECT * FROM new_row"""
        return conn.prepare(SQL, rows.typeNames())
    }

    override fun doExecute(conn: PgConnection) {
        val plan = plan(conn, collection)
        val array = rows.values()
        plan.execute(array).close()
    }
}