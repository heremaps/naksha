package naksha.psql

import naksha.model.Action
import naksha.model.TupleNumber
import naksha.model.objects.StoreMode

/**
 * Execute a [DELETE][naksha.model.request.WriteOp.DELETE].
 * @since 3.0
 * @see [PgTupleWriter]
 */
internal class PgTupleWriteDelete(session: PgSession, collection: PgCollection, writes: List<PgTupleWrite>)
    : PgTupleWriteBase(session, collection, writes)
{
    init {
        rows.addColumn("id", PgType.STRING)
        rows.addColumn("version", PgType.INT64)
        for (e in writes.withIndex()) {
            val row = e.index
            val write = e.value
            rows.set(row, "id", write.id)
            rows.set(row, "version", write.version?.txn)
        }
    }

    private fun plan(conn: PgConnection, collection: PgCollection): PgPlan {
        val headTable = collection.headTable
        val shadowTable = collection.deletedTable
        val historyTable = collection.historyTable
        val insert_into_shadow = if (shadowTable != null && collection.head.storeDeleted == StoreMode.ON) shadowTable else null
        val insert_into_history = if (historyTable != null && collection.head.storeHistory == StoreMode.ON) historyTable else null
        val do_any_insert = insert_into_shadow != null || insert_into_history != null

        // All input provided by client, basically just `id` and optionally `txn` (aka version)
        val query = """WITH query AS (
  SELECT * FROM UNNEST($1, $2) AS t(id, txn)
)"""

        // select `id` and `tn` of all rows that match query.id
        val head_select = """, head_select AS (
  SELECT head.id AS id, head.tn AS tn
  FROM ${headTable.quotedName} AS head, query
  WHERE head.id = query.id
  FOR UPDATE NOWAIT
)"""

        // If the client has provided `tn` we only delete the head row, when the version matches.
        // We only need all columns, when we should insert into history and/or shadow.
        val head_row = if (!do_any_insert) """, head_row AS (
  SELECT head.id AS id, head.tn AS tn
  FROM ${headTable.quotedName} AS head, query
  WHERE head.id = query.id AND (query.txn IS NULL OR query.txn = naksha_tn_txn(head.tn))
)""" else """, head_row AS (
  SELECT ${PgColumn.allColumns.joinToString(", ") { "head.${it.name} AS ${it.name}" }}
  FROM ${headTable.quotedName} AS head, query
  WHERE head.id = query.id AND (query.txn IS NULL OR query.txn = naksha_tn_txn(head.tn))
)"""

        // If the shadow table exists, delete old states
        val clear_shadow = if (shadowTable != null) """, clear_shadow AS (
  DELETE FROM ${shadowTable.quotedName}
  WHERE id IN (SELECT id FROM head_row)
  RETURNING id, tn
)""" else ""

        // Insert the current HEAD into history
        val head_to_history = if (insert_into_history != null) """, head_to_history AS (
  INSERT INTO ${insert_into_history.quotedName} (${PgColumn.allColumnNames})
  SELECT ${tx.version.txn} AS ${PgColumn.txn_next}, ${PgColumn.copyIntoHistoryColumnNames} FROM head_row
  RETURNING id, tn
)""" else ""

        // Delete from HEAD.
        val head_deleted = """, head_deleted AS (
  DELETE FROM ${headTable.quotedName} AS head
  WHERE head.tn IN (SELECT tn FROM head_row)
  RETURNING head.id, head.tn
)"""

        // Create a tombstone row
        // TODO: change `txn_next` to `tn_next`, what allows to navigate in history
        //       copy `tn` into `txn_next`
        //
        val tombstone = if (do_any_insert) "" else ""
//        val tombstone = if (insert_into_history != null) """, tombstone AS (
//  INSERT INTO ${insert_into_history.quotedName} (${PgColumn.allColumnNames})
//  SELECT ${tx.version.txn} AS ${PgColumn.txn_next}, ${PgColumn.copyIntoHistoryColumnNames}
//  FROM head_row
//  RETURNING head.tn
//)""" else ""

        // Copy the tombstone into history
        val history_tombstone = if (insert_into_history != null) "" else ""

        // Copy the tombstone into shadow
        val shadow_tombstone = if (insert_into_shadow != null) "" else ""
//        val shadow_row = if (insert_into_shadow != null) """, shadow_row AS (
//  INSERT INTO ${insert_into_shadow.quotedName} (${PgColumn.allColumnNames})
//  SELECT ${tx.version.txn} AS ${PgColumn.txn_next}, ${PgColumn.copyIntoHistoryColumnNames}
//  FROM head_row
//  RETURNING head.tn
//)""" else ""

        // Create the final SQL query
        val SQL = """$query$head_select$head_row$head_deleted$clear_shadow$head_to_history$tombstone$history_tombstone$shadow_tombstone
SELECT query.id AS q_id, query.txn AS q_version,
       head_select.id AS h_id, head_select.tn AS h_tn, naksha_tn_txn(head_select.tn) AS h_version,
       head_deleted.id AS d_id, head_deleted.tn AS d_tn, naksha_tn_txn(head_deleted.tn) AS d_version
FROM query
LEFT JOIN head_select ON head_select.id = query.id
LEFT JOIN head_deleted ON head_deleted.id = query.id;"""
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