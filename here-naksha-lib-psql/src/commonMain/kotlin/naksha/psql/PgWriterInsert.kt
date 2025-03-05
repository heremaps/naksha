package naksha.psql

import naksha.psql.PgColumn.PgColumnCompanion.allColumns

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
        val headTable = collection.headTable
        val shadowTable = collection.deletedTable

        val new_row = """WITH new_row AS (
  SELECT * FROM UNNEST(${rows.placeholders()}) AS t(${rows.names()})
)"""

        // If the shadow table exists, delete old states
        val clear_shadow = if (shadowTable != null) """, clear_shadow AS (
  DELETE FROM ${shadowTable.quotedName}
  WHERE id IN (SELECT id FROM new_row)
  RETURNING id, tn
)""" else ""

        // Insert the features
        val inserted = """, inserted AS (
INSERT INTO ${headTable.quotedName} (${rows.names()})
SELECT * FROM new_row
RETURNING id, tn
)"""

        // Actually perform the insert.
        val SQL = """$new_row$clear_shadow$inserted
SELECT inserted.id AS id, inserted.tn AS tn${if (clear_shadow.isNotEmpty()) ", clear_shadow.tn AS clear_tn" else ""}
FROM inserted
${if (clear_shadow.isNotEmpty()) "LEFT JOIN clear_shadow ON clear_shadow.id = inserted.id" else ""} 
"""
        return conn.prepare(SQL, rows.typeNames())
    }

    override fun doExecute(conn: PgConnection) {
        val plan = plan(conn, collection)
        val array = rows.values()
        plan.execute(array).close()
        // We ignore the result, we know that if it didn't fail, it's okay.
    }
}