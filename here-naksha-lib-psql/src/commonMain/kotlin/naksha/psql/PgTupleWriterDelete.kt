package naksha.psql

import naksha.model.objects.StoreMode

/**
 * Execute a [DELETE][naksha.model.request.WriteOp.DELETE].
 * @since 3.0
 * @see [PgTupleWriter]
 */
internal class PgTupleWriterDelete(session: PgSession, collection: PgCollection, writes: List<PgTupleWrite>)
    : PgTupleWriteBase(session, collection, writes)
{

    private fun plan(conn: PgConnection, collection: PgCollection): PgPlan {
        val shadow = collection.deleted
        val history = collection.history
        val insert_into_shadow = if (shadow != null && collection.nakshaCollection.storeDeleted != StoreMode.ON) shadow else null
        val insert_into_history = if (history != null && collection.nakshaCollection.storeHistory != StoreMode.ON) history else null

        val DELETE_FROM_SHADOW = if (shadow != null) """, deleted_shadow AS (
  DELETE FROM ${shadow.quotedName} AS shadow
  USING query 
  WHERE shadow.id = query.id
  RETURNING shadow.tn
)""" else ""

        // We only need to create a deleted state, when we need to write either history or shadow!
//        val DELETED_STATE = if (insert_into_shadow != null || insert_into_history != null) """
//, delete_row AS (
//    INSERT INTO ${shadow?.quotedName} ($allColumnNames)
//    SELECT next_txn=${tx.version.txn}, columnNamesWithoutTxnNext
//    FROM head_row h
//    RETURNING tn
//)\n
//""" else ""
//        val INSERT_INTO_SHADOW = if (shadow == null || collection.nakshaCollection.storeDeleted != StoreMode.ON) "" else """
//, shadowed AS (
//    INSERT INTO ${shadow.quotedName} ($allColumnNames)
//    SELECT next_txn=${tx.version.txn}, columnNamesWithoutTxnNext
//    FROM head_row h
//    RETURNING tn
//)\n
//"""

        // TODO: delete from shadow, insert into shadow and history, if needed
        val SQL = """
WITH query AS (
  SELECT * FROM UNNEST($1, $2) AS t(id, version)
), head_row AS (
  SELECT head.id AS id, head.tn AS tn FROM ${collection.head.quotedName} AS head, query
  WHERE head.id = query.id
  FOR UPDATE NOWAIT
), head_deleted AS (
  DELETE FROM ${collection.head.quotedName} AS head
  USING head_row, query
  WHERE head.tn = head_row.tn AND (query.version IS NULL OR query.version = naksha_tn_version(head.tn))
  RETURNING head.id, head.tn
)$DELETE_FROM_SHADOW
SELECT query.id AS q_id, query.version AS q_version,
       head_row.id AS h_id, head_row.tn AS h_tn, naksha_tn_version(head_row.tn) AS h_version,
       head_deleted.id AS d_id, head_deleted.tn AS d_tn, naksha_tn_version(head_deleted.tn) AS d_version
FROM query
LEFT JOIN head_row ON head_row.id = query.id
LEFT JOIN head_deleted ON head_deleted.id = query.id;
"""
        return conn.prepare(SQL, arrayOf(PgType.STRING_ARRAY.text, PgType.INT64_ARRAY.text))
    }

    fun deleteColumnValues() : Array<Any?> = arrayOf(id, version)

    fun execute(conn: PgConnection) {
        val plan = plan(conn, collection)
        val array = deleteColumnValues()
        plan.execute(array).close()
        // TODO: Verify the result
        //       We receive one row for each given write with q_id, q_version
        //       Then we get back if there is a HEAD state: h_id, h_tn, h_version
        //       And we get back, if a delete was done: d_id, d_tn, d_version
        // If no HEAD exists, we should treat it as okay (we do not fail)
        // If a HEAD exists and no deleted done, we need to fail, because of atomic request!
        // Eventually, if HEAD and DELETED are the same, everything is as expected
    }
}