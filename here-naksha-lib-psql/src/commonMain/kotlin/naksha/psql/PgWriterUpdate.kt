package naksha.psql

import naksha.model.objects.StoreMode
import naksha.psql.PgColumn.PgColumnCompanion.allColumnNames
import naksha.psql.PgColumn.PgColumnCompanion.allColumns

/**
 * Execute a [UPDATE][naksha.model.request.WriteOp.UPDATE].
 * @since 3.0
 * @see [PgWriter]
 */
internal class PgWriterUpdate(writer: PgWriter, collection: PgCollection, writes: List<PgWrite>)
    : PgWriterBase(writer, collection, writes)
{
    init {
        rows.addColumns(allColumns)
        rows.addColumn("version", PgType.INT64) // needed to do atomic updates
        var i = 0
        for (write in writes) {
            val tuple = write.tuple
            if (tuple != null) {
                rows[i] = tuple
                rows.set(i, "version",
                    if (write.original.atomic) (write.original.version ?: write.original.tupleNumber?.version)?.txn else null
                )
                i++
            }
        }
    }

    private fun plan(conn: PgConnection, collection: PgCollection): PgPlan {
        val headTable = collection.headTable
        val shadowTable = collection.deletedTable
        val historyTable = collection.historyTable
        val insert_into_history = if (historyTable != null && collection.head.storeHistory == StoreMode.ON) historyTable else null

        // All input provided by client (the updates)
        val query = """WITH new_row AS (
  SELECT * FROM UNNEST(${rows.placeholders()}) AS t(${rows.names()})
)"""

        // select `id` and `tn` of all rows that match new_row.id
        val head_select = """, head_select AS (
  SELECT head.id AS id, head.tn AS tn
  FROM ${headTable.quotedName} AS head, new_row
  WHERE head.id = new_row.id
)"""

        // If the client requested an atomic update, so it provided a `version`, then
        // we only update the head row, when the version matches.
        // If we need to create a history entry, select all columns, otherwise only `id` and `tn`
        val head_row = """, head_row AS (
  SELECT ${if (insert_into_history != null)
         allColumns.joinToString(", ") { "head.${it.name} AS ${it.name}" }
    else "head.id AS id, head.tn AS tn"}
  FROM ${headTable.quotedName} AS head, new_row
  WHERE head.id = new_row.id AND (new_row.version IS NULL OR new_row.version = naksha_tn_version(head.tn))
  FOR UPDATE NOWAIT
)"""

        // If the shadow table exists, delete old states
        val clear_shadow = if (shadowTable != null) """, clear_shadow AS (
  DELETE FROM ${shadowTable.quotedName}
  WHERE id IN (SELECT id FROM head_row)
  RETURNING id, tn
)""" else ""

        // Insert the current `head_row` into history
        val head_to_history = if (insert_into_history != null) """, head_to_history AS (
  INSERT INTO ${insert_into_history.quotedName} (${PgColumn.next_tn}, ${PgColumn.copyIntoHistoryColumnNames})
  SELECT substring(head_row.tn, 9) AS ${PgColumn.next_tn}, ${PgColumn.copyIntoHistoryColumnNames} FROM head_row
  RETURNING id, tn
)""" else ""

        // Delete `head_row` from HEAD.
        val head_deleted = """, head_deleted AS (
  DELETE FROM ${headTable.quotedName}
  WHERE tn IN (SELECT tn FROM head_row)
  RETURNING id, tn
)"""

        val inserted = """, inserted AS (
INSERT INTO ${collection.headTable.quotedName} ($allColumnNames)
SELECT $allColumnNames FROM new_row
RETURNING id, tn
)"""

        val SQL = """$query$head_select$head_row$head_to_history$clear_shadow$head_deleted$inserted
SELECT 'new_row' as source, id, tn FROM new_row
UNION ALL SELECT 'head_select' as source, id, tn FROM head_select
UNION ALL SELECT 'head_row' as source, id, tn FROM head_row
${if (head_to_history.isNotEmpty()) "UNION ALL SELECT 'head_to_history' as source, id, tn FROM head_to_history" else ""}
${if (clear_shadow.isNotEmpty()) "UNION ALL SELECT 'clear_shadow' as source, id, tn FROM clear_shadow" else ""}
UNION ALL SELECT 'head_deleted' as source, id, tn FROM head_deleted
UNION ALL SELECT 'inserted' as source, id, tn FROM inserted
;"""
        return conn.prepare(SQL, rows.typeNames())
    }

    override fun doExecute(conn: PgConnection) {
        val outRows = PgColumnRows()
            .withStorageNumber(storageNumber)
            .withMapNumber(mapNumber)
            .withCollectionNumber(collectionNumber)
            .addColumn("q_id", PgType.STRING)
            .addColumn("q_version", PgType.INT64)
            .addColumn("h_id", PgType.STRING)
            .addColumn("h_tn", PgType.BYTE_ARRAY)
            .addColumn("h_version", PgType.INT64)
            .addColumn("d_id", PgType.STRING)
            .addColumn("d_tn", PgType.BYTE_ARRAY)
            .addColumn("d_version", PgType.INT64)
        val plan = plan(conn, collection)
        val array = rows.values()
        plan.execute(array).fetch().use { cursor ->
            outRows.addAll(cursor)
            for (row in 0 until outRows.size) {
                val write = writes[row]
                // TODO: detect atomic failures, then throw exception!
            }
        }
    }
}