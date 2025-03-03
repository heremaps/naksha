package naksha.psql

import naksha.model.*
import naksha.model.objects.StoreMode
import naksha.psql.PgColumn.PgColumnCompanion.allColumns

/**
 * Execute UPSERT into a collection.
 * @since 3.0
 * @see [PgTupleWriter]
 */
internal class PgTupleWriteUpsert(session: PgSession, collection: PgCollection, writes: List<PgTupleWrite>)
    : PgTupleWriteBase(session, collection, writes)
{
    private val writeByTn = mutableMapOf<TupleNumber, PgTupleWrite>()

    init {
        rows.addColumns(allColumns)
        var i = 0
        for (write in writes) {
            val tuple = write.tuple
            if (tuple != null) {
                rows[i++] = tuple
                writeByTn[tuple.tupleNumber] = write
            }
        }
    }

    private fun plan(conn: PgConnection, collection: PgCollection): PgPlan {
        val headTable = collection.headTable
        val shadowTable = collection.deletedTable
        val historyTable = collection.historyTable
        val insert_into_history = if (historyTable != null && collection.head.storeHistory == StoreMode.ON) historyTable else null

        // This is what we should INSERT or UPDATE
        val new_row = """WITH new_row AS (
  SELECT * FROM UNNEST(${rows.placeholders()}) AS t(${rows.names()})
)"""

        // Select existing
        val head_row = """, head_row AS (
  SELECT * FROM ${headTable.quotedName}
  WHERE id IN (SELECT id FROM new_row)
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
  WHERE id IN (SELECT id FROM head_row)
  RETURNING id, tn
)"""

        // Insert
        val head_inserted = """, head_inserted AS (
  INSERT INTO ${collection.headTable.quotedName} (${rows.names()})
  SELECT new_row.* FROM new_row WHERE new_row.id NOT IN (SELECT id FROM head_deleted) 
  RETURNING id, tn
)"""

        // Update
        val head_updated = """, head_updated AS (
  INSERT INTO ${collection.headTable.quotedName} (${PgColumn.flags}, ${PgColumn.cc}, ${PgColumn.prev_tn}, ${PgColumn.updateColumnsNames})
  SELECT
    ((new_row.flags & -196609) | (1 << 16) | (1 << 12)) AS ${PgColumn.flags},
    (cc + 1) AS ${PgColumn.cc},
    substring(new_row.tn, 9) AS ${PgColumn.prev_tn}, 
    ${PgColumn.updateColumnsNames}
  FROM new_row WHERE new_row.id IN (SELECT id FROM head_deleted) 
  RETURNING id, tn, prev_tn, cc
)"""

        val SQL = """$new_row$head_row$clear_shadow$head_deleted$head_to_history$head_inserted$head_updated
SELECT 'new_row' as source, id, tn, prev_tn, null::int4 as cc FROM new_row
UNION ALL SELECT 'head_row' as source, id, tn, null::bytea AS prev_tn, null::int4 as cc FROM head_row
UNION ALL SELECT 'head_to_history' as source, id, tn, null::bytea AS prev_tn, null::int4 as cc FROM head_to_history
UNION ALL SELECT 'head_deleted' as source, id, tn, null::bytea AS prev_tn, null::int4 as cc FROM head_deleted
UNION ALL SELECT 'head_inserted' as source, id, tn, null::bytea AS prev_tn, null::int4 as cc FROM head_inserted
UNION ALL SELECT 'head_updated' as source, id, tn, prev_tn, cc FROM head_updated
;"""
        return conn.prepare(SQL, rows.typeNames())
    }

    override fun doExecute(conn: PgConnection) {
        val plan = plan(conn, collection)
        val array = rows.values()
        plan.execute(array).fetch().use { cursor ->
            while (cursor.next()) {
                val source: String = cursor["source"]
                if (source == "head_updated") {
                    val changeCount: Int = cursor["cc"]
                    val tn_raw: ByteArray = cursor["tn"]
                    val prev_tn_raw: ByteArray = cursor["prev_tn"]
                    val tn = TupleNumber.fromB160(tn_raw, storageNumber, mapNumber, collectionNumber)
                    val prev_tn = TupleNumber.fromB96(prev_tn_raw, storageNumber, mapNumber, collectionNumber, tn.featureNumber)
                    val write = writeByTn[tn] ?: continue
                    val tuple = write.tuple ?: continue
                    write.tuple = tuple.copy(
                        meta = tuple.meta.copy(
                            flags = tuple.meta.flags.withAction(Action.UPDATED).withOperation(Operation.UPDATED),
                            changeCount = changeCount,
                            prevTupleNumber = prev_tn,
                        )
                    )
                }
            }
        }
    }
}