package naksha.psql

import naksha.base.Platform
import naksha.base.Platform.PlatformCompanion.logger
import naksha.base.PlatformUtil
import naksha.model.*
import naksha.model.objects.StoreMode
import naksha.model.objects.MemberType
import naksha.model.objects.StandardMembers
import naksha.psql.PgColumn.PgColumn_C.FN
import naksha.psql.PgColumn.PgColumn_C.NEXT_VERSION
import naksha.psql.PgColumn.PgColumn_C.VERSION

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
        inRows.addColumn(FN.ident, MemberType.INT64)
        inRows.addColumn("expected_version", MemberType.INT64)
        var row = 0
        for (i in start until end) {
            val pgWrite = pgWrites[i]
            inRows.set(row, FN.ident, pgWrite.tupleNumber!!.featureNumber)
            inRows.set(row, "expected_version", pgWrite.version?.number)
            row++
        }
        check(row == (end-start))
    }

    private fun plan(conn: PgConnection): PgWriterPlan {
        // The new version with action bits set to DELETED (2).
        val deleted_version = "(${tx.version.number}::int8 | 2)"

        // All input provided by client, `id` and optionally `expected_version`
        val query = """WITH query AS (
  SELECT * FROM UNNEST($1, $2) AS t($FN, expected_version)
)"""

        // Select id and version of all rows matching query.id — used for conflict detection.
        val head_select = """, head_select AS (
  SELECT head.$FN AS $FN, head.$VERSION AS $VERSION
  FROM $headIdent AS head, query
  WHERE head.$FN = query.$FN
)"""

        // Select the HEAD rows to act on:
        // - Optional atomic version check (expected_version).
        // - Skip rows already deleted ((version & 3) >= 2) — idempotent.
        val head_row = """, head_row AS (
  SELECT ${pgCollection.joinColumns { column -> "head.$column AS $column" }}
  FROM $headIdent AS head, query
  WHERE head.$FN = query.$FN
    AND (query.expected_version IS NULL OR (query.expected_version & -4) = (head.$VERSION & -4))
    AND (head.$VERSION & 3) < 2
)"""

        // Archive the current HEAD row into history (identical to how UPDATE does it).
        // next_version = the new deleted version, signaling "succeeded by a deletion".
        val head_to_history = if (historyTable != null) """, head_to_history AS (
  INSERT INTO $historyIdent ($NEXT_VERSION, ${pgCollection.joinColumns { if (it.name != NEXT_VERSION.name) it.ident else null }})
  SELECT $deleted_version AS $NEXT_VERSION,
         ${pgCollection.joinColumns { column -> if (column eq NEXT_VERSION) null else "head_row.$column AS $column" }}
  FROM head_row
  RETURNING $FN, $VERSION
)""" else ""

        // For DELETE: UPDATE version (action bits = DELETED) and cc in HEAD.
        // Only these two control-columns change; all data columns remain identical.
        val head_updated = if (!purge) """, head_updated AS (
  UPDATE $headIdent
  SET $VERSION = $deleted_version${if (CC!=null) ", $CC = $headIdent.$CC + 1" else ""}
  FROM head_row
  WHERE $headIdent.$FN = head_row.$FN
  RETURNING $headIdent.$FN, $headIdent.$VERSION${if (CC!=null) ", $headIdent.$CC" else ""}
)""" else ""

        // For PURGE: DELETE the HEAD row entirely.
        val head_deleted = if (purge) """, head_deleted AS (
  DELETE FROM $headIdent
  WHERE ($FN, $VERSION) IN (SELECT $FN, $VERSION FROM head_row)
  RETURNING $FN, $VERSION
)""" else ""

        // For PURGE only: also write a tombstone record into history to explicitly mark
        // end-of-lifetime. The tombstone's next_version == version (closed interval).
        val history_tombstone = if (purge && historyTable != null) """, history_tombstone AS (
  INSERT INTO ${historyTable.quotedName}
        ($VERSION, $NEXT_VERSION, 
         ${pgCollection.joinColumns { if (VERSION eq it || NEXT_VERSION eq it) null else it.ident }})
  SELECT $deleted_version AS $VERSION, $deleted_version AS $NEXT_VERSION, 
         ${pgCollection.joinColumns { column -> if (VERSION eq column || NEXT_VERSION eq column) null else "head_row.$column AS $column" }}
  FROM head_row
  RETURNING $FN, $VERSION
)""" else ""

        // The returned row for DELETE is the updated HEAD row (same data, new version/cc).
        // We reconstruct it from head_row overriding the two changed columns.
        val SQL = """$query$head_select$head_row$head_to_history$head_updated$head_deleted$history_tombstone
SELECT
    head_row.$FN AS $FN,
    $deleted_version AS $VERSION,
    null::int8 AS $NEXT_VERSION,
    ${if (CC!=null) "COALESCE(head_updated.$CC, head_row.$CC + 1) AS $CC," else ""}
    ${pgCollection.joinColumns { col -> if (col eq CC || col eq FN || col eq VERSION || col eq NEXT_VERSION) null else "head_row.$col AS $col" }},
    ${if (head_to_history.isNotEmpty()) "head_to_history.$VERSION AS head_history_version," else "null AS head_history_version"}
    ${if (history_tombstone.isNotEmpty()) "history_tombstone.version AS history_version," else "null AS history_version"}
    head_select.$FN AS select_fn,
    head_select.$VERSION AS select_version,
    head_row.$VERSION AS head_version,
    ${if (!purge) "head_updated.$VERSION AS deleted_version," else "head_deleted.$VERSION AS deleted_version,"}
    query.$FN AS query_fn,
    query.expected_version AS query_expected_version
FROM query
LEFT JOIN head_row ON head_row.$FN = query.$FN
${if (!purge) "LEFT JOIN head_updated ON head_updated.$FN = query.$FN" else ""}
${if (head_to_history.isNotEmpty()) "LEFT JOIN head_to_history ON head_to_history.$FN = query.$FN" else ""}
${if (history_tombstone.isNotEmpty()) "LEFT JOIN history_tombstone ON history_tombstone.$FN = query.$FN" else ""}
LEFT JOIN head_select ON head_select.$FN = query.$ID
${if (purge) "LEFT JOIN head_deleted ON head_deleted.$FN = query.$FN" else ""}
;"""
        val typeNames = inRows.typeNames()
        val pgPlan = conn.prepare(SQL, typeNames)
        return PgWriterPlan(pgPlan, SQL, typeNames)
    }

    override fun doExecute(conn: PgConnection) {
        if (pgWrites.isEmpty()) return
        val outRows = PgRows()
            .withDatabaseNumber(storageNumber)
            .withCatalogNumber(catalogNumber)
            .withCollectionNumber(collectionNumber)
        outRows.addColumn(FN)
        outRows.addColumn(VERSION)
        outRows.addColumn(NEXT_VERSION)
        if (CC!=null) outRows.addColumn(CC)
        for (col in pgCollection.columns) {
            if (col eq CC || col eq FN || col eq VERSION || col eq NEXT_VERSION) continue
            outRows.addColumn(col)
        }
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
                val write = pgWrites[row]
                val fn = outRows.getString(row, "query_fn") ?: throw generalException("Missing 'query_fn' in result")

                val tuple = outRows[row]
                if (tuple != null) write.tuple = tuple

                val tombstone_fn = outRows.getInt64(row, FN)
                val tombstone_version = outRows.getInt64(row, VERSION)
                val tn = if (tombstone_fn != null && tombstone_version != null) {
                    TupleNumber(storageNumber, catalogNumber, collectionNumber, tombstone_fn, tombstone_version)
                } else null
                write.tupleNumber = tn

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
            }
        }
    }
}
