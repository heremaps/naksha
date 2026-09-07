package naksha.psql

import naksha.base.Platform
import naksha.base.Platform.PlatformCompanion.logger
import naksha.base.PlatformUtil
import naksha.base.TupleNumber
import naksha.base.conflict
import naksha.model.objects.MemberType
import naksha.psql.PgColumn.PgColumn_C.FnColumn
import naksha.psql.PgColumn.PgColumn_C.NextVersionColumn
import naksha.psql.PgColumn.PgColumn_C.VersionColumn

/**
 * Execute a [DELETE][naksha.model.request.WriteOp.DELETE] or PURGE.
 *
 * **DELETE semantics (purge=false)**:
 * - Copy the existing HEAD row into history (exactly like UPDATE does: one INSERT into history with
 *   `next_version` set to the new deleted version).
 * - UPDATE the HEAD row in-place: only `version` changes to encode `action=DELETED`
 *   `((old_version & -4) | 2)`. All other columns remain unchanged.
 * - Normal reads filter tombstones out via `(version & 3) < 2`.
 *
 * **PURGE semantics (purge=true)**:
 * - Copy the existing HEAD row into history (same single INSERT as DELETE).
 * - Additionally copy a tombstone row into history to mark the end-of-lifetime.
 * - DELETE the HEAD row entirely — the feature is completely absent from HEAD.
 *
 * @since 3.0
 * @see [PgWriter]
 */
