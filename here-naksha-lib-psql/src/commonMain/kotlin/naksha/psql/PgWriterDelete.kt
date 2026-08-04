package naksha.psql

import naksha.base.Base
import naksha.base.Base.BaseCompanion.logger
import naksha.base.BaseUtil
import naksha.base.TupleNumber
import naksha.base.Version
import naksha.base.conflict
import naksha.base.featureNotFound
import naksha.base.generalException
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
            inRows.set(row, FnColumn.ident, pgWrite.id.number)
            inRows.set(row, "expected_version", pgWrite.version)
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

        // Select id and version of all rows matching query.id — used for conflict detection.
        val head_select = """, head_select AS (
  SELECT head.$FnColumn AS $FnColumn, head.$VersionColumn AS $VersionColumn
  FROM $headIdent AS head, query
  WHERE head.$FnColumn = query.$FnColumn
)"""

        // Select the HEAD rows to act on:
        // - Optional atomic version check (expected_version).
        // - Skip rows already deleted ((version & 3) >= 2) — idempotent.
        val head_row = """, head_row AS (
  SELECT ${pgCollection.joinColumns { column -> "head.$column AS $column" }}
  FROM $headIdent AS head, query
  WHERE head.$FnColumn = query.$FnColumn
    AND (query.expected_version IS NULL OR (query.expected_version & -4) = (head.$VersionColumn & -4))
    AND (head.$VersionColumn & 3) < 2
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
        val SQL = """$query$head_select$head_row$head_to_history$head_updated$head_deleted$history_tombstone
SELECT
    head_row.$FnColumn AS $FnColumn,
    $deleted_version AS $VersionColumn,
    null::int8 AS $NextVersionColumn,
    ${if (CC!=null) "COALESCE(head_updated.$CC, head_row.$CC + 1) AS $CC," else ""}
    ${pgCollection.joinColumns { col -> if (col eq CC || col eq FnColumn || col eq VersionColumn || col eq NextVersionColumn) null else "head_row.$col AS $col" }},
    ${if (head_to_history.isNotEmpty()) "head_to_history.$VersionColumn AS head_history_version," else "null AS head_history_version,"}
    ${if (history_tombstone.isNotEmpty()) "history_tombstone.version AS history_version," else "null AS history_version,"}
    head_select.$FnColumn AS select_fn,
    head_select.$VersionColumn AS select_version,
    head_row.$VersionColumn AS head_version,
    ${if (!purge) "head_updated.$VersionColumn AS deleted_version," else "head_deleted.$VersionColumn AS deleted_version,"}
    query.$FnColumn AS query_fn,
    query.expected_version AS query_expected_version
FROM query
LEFT JOIN head_row ON head_row.$FnColumn = query.$FnColumn
${if (!purge) "LEFT JOIN head_updated ON head_updated.$FnColumn = query.$FnColumn" else ""}
${if (head_to_history.isNotEmpty()) "LEFT JOIN head_to_history ON head_to_history.$FnColumn = query.$FnColumn" else ""}
${if (history_tombstone.isNotEmpty()) "LEFT JOIN history_tombstone ON history_tombstone.$FnColumn = query.$FnColumn" else ""}
LEFT JOIN head_select ON head_select.$FnColumn = query.$FnColumn
${if (purge) "LEFT JOIN head_deleted ON head_deleted.$FnColumn = query.$FnColumn" else ""}
;"""
        val typeNames = inRows.typeNames()
        val pgPlan = conn.prepare(SQL, typeNames)
        return PgWriterPlan(pgPlan, SQL, typeNames)
    }

    override fun doExecute(conn: PgConnection) {
        if (pgWrites.isEmpty()) return
        val outRows = PgRows().withPgCollection(pgCollection)
        outRows.addColumn("head_history_version", MemberType.INT64)
            .addColumn("history_version", MemberType.INT64)
            .addColumn("select_fn", MemberType.INT64)
            .addColumn("select_version", MemberType.INT64)
            .addColumn("head_version", MemberType.INT64)
            .addColumn("deleted_version", MemberType.INT64)
            .addColumn("query_fn", MemberType.INT64)
            .addColumn("query_expected_version", MemberType.INT64)
        val plan = plan(conn)
        val array = inRows.values()
        if (BaseUtil.ENABLE_INFO) {
            if (session.logQueries) {
                session.logAtInfo(plan.sql)
            }
            if (session.logExplain) {
                val explain = session.explain(conn, false, plan.sql, plan.typeNames, array)
                session.logAtInfo(explain)
            }
        }
        val start = Base.currentNanos()
        val cursor = plan.pgPlan.execute(array)
        val end = Base.currentNanos()
        val seconds = (end.toDouble() - start.toDouble()) / 1e9
        if (pgWrites.size != 1 || pgWrites[0].isFeatureModification) {
            logger.info(
                "${if (purge) "PURGE" else "DELETE"} for ${pgWrites.size} rows resulted in ${inRows.size} rows deleted, ${seconds * 1000}ms, ${inRows.size / seconds} features/s, partitions: $featureCountByPartitionJoined"
            )
        }
        cursor.fetch().use { cursor ->
            outRows.readAll(cursor)
            for (row in 0 until outRows.size) {
                val write = pgWrites[row]
                // query_fn is an int8 column, read as Int64; used only for diagnostics below.
                val fn = outRows.getInt64(row, "query_fn") ?: throw generalException("Missing 'query_fn' in result")

                val select_fn = outRows.getInt64(row, "select_fn")
                val select_version = outRows.getInt64(row, "select_version")
                if (select_fn == null || select_version == null) {
                    if (write.version != null) {
                        throw featureNotFound(
                            "Expected feature '$fn' in version '${write.version}', but no such feature exists"
                        )
                    }
                    continue
                }
                val head_version = outRows.getInt64(row, "head_version")
                if (head_version == null || select_version != head_version) {
                    throw conflict(
                        "The feature '$fn' was expected in version '${write.version}', but found in '${Version(select_version)}'"
                    )
                }

                val tuple = outRows[row]
                if (tuple != null) write.tuple = tuple

                val tombstone_fn = outRows.getInt64(row, FnColumn)
                val tombstone_version = outRows.getInt64(row, VersionColumn)
                write.tupleNumber = if (tombstone_fn != null && tombstone_version != null) {
                    val databaseNumber = databaseId.number
                    val catalogNumber = catalogId.number.toInt()
                    val collectionNumber = collectionId.number.toInt()
                    TupleNumber(databaseNumber, catalogNumber, collectionNumber, tombstone_fn, tombstone_version)
                } else null
            }
        }
    }
}
