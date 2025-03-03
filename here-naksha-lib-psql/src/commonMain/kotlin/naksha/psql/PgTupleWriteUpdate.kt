package naksha.psql

import naksha.model.Action
import naksha.model.TupleNumber
import naksha.model.objects.StoreMode
import naksha.psql.PgColumn.PgColumnCompanion.allColumns

/**
 * Execute a [DELETE][naksha.model.request.WriteOp.DELETE].
 * @since 3.0
 * @see [PgTupleWriter]
 */
internal class PgTupleWriteUpdate(session: PgSession, collection: PgCollection, writes: List<PgTupleWrite>)
    : PgTupleWriteBase(session, collection, writes)
{
    init {
        rows.addColumns(allColumns)
        rows.addColumn("version", PgType.INT64)
        var i = 0
        for (write in writes) {
            val tuple = write.tuple
            if (tuple != null) rows[i++] = tuple
        }
    }

    private fun plan(conn: PgConnection, collection: PgCollection, purge: Boolean): PgPlan {
        val headTable = collection.headTable
        val shadowTable = collection.deletedTable
        val historyTable = collection.historyTable
        val insert_into_shadow = if (!purge && shadowTable != null && collection.head.storeDeleted == StoreMode.ON) shadowTable else null
        val insert_into_history = if (historyTable != null && collection.head.storeHistory == StoreMode.ON) historyTable else null
        val do_any_insert = insert_into_shadow != null || insert_into_history != null

        // All input provided by client (the updates)
        val query = """WITH new_row AS (
  SELECT * FROM UNNEST(${rows.placeholders()}) AS t(${rows.names()},)
)"""

        // select `id` and `tn` of all rows that match query.id
        // TODO: we could allow a search filter here, so extended WHERE query!
        val head_select = """, head_select AS (
  SELECT head.id AS id, head.tn AS tn
  FROM ${headTable.quotedName} AS head, query
  WHERE head.id = query.id
)"""

        // If the client requested an atomic deleted, so it provided a `version`, then
        // we only delete the head row, when the version matches.
        // If we need to create a tombstone, select all columns, otherwise only `id` and `tn`
        val head_row = if (!do_any_insert) """, head_row AS (
  SELECT head.id AS id, head.tn AS tn
  FROM ${headTable.quotedName} AS head, query
  WHERE head.id = query.id AND (query.version IS NULL OR query.version = naksha_tn_version(head.tn))
  FOR UPDATE NOWAIT
)""" else """, head_row AS (
  SELECT ${PgColumn.allColumns.joinToString(", ") { "head.${it.name} AS ${it.name}" }}
  FROM ${headTable.quotedName} AS head, query
  WHERE head.id = query.id AND (query.version IS NULL OR query.version = naksha_tn_version(head.tn))
  FOR UPDATE NOWAIT
)"""

        // Check if any atomic delete failed (we have fewer rows in `head_row` as in `head_select`
        val check_match = """, check_match AS (
  SELECT id, tn FROM head_select
  EXCEPT
  SELECT id, tn FROM head_row
)"""
        // If any atomic delete will fail, abort the query, and return an error with the ids (max 10)
        val abort_if_mismatch = """, abort_if_mismatch AS (
  SELECT CASE 
    WHEN EXISTS (SELECT 1 FROM check_match) 
    THEN RAISE EXCEPTION 'Conflict, ids: %', 
      (SELECT STRING_AGG(id::TEXT, ', ') FROM (SELECT id FROM check_match LIMIT 10) AS subquery) 
    ELSE NULL 
  END
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

        // Create a tombstone row for each head_row
        // TODO:
        //  - mutate `flags`, set operation and action to DELETED
        //  - set `tn` to `query.final_tn`
        //  - set `tn_next` to `query.final_tn` (link close, tombstone)
        //  - set `tn_prev` to `head_row.tn`
        //  - set `tn_base` to `null`
        //
        // Note: -258049 = fffc0fff (clear operation and action bits)
        //       2 << 16 = action DELETED
        //       2 << 12 = operation DELETED
        val tombstone = if (do_any_insert) """, tombstone AS (
  SELECT
    ((head_row.flags & -196609) | (2 << 16) | (2 << 12)) AS ${PgColumn.flags},
    naksha_tn_160(naksha_tn_feature_number(head_row.tn), ${tx.version.txn}::int8, query.uid) AS ${PgColumn.tn}, 
    naksha_tn_96(${tx.version.txn}::int8, query.uid) AS ${PgColumn.next_tn}, 
    substring(head_row.tn, 9) AS ${PgColumn.prev_tn}, 
    null::bytea AS ${PgColumn.base_tn}, 
    ${PgColumn.tombstoneColumns.joinToString(", ") { "head_row.${it.name} AS ${it.name}" }}
  FROM head_row, query
  WHERE head_row.id = query.id
)""" else ""

        // Copy the tombstone into history
        val history_tombstone = if (insert_into_history != null) """, history_tombstone AS (
 INSERT INTO ${insert_into_history.quotedName} 
 (${PgColumn.flags}, ${PgColumn.tn}, ${PgColumn.next_tn}, ${PgColumn.prev_tn}, ${PgColumn.base_tn}, ${PgColumn.tombstoneColumns.joinToString(", ")})
 SELECT * FROM tombstone
 RETURNING id, tn
)""" else ""

        // Copy the tombstone into shadow
        val shadow_tombstone = if (insert_into_shadow != null) """, shadow_tombstone AS (
 INSERT INTO ${insert_into_shadow.quotedName} 
 (${PgColumn.flags}, ${PgColumn.tn}, ${PgColumn.next_tn}, ${PgColumn.prev_tn}, ${PgColumn.base_tn}, ${PgColumn.tombstoneColumns.joinToString(", ")})
 SELECT * FROM tombstone
 RETURNING id, tn
)""" else ""

        // TODO: we know that the query aborts, when any atomic delete failed
        //   - therefore, we can just return the (`query.id`, `query.final_tn`), where `query.id` = `head_row.id`
        //   - beware that the tuple of the `final_tn` can only be loaded from storage, when history is enabled
        //   - with disabled history, only delete confirmation is returned
        //   - so we return a feature-tuple with id and feature-number
        //   - we need to make feature-id mutable in feature-tuple for this case
        //   - if the client has provided the full feature, we could fake the response, but what if the real version differs !?!?!?
        val SQL = """$query$head_select$head_row$head_deleted$clear_shadow$head_to_history$tombstone$history_tombstone$shadow_tombstone
SELECT 'query' as source, query.id AS q_id, null AS tn FROM query
UNION ALL SELECT 'head_select' as source, id, tn FROM head_select
UNION ALL SELECT 'head_row' as source, id, tn FROM head_row
UNION ALL SELECT 'head_deleted' as source, id, tn FROM head_deleted
${if (clear_shadow.isNotEmpty()) "UNION ALL SELECT 'clear_shadow' as source, id, tn FROM clear_shadow" else ""}
${if (head_to_history.isNotEmpty()) "UNION ALL SELECT 'head_to_history' as source, id, tn FROM head_to_history" else ""}
${if (history_tombstone.isNotEmpty()) "UNION ALL SELECT 'history_tombstone' as source, id, tn FROM history_tombstone" else ""}
${if (shadow_tombstone.isNotEmpty()) "UNION ALL SELECT 'shadow_tombstone' as source, id, tn FROM shadow_tombstone" else ""}
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
        val plan = plan(conn, collection, false)
        val array = rows.values()
        plan.execute(array).fetch().use { cursor ->
            outRows.addAll(cursor)
            for (row in 0 until outRows.size) {
                val write = writes[row]
                if (write.isMapModification) {
                    val deletedId = outRows.getString(row, "d_id")
                    if (deletedId != null) {
                        check(write.id == deletedId)
                        val tn_bytes = outRows.getByteArray(row, "d_tn")
                        if (tn_bytes != null) {
                            val tn = TupleNumber.fromB160(tn_bytes, storageNumber, mapNumber, collectionNumber)
                            transaction.useMap(write.id, tn.featureNumber.toInt(), Action.DELETED)
                        }
                    }
                }
            }
        }
        // TODO: Verify the result
        //       We receive one row for each given write with q_id, q_version
        //       Then we get back if there is a HEAD state: h_id, h_tn, h_version
        //       And we get back, if a delete was done: d_id, d_tn, d_version
        // If no HEAD exists, we should treat it as okay (we do not fail)
        // If a HEAD exists and no deleted done, we need to fail, because of atomic request!
        // Eventually, if HEAD and DELETED are the same, everything is as expected
    }
}