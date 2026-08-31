package naksha.psql

import naksha.base.Platform
import naksha.base.Platform.PlatformCompanion.logger
import naksha.base.PlatformUtil
import naksha.psql.PgColumn.PgColumn_C.FnColumn
import naksha.psql.PgColumn.PgColumn_C.NextVersionColumn
import naksha.psql.PgColumn.PgColumn_C.VersionColumn

/**
 * Execute an **INSERT** _(aka [CREATE][naksha.model.request.WriteOp.CREATE])_ into a collection.
 *
 * **Auto-purge on re-create**: if a tombstone (deleted state, `(version & 3) >= 2`) already exists
 * in HEAD for the same `id`, it is automatically archived into history before the new feature is
 * written into HEAD. This makes re-create-after-delete transparent: the caller just issues a
 * normal CREATE and the full history is preserved correctly.
 *
 * @since 3.0
 * @see [PgWriter]
 */
internal class PgWriterInsert(
    pgWriter: PgWriter,
    pgCollection: PgCollection,
    pgWrites: List<PgWrite>,
    start: Int,
    end: Int
) : PgWriterBase(pgWriter, pgCollection, pgWrites, start, end) {

    init {
        inRows.addColumns(pgCollection.columns)
        loadAllTuple()
    }

    private fun plan(conn: PgConnection): PgWriterPlan {
        val new_row = """WITH new_row AS (
  SELECT ${inRows.decodedColumns()} FROM UNNEST(${inRows.placeholders()}) AS t(${inRows.aliases()})
)"""

        // Detect any existing tombstone in HEAD for the same id (auto-purge target).
        val head_tombstone = """, head_tombstone AS (
  SELECT ${pgCollection.joinColumns { column -> "head.$column AS $column" }}
  FROM $headIdent AS head
  JOIN new_row ON head.$FnColumn = new_row.$FnColumn
  WHERE (head.$VersionColumn & 3) >= 2
)"""

        // Archive the tombstone into history so the deletion is preserved in the audit trail.
        // next_version = new feature's version (the tombstone is succeeded by the new creation).
        val tombstone_to_history = if (historyTable != null) """, tombstone_to_history AS (
  INSERT INTO $historyIdent ($NextVersionColumn, ${pgCollection.joinColumns { column -> if (NextVersionColumn eq column) null else column.ident }})
  SELECT new_row.$VersionColumn AS $NextVersionColumn,
         ${pgCollection.joinColumns { column -> if (NextVersionColumn eq column) null else "head_tombstone.$column" }}
  FROM head_tombstone
  JOIN new_row ON new_row.$FnColumn = head_tombstone.$FnColumn
  RETURNING $FnColumn, $VersionColumn
)""" else ""

        // Overwrite the tombstone in HEAD (except for fn) with the new feature (UPDATE in-place).
        // All columns are replaced; cc resets to 1 for the new lifecycle.
        val head_overwrite = """, head_overwrite AS (
  UPDATE $headIdent
  SET ${pgCollection.joinColumns { column -> if (column eq FnColumn) null else "$column = new_row.$column" }}
  FROM new_row
  JOIN head_tombstone ON head_tombstone.$FnColumn = new_row.$FnColumn
  WHERE $headIdent.$FnColumn = head_tombstone.$FnColumn
  RETURNING $headIdent.$FnColumn, $headIdent.$VersionColumn
)"""

        // Plain INSERT for features that have no tombstone in HEAD (the normal case).
        val head_inserted = """, head_inserted AS (
  INSERT INTO $headIdent (${inRows.aliases()})
  SELECT * FROM new_row
  WHERE new_row.$FnColumn NOT IN (SELECT $FnColumn FROM head_tombstone)
  RETURNING $FnColumn, $VersionColumn
)"""

        val SQL = """$new_row$head_tombstone${tombstone_to_history}$head_overwrite$head_inserted
SELECT
    COALESCE(head_overwrite.$FnColumn, head_inserted.$FnColumn) AS $FnColumn,
    COALESCE(head_overwrite.$VersionColumn, head_inserted.$VersionColumn) AS $VersionColumn
FROM new_row
LEFT JOIN head_overwrite ON head_overwrite.$FnColumn = new_row.$FnColumn
LEFT JOIN head_inserted ON head_inserted.$FnColumn = new_row.$FnColumn
"""
        val typeNames = inRows.typeNames()
        val pgPlan = conn.prepare(SQL, typeNames)
        return PgWriterPlan(pgPlan, SQL, typeNames)
    }

    override fun doExecute(conn: PgConnection) {
        if (pgWrites.isEmpty()) return
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
        // We ignore the result, we know that if it didn't fail, it's okay.
        plan.pgPlan.execute(array).close()
        val end = Platform.currentNanos()
        val seconds = (end.toDouble() - start.toDouble()) / 1e9
        if (pgWrites.size != 1 || pgWrites[0].isFeatureModification) {
            logger.info("INSERT of ${inRows.size} rows took ${seconds * 1000}ms, therefore ${inRows.size / seconds} features/s, partitions: $featureCountByPartitionJoined")
        }
    }
}
