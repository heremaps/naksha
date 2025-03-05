package naksha.psql

import naksha.model.mapExists
import naksha.psql.PgColumn.PgColumnCompanion.allColumns
import naksha.psql.PgColumn.PgColumnCompanion.next_tn

/**
 * Execute an **INSERT** _(aka [CREATE][naksha.model.request.WriteOp.CREATE])_ into a collection.
 * @since 3.0
 * @see [PgWriter]
 */
internal class PgWriterInsert(writer: PgWriter, collection: PgCollection, writes: List<PgWrite>)
    : PgWriterBase(writer, collection, writes)
{
    init {
        rows.addColumns(allColumns)
        var i = 0
        for (write in writes) {
            val tuple = write.tuple
            if (tuple != null) {
                rows[i++] = tuple
            }
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