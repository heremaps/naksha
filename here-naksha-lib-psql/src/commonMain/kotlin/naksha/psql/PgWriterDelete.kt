package naksha.psql

import naksha.base.Platform
import naksha.base.Platform.PlatformCompanion.logger
import naksha.base.PlatformUtil
import naksha.model.*
import naksha.model.objects.StoreMode
import naksha.psql.PgColumn.PgColumnCompanion.allColumns

/**
 * Execute a [DELETE][naksha.model.request.WriteOp.DELETE].
 * @since 3.0
 * @see [PgWriter]
 */
internal class PgWriterDelete(writer: PgWriter, collection: PgCollection, partition: Int, writes: List<PgWrite>)
    : PgWriterBase(writer, collection, partition, writes)
{
    init {
        inRows.addColumn("id", PgType.STRING)
        inRows.addColumn("version", PgType.INT64)
        for (e in writes.withIndex()) {
            val row = e.index
            val write = e.value
            inRows.set(row, "id", write.id)
            inRows.set(row, "version", write.version?.txn)
        }
    }

    private fun plan(conn: PgConnection, collection: PgCollection, purge: Boolean): PgWriterPlan {
        // We do not insert into shadow, if the table does not exist, is disabled, or we are asked to PURGE
        val insert_into_shadow = if (!purge && shadowTable != null && collection.head.storeDeleted == StoreMode.ON) shadowTable else null
        // We do not insert into history, if the table does not exist, or is disabled
        val insert_into_history = if (historyTable != null && collection.head.storeHistory == StoreMode.ON) historyTable else null
        val do_any_insert = insert_into_shadow != null || insert_into_history != null
        // If there is no history and no shadow, or there is no history when purging, we need to
        //   return the tombstone tuple, so the deletion tuple, because it will not be available
        //   within the database, and we do not yet know if the client will request it from the
        //   success-result returned to it!
        // Eventually this means, we need to create the tombstone all the time, we always need it!
        val return_tuple = !do_any_insert || (purge && insert_into_history == null)

        // All input provided by client, `id` and optionally `version`
        val query = """WITH query AS (
  SELECT * FROM UNNEST($1, $2) AS t(id, version)
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
        val head_row = """, head_row AS (
  SELECT ${allColumns.joinToString(", ") { "head.${it.name} AS ${it.name}" }}
  FROM ${headTable.quotedName} AS head, query
  WHERE head.id = query.id AND (query.version IS NULL OR (query.version & -4) = (naksha_tn_version(head.tn) & -4))
)"""

        // If the shadow table exists, delete old states
        val clear_shadow = if (shadowTable != null) """, clear_shadow AS (
  DELETE FROM ${shadowTable.quotedName}
  WHERE id IN (SELECT id FROM head_row)
  RETURNING id, tn
)""" else ""

        // Create a tombstone row for each head_row (row actually to be deleted)
        // We either return this, or we insert it into history and/or shadow!
        val tombstone = """, tombstone AS (
  SELECT
    ((head_row.flags & -196609) | (2 << 16) | (2 << 12)) AS ${PgColumn.flags},
    (head_row.cc + 1) AS ${PgColumn.cc},
    naksha_tn_128(naksha_tn_feature_number(head_row.tn), (${tx.version.txn}::int8 | 2)) AS ${PgColumn.tn}, 
    naksha_tn_128(naksha_tn_feature_number(head_row.tn), (${tx.version.txn}::int8 | 2)) AS ${PgColumn.next_tn}, 
    head_row.tn AS ${PgColumn.prev_tn}, 
    null::bytea AS ${PgColumn.base_tn}, 
    ${PgColumn.tombstoneColumns.joinToString(", ") { "head_row.${it.name} AS ${it.name}" }}
  FROM head_row, query
  WHERE head_row.id = query.id
)"""

        // Insert the current `head_row` into history
        val head_to_history = if (insert_into_history != null) """, head_to_history AS (
  INSERT INTO ${insert_into_history.quotedName} (${PgColumn.next_tn}, ${PgColumn.copyIntoHistoryColumnNames})
  SELECT tombstone.tn AS ${PgColumn.next_tn},
         ${PgColumn.copyIntoHistoryColumns.joinToString(", ") { "head_row.${it.name} AS ${it.name}" }}
  FROM head_row
  LEFT JOIN tombstone ON tombstone.id = head_row.id
  RETURNING id, tn
)""" else ""

        // Delete `head_row` from HEAD.
        val head_deleted = """, head_deleted AS (
  DELETE FROM ${headTable.quotedName}
  WHERE tn IN (SELECT tn FROM head_row)
  RETURNING id, tn
)"""

        // Copy the tombstone into history.
        val history_tombstone = if (insert_into_history != null) """, history_tombstone AS (
 INSERT INTO ${insert_into_history.quotedName} 
 (${PgColumn.flags}, ${PgColumn.cc}, ${PgColumn.tn}, ${PgColumn.next_tn}, ${PgColumn.prev_tn}, ${PgColumn.base_tn}, ${PgColumn.tombstoneColumns.joinToString(", ")})
 SELECT * FROM tombstone
 RETURNING id, tn
)""" else ""

        // Copy the tombstone into shadow
        val shadow_tombstone = if (insert_into_shadow != null) """, shadow_tombstone AS (
 INSERT INTO ${insert_into_shadow.quotedName} 
 (${PgColumn.flags}, ${PgColumn.cc}, ${PgColumn.tn}, ${PgColumn.next_tn}, ${PgColumn.prev_tn}, ${PgColumn.base_tn}, ${PgColumn.tombstoneColumns.joinToString(", ")})
 SELECT * FROM tombstone
 RETURNING id, tn
)""" else ""

        // `head_select`: The `id` and `tn` of all rows from HEAD matching the `query.id`, no matter if `query.version` matches
        // `head_row`: All rows that are currently in HEAD, matching `query.id` and optionally `query.version`, will be copied to history
        // `head_deleted`: The `id` and `tn` of the rows actually deleted from HEAD, should be all `head_row`
        // `clear_shadow`: The `id` and `tn` of all rows that were deleted from shadow
        //                 This information is available as soon as the shadow table exists, no matter of other options
        // `head_to_history`: The `id` and `tn` of the HEAD tuple copied into history, if history should be written
        // `tombstone`: The row to be inserted into history and/or shadow and/or to be returned to client
        // `history_tombstone`: The `id` and `tn` of the tombstone, if written into history
        // `shadow_tombstone`: The `id` and `tn` of the tombstone, if written into shadow
        // Beware:
        // Postgres is very good at optimizing, even while it makes the result wrong.
        // It will, sadly, not execute CTE queries that are not needed to generate the result.
        // Therefore, we need to read `tn` of all parts, if available, otherwise Postgres will not execute them!
        val SQL = """$query$head_select$head_row$head_deleted$clear_shadow$tombstone$head_to_history$history_tombstone$shadow_tombstone
SELECT
    ${ if (return_tuple) allColumns.joinToString(", ") { "tombstone.${it.name} AS ${it.name}" } else "tombstone.tn AS tn" },
    ${if (clear_shadow.isNotEmpty()) "clear_shadow.tn AS shadow_tn," else ""}
    ${if (head_to_history.isNotEmpty()) "head_to_history.tn AS head_history_tn," else ""}
    ${if (history_tombstone.isNotEmpty()) "history_tombstone.tn AS history_tn," else ""}
    ${if (shadow_tombstone.isNotEmpty()) "shadow_tombstone.tn AS shadow_tn," else ""}
    head_select.tn AS select_tn,
    head_row.tn AS head_tn,
    head_deleted.tn AS deleted_tn,
    query.id AS query_id,
    query.version AS query_version
FROM query
LEFT JOIN tombstone ON tombstone.id = query.id
${if (clear_shadow.isNotEmpty()) "LEFT JOIN clear_shadow ON clear_shadow.id = query.id" else ""}
${if (head_to_history.isNotEmpty()) "LEFT JOIN head_to_history ON head_to_history.id = query.id" else ""}
${if (history_tombstone.isNotEmpty()) "LEFT JOIN history_tombstone ON history_tombstone.id = query.id" else ""}
${if (shadow_tombstone.isNotEmpty()) "LEFT JOIN shadow_tombstone ON shadow_tombstone.id = query.id" else ""}
LEFT JOIN head_select ON head_select.id = query.id
LEFT JOIN head_row ON head_row.id = query.id
LEFT JOIN head_deleted ON head_deleted.id = query.id
;"""
        val typeNames = inRows.typeNames()
        val pgPlan = conn.prepare(SQL, typeNames)
        return PgWriterPlan(pgPlan, SQL, typeNames)
    }

    override fun doExecute(conn: PgConnection) {
        if (writes.isEmpty()) return
        val outRows = PgColumnRows()
            .withStorageNumber(storageNumber)
            .withMapNumber(mapNumber)
            .withCollectionNumber(collectionNumber)
            // We return all columns from tombstone, except we store tombstone, then we return only `tn`!
            .addColumns(allColumns)
            .addColumn("shadow_tn", PgType.BYTE_ARRAY)
            .addColumn("head_history_tn", PgType.BYTE_ARRAY)
            .addColumn("history_tn", PgType.BYTE_ARRAY)
            .addColumn("select_tn", PgType.BYTE_ARRAY)
            .addColumn("head_tn", PgType.BYTE_ARRAY)
            .addColumn("deleted_tn", PgType.BYTE_ARRAY)
            .addColumn("query_id", PgType.STRING)
            .addColumn("query_version", PgType.INT64)
        val plan = plan(conn, collection, false)
        val array = inRows.values()
        if (PlatformUtil.ENABLE_INFO) {
            if (session.logQueries) {
                session.logAtInfo(plan.sql)
            }
            if (session.logExplain) {
                val explain = session.explain(conn, false, plan.sql, plan.typeNames, array)
                session.logAtInfo(explain)
            }
        }
        val start = Platform.currentNanos()
        val cursor = plan.pgPlan.execute(array)
        val end = Platform.currentNanos()
        val seconds = (end.toDouble() - start.toDouble()) / 1e9
        if (writes.size != 1 || writes[0].isFeatureModification) {
            logger.info(
                "DELETE for ${writes.size} rows resulted in ${inRows.size} rows deleted, ${seconds * 1000}ms, ${inRows.size / seconds} features/s, partitions: $featureCountByPartitionJoined"
            )
        }
        cursor.fetch().use { cursor ->
            outRows.addAll(cursor)
            for (row in 0 until outRows.size) {
                val write = writes[row]
                // The `id` that should be deleted (same as write.id).
                val id = outRows.getString(row, "query_id") ?: throw generalException("Missing 'query_id' in result")

                // We only have a tuple if history and shadow are disabled, or, we `PURGE` with history disabled!
                val tuple = outRows[row]
                if (tuple != null) write.tuple = tuple

                // The tombstone tuple-number, which is the final deleted state, but only exists, if a feature was deleted.
                val tn = outRows.getB128(row, PgColumn.tn)
                write.tupleNumber = tn

                // The tuple-number of the HEAD state, before deletion.
                val select_tn = outRows.getB128(row, "select_tn")
                if (select_tn == null) {
                    // If there was no HEAD state (the feature does not exist), we do not fail.
                    // Except: The client requested an atomic delete.
                    if (write.version != null) {
                        throw featureNotFound(
                            "Expected feature '$id' in version '${write.version}', but no such feature exists"
                        )
                    }
                    continue
                }
                // If the HEAD tuple does not match the requested version, then `head_tn` will be `null`,
                // while there will an existing HEAD tuple, so `selected_tn` is the current, not matching state.
                // Otherwise, we expect that `select_tn` == `head_tn`
                if (select_tn != outRows.getB128(row, "head_tn")) {
                    throw conflict(
                        "The feature '$id' was expected in version '${write.version}', but found in '${select_tn.version}'"
                    )
                }
            }
        }
    }
}