internal class PgWriterDelete(
    pgWriter: PgWriter,
    pgCollection: PgCollection,
    pgWrites: List<PgWrite>,
    start: Int,
    end: Int,
    val purge: Boolean = false
) : PgWriterBase(pgWriter, pgCollection, pgWrites, start, end) {
    init {
        inRows.addColumn(FnColumn.ident, MemberType.INT64)
        inRows.addColumn("expected_version", MemberType.INT64)
        var row = 0
        for (i in start until end) {
            val pgWrite = pgWrites[i]
            inRows.setColumn(FnColumn.ident, row, pgWrite.featureNumber)
            inRows.setColumn("expected_version", row, pgWrite.version?.number)
            row++
        }
        check(row == (end-start))
    }

    private fun plan(conn: PgConnection): PgWriterPlan {
        // New version with action bits set to DELETED (2): clear the transaction version's action bits
        // (VERSION sentinel = 3) before OR-ing in DELETE.
        val deleted_version = "((${tx.version.number}::int8 & -4) | 2)"

        // All input provided by client, `id` and optionally `expected_version`
        val query = """WITH query AS (
  SELECT * FROM UNNEST($1, $2) AS t($FnColumn, expected_version)
)"""

        // Select `fn` and `version` of all existing rows matching query.`fn`
        val head_exists = """, head_exists AS (
  SELECT head.$FnColumn AS $FnColumn, head.$VersionColumn AS $VersionColumn
  FROM $headIdent AS head, query
  WHERE head.$FnColumn = query.$FnColumn
)"""

        // Note:
        // The client can request to delete a feature that actually is already in DELETE state.
        // If he does this atomically, the operation should always fail.
        // If he does this non-atomically, we should not move the tombstone into history, because if we would,
        // we would need to create a new tombstone. However, the feature is already deleted, deleting an already
        // deleted feature should not fail, but either move the existing HEAD into history.
        // Removing a tombstone is a PURGE operation, only this will move a tombstone into history and clear
        // the HEAD.
        //
        // Therefore: Select the HEAD rows that are valid to act on:
        // - Skip rows already deleted ((version & 3) >= 2) - As explained above, a delete must never move tombstones into history!
        // - If atomic, `expected_version` must match head.`version`, otherwise the row is not legal to operate upon.
        val head_row = """, head_row AS (
  SELECT ${pgCollection.joinColumns { column -> "head.$column AS $column" }}
  FROM $headIdent AS head, query
  WHERE head.$FnColumn = query.$FnColumn
    AND (head.$VersionColumn & 3) < 2
    AND (query.expected_version IS NULL OR (query.expected_version & -4) = (head.$VersionColumn & -4))
)"""

        // Archive the current HEAD row into history (identical to how UPDATE does it).
        // next_version = the new deleted version, signaling "succeeded by a deletion".
        val head_to_history = if (historyTable != null) """, head_to_history AS (
  INSERT INTO $historyIdent ($NextVersionColumn, ${pgCollection.joinColumns { if (it.name != NextVersionColumn.name) it.ident else null }})
  SELECT $deleted_version AS $NextVersionColumn,
         ${pgCollection.joinColumns { column -> if (column eq NextVersionColumn) null else "head_row.$column AS $column" }}
  FROM head_row
  RETURNING $FnColumn, $VersionColumn
)""" else ""

        // For DELETE: UPDATE version (action bits = DELETED) and cc in HEAD.
        // Only these two control-columns change; all data columns remain identical.
        val head_updated = if (!purge) """, head_updated AS (
  UPDATE $headIdent
  SET $VersionColumn = $deleted_version${if (CC!=null) ", $CC = $headIdent.$CC + 1" else ""}
  FROM head_row
  WHERE $headIdent.$FnColumn = head_row.$FnColumn
  RETURNING $headIdent.$FnColumn, $headIdent.$VersionColumn${if (CC!=null) ", $headIdent.$CC" else ""}
)""" else ""

        // For PURGE: DELETE the HEAD row entirely.
        val head_deleted = if (purge) """, head_deleted AS (
  DELETE FROM $headIdent
  WHERE ($FnColumn, $VersionColumn) IN (SELECT $FnColumn, $VersionColumn FROM head_row)
  RETURNING $FnColumn, $VersionColumn
)""" else ""

        // For PURGE only: also write a tombstone record into history to explicitly mark
        // end-of-lifetime. The tombstone's next_version == version (closed interval).
        val history_tombstone = if (purge && historyTable != null) """, history_tombstone AS (
  INSERT INTO ${historyTable.quotedName}
        ($VersionColumn, $NextVersionColumn, 
         ${pgCollection.joinColumns { if (VersionColumn eq it || NextVersionColumn eq it) null else it.ident }})
  SELECT $deleted_version AS $VersionColumn, $deleted_version AS $NextVersionColumn, 
         ${pgCollection.joinColumns { column -> if (VersionColumn eq column || NextVersionColumn eq column) null else "head_row.$column AS $column" }}
  FROM head_row
  RETURNING $FnColumn, $VersionColumn
)""" else ""

        // The returned row for DELETE is the updated HEAD row (same data, new version/cc).
        // We reconstruct it from head_row overriding the two changed columns.
        val SQL = """$query$head_exists$head_row$head_to_history$head_updated$head_deleted$history_tombstone
SELECT
    head_row.$FnColumn AS $FnColumn,
    $deleted_version AS $VersionColumn,
    null::int8 AS $NextVersionColumn,
    ${if (CC!=null) "COALESCE(head_updated.$CC, head_row.$CC + 1) AS $CC," else ""}
    ${pgCollection.joinColumns { col -> if (col eq CC || col eq FnColumn || col eq VersionColumn || col eq NextVersionColumn) null else "head_row.$col AS $col" }},
    ${if (head_to_history.isNotEmpty()) "head_to_history.$VersionColumn AS head_history_version," else "null AS head_history_version,"}
    ${if (history_tombstone.isNotEmpty()) "history_tombstone.version AS history_version," else "null AS history_version,"}
    head_exists.$FnColumn AS existing_fn,
    head_exists.$VersionColumn AS existing_version,
    ${if (!purge) "head_updated.$VersionColumn AS deleted_version," else "head_deleted.$VersionColumn AS deleted_version,"}
    head_row.$VersionColumn AS head_row_version
FROM query
LEFT JOIN head_row ON head_row.$FnColumn = query.$FnColumn
${if (!purge) "LEFT JOIN head_updated ON head_updated.$FnColumn = query.$FnColumn" else ""}
${if (head_to_history.isNotEmpty()) "LEFT JOIN head_to_history ON head_to_history.$FnColumn = query.$FnColumn" else ""}
${if (history_tombstone.isNotEmpty()) "LEFT JOIN history_tombstone ON history_tombstone.$FnColumn = query.$FnColumn" else ""}
LEFT JOIN head_exists ON head_exists.$FnColumn = query.$FnColumn
${if (purge) "LEFT JOIN head_deleted ON head_deleted.$FnColumn = query.$FnColumn" else ""}
;"""
        val typeNames = inRows.typeNames()
        val pgPlan = conn.prepare(SQL, typeNames)
        return PgWriterPlan(pgPlan, SQL, typeNames)
    }

    override fun doExecute(conn: PgConnection) {
        if (pgWrites.isEmpty()) return
        val outRows = PgRows().withCollection(pgCollection)
        outRows.addColumn("head_history_version", MemberType.INT64)
            .addColumn("history_version", MemberType.INT64)
            .addColumn("existing_fn", MemberType.INT64)
            .addColumn("existing_version", MemberType.INT64)
            .addColumn("deleted_version", MemberType.INT64)
            .addColumn("head_row_version", MemberType.INT64)
        val plan = plan(conn)
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
        if (pgWrites.size != 1 || pgWrites[0].isFeatureModification) {
            logger.info(
                "${if (purge) "PURGE" else "DELETE"} for ${pgWrites.size} rows resulted in ${inRows.size} rows deleted, ${seconds * 1000}ms, ${inRows.size / seconds} features/s, partitions: $featureCountByPartitionJoined"
            )
        }
        cursor.fetch().use { cursor ->
            outRows.readAll(cursor)
            for (row in 0 until outRows.size) {
                // Take values from input
                val pgWrite = pgWrites[this.start + row]
                val id = pgWrite.id
                //val op = pgWrite.op
                //val fn = pgWrite.featureNumber
                val expected_version = pgWrite.version?.number

                // `existing_fn` and `existing_version` are existing rows in HEAD, no matter in what state.
                // val existing_fn = outRows.getInt64(row, "existing_fn")
                val existing_version = outRows.getLong(row, "existing_version")

                // `head_row` selects features that are not in DELETE state and match the expected version.
                // These are the actual rows the query act upon.
                // head_row.`version` is the version that was selected from HEAD.
                // It is NULL:
                // - If the feature exists, but is DELETE
                // - If the feature exists, but the client provided `expected_version` and the head_row.`version` differs.
                val head_row_version = outRows.getLong(row, "head_row_version")
                if (head_row_version == null) {
                    // Nothing was done. This can indicate an error, but not in 100% of the cases.

                    // If the operation was atomic, the client expected a specific state.
                    if (expected_version != null) {
                        // As we generally exclude tombstones from processing, to prevent duplicate tombstones in history, the only
                        // way this is OK is when the existing tombstone is exactly the version the client expected.
                        // We do not delete the tombstone, only PURGE will do, but we treat this as success still and do not return
                        // anything.
                        if (expected_version == existing_version) continue

                        // In all other cases, this is a conflict (unexpected state, failed delete).
                        if (existing_version == null)
                            throw conflict("DELETE [$id] failed: Expected feature in version '$expected_version', but no such feature exists")
                        throw conflict("DELETE [$id] failed: Expected feature in version '$expected_version', but found it in $existing_version")
                    }

                    // The client did not expect any version, non-atomic DELETE operation.
                    // This means, HEAD is either a soft-delete aka a tombstone or does not exist.
                    // In both cases this is okay and we return nothing.
                    continue
                }

                // We have deleted something and inserted a tombstone into HEAD.
                // Let's return the deleted feature.
                val tuple = outRows[row]
                if (tuple != null) pgWrite.tuple = tuple

                val tombstone_fn = outRows.getLong(row, FnColumn)
                val tombstone_version = outRows.getLong(row, VersionColumn)
                pgWrite.tupleNumber = if (tombstone_fn != null && tombstone_version != null) {
                    TupleNumber(storageNumber, catalogNumber, collectionNumber, tombstone_fn, tombstone_version)
                } else null
            }
        }
    }
}
