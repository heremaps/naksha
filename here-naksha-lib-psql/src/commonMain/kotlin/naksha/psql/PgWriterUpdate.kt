package naksha.psql

import naksha.model.*
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
    private val writeById = mutableMapOf<String, PgWrite>()

    init {
        rows.addColumns(allColumns)
        rows.addColumn("version", PgType.INT64) // needed to do atomic updates
        var i = 0
        for (write in writes) {
            val tuple = write.tuple
            if (tuple != null) {
                writeById[write.id] = write
                rows[i] = tuple
                rows.set(i, "version", write.version?.txn)
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
SELECT
    new_row.id AS id,
    new_row.tn AS tn,
    head_select.id AS existing_id,
    head_select.tn AS existing_tn,
    head_row.id AS head_id,
    ${if (head_to_history.isNotEmpty()) "head_to_history.id AS history_id," else ""}
    ${if (clear_shadow.isNotEmpty()) "clear_shadow.id AS clear_shadow_id," else ""}
    head_deleted.id AS head_deleted_id,
    inserted.id AS inserted_id
FROM new_row
LEFT JOIN head_select ON head_select.id = new_row.id
LEFT JOIN head_row ON head_row.id = new_row.id
${if (head_to_history.isNotEmpty()) "LEFT JOIN head_to_history ON head_to_history.id = new_row.id" else ""}
${if (clear_shadow.isNotEmpty()) "LEFT JOIN clear_shadow ON clear_shadow.id = new_row.id" else ""}
LEFT JOIN head_deleted ON head_deleted.id = new_row.id
LEFT JOIN inserted ON inserted.id = new_row.id
;"""
        return conn.prepare(SQL, rows.typeNames())
    }

    override fun doExecute(conn: PgConnection) {
        val rows = PgColumnRows()
            .withStorageNumber(storageNumber)
            .withMapNumber(mapNumber)
            .withCollectionNumber(collectionNumber)
            .addColumn("id", PgType.STRING)
            .addColumn("existing_id", PgType.STRING)
            .addColumn("existing_tn", PgType.BYTE_ARRAY)
            .addColumn("head_id", PgType.STRING)
        val plan = plan(conn, collection)
        val array = this.rows.values()
        plan.execute(array).fetch().use { cursor ->
            rows.addAll(cursor)
            for (rowNum in 0 until rows.size) {
                // The original `id` of the feature to update.
                val id  = rows.getString(rowNum, "id") ?: throw illegalState("Column 'id' in result must not be null")
                // The `id` and `tuple-number` currently in HEAD table.
                val existing_id = rows.getString(rowNum, "existing_id")
                if (existing_id != id) {
                    throw featureNotFound("Failed to update feature '$id', no such feature exists")
                }
                val existing_tn = rows.getByteArray(rowNum, "existing_tn") ?: throw illegalState("Missing tuple-number in HEAD select for feature '$id'")
                // The `id` from the eventually read head-row, this is only available, if the existing_id is the expected version!
                val head_id = rows.getString(rowNum, "head_id")
                if (head_id != id) { // Conflict!
                    val write = writeById[id] ?: throw illegalState("Missing write state for feature '$id'")
                    val expectedVersion = write.version ?: throw illegalState("Missing expected version for feature '$id'")
                    val tn = TupleNumber.fromB160(existing_tn, storageNumber, mapNumber, collectionNumber)
                    throw conflict("The feature '$id' was expected in version $expectedVersion, but actually found in ${tn.version}")
                }
            }
        }
    }
}