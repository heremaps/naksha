package naksha.psql

import naksha.base.Platform
import naksha.base.Platform.PlatformCompanion.logger
import naksha.base.PlatformUtil
import naksha.model.*
import naksha.model.objects.StoreMode

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
internal class PgWriterDelete(writer: PgWriter, collection: PgCollection, partition: Int, writes: List<PgWrite>, val purge: Boolean = false)
    : PgWriterBase(writer, collection, partition, writes)
{
    init {
        inRows.addColumn("id", PgType.STRING)
        inRows.addColumn("expected_version", PgType.INT64)
        for (e in writes.withIndex()) {
            val row = e.index
            val write = e.value
            inRows.set(row, "id", write.id)
            inRows.set(row, "expected_version", write.version?.number)
        }
    }

    private fun plan(conn: PgConnection, collection: PgCollection): PgWriterPlan {
        // We do not insert into history, if the table does not exist, or is disabled
        val insert_into_history = if (historyTable != null && collection.head.storeHistory == StoreMode.ON) historyTable else null

        // The new version with action bits set to DELETED (2).
        val deleted_version = "(${tx.version.number}::int8 | 2)"

        // All input provided by client, `id` and optionally `expected_version`
        val query = """WITH query AS (
  SELECT * FROM UNNEST($1, $2) AS t(id, expected_version)
)"""

        // Select id and version of all rows matching query.id — used for conflict detection.
        val head_select = """, head_select AS (
  SELECT head.id AS id, head.fn AS fn, head.version AS version
  FROM ${headTable.quotedName} AS head, query
  WHERE head.id = query.id
)"""

        val effHead = collection.effectiveHeadColumns
        val effCopyHistory = collection.effectiveCopyIntoHistoryColumns
        val hasCc = PgColumn.cc in effHead

        // Select the HEAD rows to act on:
        // - Optional atomic version check (expected_version).
        // - Skip rows already deleted ((version & 3) >= 2) — idempotent.
        val head_row = """, head_row AS (
  SELECT ${effHead.joinToString(", ") { "head.${it.name} AS ${it.name}" }}
  FROM ${headTable.quotedName} AS head, query
  WHERE head.id = query.id
    AND (query.expected_version IS NULL OR (query.expected_version & -4) = (head.version & -4))
    AND (head.version & 3) < 2
)"""

        // Archive the current HEAD row into history (identical to how UPDATE does it).
        // next_version = the new deleted version, signalling "succeeded by a deletion".
        val head_to_history = if (insert_into_history != null) """, head_to_history AS (
  INSERT INTO ${insert_into_history.quotedName} (${PgColumn.next_version}, ${effCopyHistory.joinToString(",") { it.name }})
  SELECT $deleted_version AS ${PgColumn.next_version},
         ${effCopyHistory.joinToString(", ") { "head_row.${it.name} AS ${it.name}" }}
  FROM head_row
  RETURNING id, fn, version
)""" else ""

        // For DELETE: UPDATE version (action bits = DELETED) and cc in HEAD.
        // Only these two control-columns change; all data columns remain identical.
        val head_updated = if (!purge) """, head_updated AS (
  UPDATE ${headTable.quotedName}
  SET
    ${PgColumn.version.name} = $deleted_version${if (hasCc) ",\n    ${PgColumn.cc.name} = ${headTable.quotedName}.${PgColumn.cc.name} + 1" else ""}
  FROM head_row
  WHERE ${headTable.quotedName}.fn = head_row.fn
  RETURNING ${headTable.quotedName}.id, ${headTable.quotedName}.fn, ${headTable.quotedName}.version${if (hasCc) ",\n            ${headTable.quotedName}.${PgColumn.cc.name}" else ""}
)""" else ""

        // For PURGE: DELETE the HEAD row entirely.
        val head_deleted = if (purge) """, head_deleted AS (
  DELETE FROM ${headTable.quotedName}
  WHERE (fn, version) IN (SELECT fn, version FROM head_row)
  RETURNING id, fn, version
)""" else ""

        // For PURGE only: also write a tombstone record into history to explicitly mark
        // end-of-lifetime. The tombstone's next_version == version (closed interval).
        val history_tombstone = if (purge && insert_into_history != null) """, history_tombstone AS (
  INSERT INTO ${insert_into_history.quotedName}
  (${if (hasCc) "${PgColumn.cc}, " else ""}${PgColumn.fn}, ${PgColumn.version}, ${PgColumn.next_version}, ${PgColumn.base_tn}, ${PgColumn.tombstoneColumns.joinToString(", ")})
  SELECT ${if (hasCc) "head_row.cc, " else ""}head_row.fn, $deleted_version, $deleted_version, null::bytea,
         ${PgColumn.tombstoneColumns.joinToString(", ") { "head_row.${it.name} AS ${it.name}" }}
  FROM head_row
  RETURNING id, fn, version
)""" else ""

        // The returned row for DELETE is the updated HEAD row (same data, new version/cc).
        // We reconstruct it from head_row overriding the two changed columns.
        val effHeadNoCcVersionFn = effHead.filter { it !== PgColumn.cc && it !== PgColumn.version && it !== PgColumn.fn }
        val SQL = """$query$head_select$head_row$head_to_history$head_updated$head_deleted$history_tombstone
SELECT
    head_row.fn AS fn,
    $deleted_version AS version,
    ${if (hasCc) "COALESCE(head_updated.${PgColumn.cc.name}, head_row.${PgColumn.cc.name} + 1) AS ${PgColumn.cc}," else ""}
    ${effHeadNoCcVersionFn.joinToString(", ") { "head_row.${it.name} AS ${it.name}" }}${if (effHeadNoCcVersionFn.isNotEmpty()) "," else ""}
    null::int8 AS ${PgColumn.next_version},
    ${if (head_to_history.isNotEmpty()) "head_to_history.version AS head_history_version," else ""}
    ${if (history_tombstone.isNotEmpty()) "history_tombstone.version AS history_version," else ""}
    head_select.fn AS select_fn,
    head_select.version AS select_version,
    head_row.version AS head_version,
    ${if (!purge) "head_updated.version AS deleted_version," else "head_deleted.version AS deleted_version,"}
    query.id AS query_id,
    query.expected_version AS query_expected_version
FROM query
LEFT JOIN head_row ON head_row.id = query.id
${if (!purge) "LEFT JOIN head_updated ON head_updated.id = query.id" else ""}
${if (head_to_history.isNotEmpty()) "LEFT JOIN head_to_history ON head_to_history.id = query.id" else ""}
${if (history_tombstone.isNotEmpty()) "LEFT JOIN history_tombstone ON history_tombstone.id = query.id" else ""}
LEFT JOIN head_select ON head_select.id = query.id
${if (purge) "LEFT JOIN head_deleted ON head_deleted.id = query.id" else ""}
;"""
        val typeNames = inRows.typeNames()
        val pgPlan = conn.prepare(SQL, typeNames)
        return PgWriterPlan(pgPlan, SQL, typeNames)
    }

    override fun doExecute(conn: PgConnection) {
        if (writes.isEmpty()) return
        val outRows = PgColumnRows()
            .withDatabaseNumber(storageNumber)
            .withCatalogNumber(mapNumber)
            .withCollectionNumber(collectionNumber)
            .withDefaultDataEncoding(collection.head.dataEncoding ?: Naksha.DEFAULT_DATA_ENCODING)
            .addColumns(collection.effectiveHistoryColumns)
            .addColumn("head_history_version", PgType.INT64)
            .addColumn("history_version", PgType.INT64)
            .addColumn("select_fn", PgType.INT64)
            .addColumn("select_version", PgType.INT64)
            .addColumn("head_version", PgType.INT64)
            .addColumn("deleted_version", PgType.INT64)
            .addColumn("query_id", PgType.STRING)
            .addColumn("query_expected_version", PgType.INT64)
        val plan = plan(conn, collection)
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
                "${if (purge) "PURGE" else "DELETE"} for ${writes.size} rows resulted in ${inRows.size} rows deleted, ${seconds * 1000}ms, ${inRows.size / seconds} features/s, partitions: $featureCountByPartitionJoined"
            )
        }
        cursor.fetch().use { cursor ->
            outRows.addAll(cursor)
            for (row in 0 until outRows.size) {
                val write = writes[row]
                val id = outRows.getString(row, "query_id") ?: throw generalException("Missing 'query_id' in result")

                val tuple = outRows[row]
                if (tuple != null) write.tuple = tuple

                val tombstone_fn = outRows.getInt64(row, PgColumn.fn)
                val tombstone_version = outRows.getInt64(row, PgColumn.version)
                val tn = if (tombstone_fn != null && tombstone_version != null) {
                    TupleNumber(storageNumber, mapNumber, collectionNumber, tombstone_fn, Version(tombstone_version))
                } else null
                write.tupleNumber = tn

                val select_fn = outRows.getInt64(row, "select_fn")
                val select_version = outRows.getInt64(row, "select_version")
                if (select_fn == null || select_version == null) {
                    if (write.version != null) {
                        throw featureNotFound(
                            "Expected feature '$id' in version '${write.version}', but no such feature exists"
                        )
                    }
                    continue
                }
                val head_version = outRows.getInt64(row, "head_version")
                if (head_version == null || select_version != head_version) {
                    throw conflict(
                        "The feature '$id' was expected in version '${write.version}', but found in '${Version(select_version)}'"
                    )
                }
            }
        }
    }
}
