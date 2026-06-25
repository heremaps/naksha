package naksha.psql

import naksha.base.Platform
import naksha.base.Platform.PlatformCompanion.logger
import naksha.base.PlatformUtil
import naksha.model.illegalState
import naksha.model.objects.StandardMembers
import naksha.model.objects.StoreMode

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
    }

    val headTable = pgCollection.headTable
    val historyTable = if (pgCollection.storeHistory) pgCollection.historyTable else null
    val ID: PgColumn = pgCollection.column(StandardMembers.Id) ?: throw illegalState("The collection does not have an 'id' column.")
    val CC: PgColumn? = pgCollection.column(StandardMembers.ChangeCount)

    private fun plan(conn: PgConnection): PgWriterPlan {
        val new_row = """WITH new_row AS (
  SELECT * FROM UNNEST(${inRows.placeholders()}) AS t(${inRows.aliases()})
)"""

        // Detect any existing tombstone in HEAD for the same id (auto-purge target).
        val effectiveHead = collection.effectiveHeadColumns
        val head_tombstone = """, head_tombstone AS (
  SELECT ${effectiveHead.joinToString(", ") { "head.${it.name} AS ${it.name}" }}
  FROM ${headTable.quotedName} AS head
  JOIN new_row ON head.id = new_row.id
  WHERE (head.version & 3) >= 2
)"""

        // Archive the tombstone into history so the deletion is preserved in the audit trail.
        // next_version = new feature's version (the tombstone is succeeded by the new creation).
        val effCopyHistory = collection.effectiveCopyIntoHistoryColumns
        val tombstone_to_history = if (insert_into_history != null) """, tombstone_to_history AS (
  INSERT INTO ${insert_into_history.quotedName} (${PgColumn.next_version}, ${effCopyHistory.joinToString(",") { it.name }})
  SELECT new_row.version AS ${PgColumn.next_version},
         ${effCopyHistory.joinToString(", ") { "head_tombstone.${it.name} AS ${it.name}" }}
  FROM head_tombstone
  JOIN new_row ON new_row.id = head_tombstone.id
  RETURNING id, fn, version
)""" else ""

        // Overwrite the tombstone in HEAD with the new feature (UPDATE in-place, keeps the same fn).
        // All columns are replaced; cc resets to 1 for the new lifecycle.
        val head_overwrite = """, head_overwrite AS (
  UPDATE ${headTable.quotedName}
  SET ${effectiveHead.filter { it !== PgColumn.fn }.joinToString(", ") { "${it.name} = new_row.${it.name}" }}
  FROM new_row
  JOIN head_tombstone ON head_tombstone.id = new_row.id
  WHERE ${headTable.quotedName}.fn = head_tombstone.fn
  RETURNING ${headTable.quotedName}.id, ${headTable.quotedName}.fn, ${headTable.quotedName}.version
)"""

        // Plain INSERT for features that have no tombstone in HEAD (the normal case).
        val head_inserted = """, head_inserted AS (
  INSERT INTO ${headTable.quotedName} (${inRows.aliases()})
  SELECT * FROM new_row
  WHERE new_row.id NOT IN (SELECT id FROM head_tombstone)
  RETURNING id, fn, version
)"""

        val SQL = """$new_row$head_tombstone${tombstone_to_history}$head_overwrite$head_inserted
SELECT
    COALESCE(head_overwrite.id, head_inserted.id) AS id,
    COALESCE(head_overwrite.fn, head_inserted.fn) AS fn,
    COALESCE(head_overwrite.version, head_inserted.version) AS version
FROM new_row
LEFT JOIN head_overwrite ON head_overwrite.id = new_row.id
LEFT JOIN head_inserted ON head_inserted.id = new_row.id
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
