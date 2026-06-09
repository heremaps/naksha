package naksha.psql

import naksha.base.Platform
import naksha.base.Platform.PlatformCompanion.logger
import naksha.base.PlatformUtil
import naksha.model.*
import naksha.model.objects.StoreMode

/**
 * Execute a [UPDATE][naksha.model.request.WriteOp.UPDATE].
 * @since 3.0
 * @see [PgWriter]
 */
internal class PgWriterUpdate(writer: PgWriter, collection: PgCollection, partition: Int, writes: List<PgWrite>)
    : PgWriterBase(writer, collection, partition, writes)
{
    private val writeById = mutableMapOf<String, PgWrite>()

    init {
        inRows.addColumns(collection.effectiveHeadColumns)
        // Separate column for the expected/atomic version, because `version` itself
        // is now a real HEAD column carrying the new tuple's version.
        inRows.addColumn("expected_version", PgType.INT64) // needed to do atomic updates
        val members = collection.head.members
        inRows.addCustomMembers(members)
        var i = 0
        for (write in writes) {
            val tuple = write.tuple
            if (tuple != null) {
                writeById[write.id] = write
                inRows[i] = tuple
                inRows.set(i, "expected_version", write.version?.txn)
                inRows.setCustomMembers(i, write.feature, members)
                i++
            }
        }
    }

    private fun plan(conn: PgConnection, collection: PgCollection): PgWriterPlan {
        val insert_into_history = if (historyTable != null && collection.head.storeHistory == StoreMode.ON) historyTable else null

        // All input provided by client (the updates)
        val query = """WITH new_row AS (
  SELECT * FROM UNNEST(${inRows.placeholders()}) AS t(${inRows.names()})
)"""

        val effHead = collection.effectiveHeadColumns
        val effCopyHistory = collection.effectiveCopyIntoHistoryColumns

        // select `id` and version of all rows that match new_row.id
        val head_select = """, head_select AS (
  SELECT head.id AS id, head.fn AS fn, head.version AS version
  FROM ${headTable.quotedName} AS head, new_row
  WHERE head.id = new_row.id
)"""

        val effHeadNames = effHead.joinToString(", ") { it.name }
        // All nullable BYTE_ARRAY columns support the "keep if undefined" sentinel.
        // `feature` is mandatory (NOT NULL) and never carries the sentinel.
        val keepableByteCols = effHead.filter { it.type == PgType.BYTE_ARRAY && it !== PgColumn.feature }

        // If the client requested an atomic update, so it provided an `expected_version`, then
        // we only update the head row, when the version matches.
        // If we need to create a history entry, select all HEAD columns, otherwise the minimal set
        // needed: id/fn/version for control flow, plus all keepable BYTE_ARRAY columns so the CASE
        // expressions in `inserted` can reference head_row.<col>.
        val leanHeadRowCols = (listOf(PgColumn.id, PgColumn.fn, PgColumn.version) + keepableByteCols).distinct()
        val head_row = """, head_row AS (
  SELECT ${if (insert_into_history != null)
         effHead.joinToString(", ") { "head.${it.name} AS ${it.name}" }
    else leanHeadRowCols.joinToString(", ") { "head.${it.name} AS ${it.name}" }}
  FROM ${headTable.quotedName} AS head, new_row
  WHERE head.id = new_row.id AND (new_row.expected_version IS NULL OR (new_row.expected_version & -4) = (head.version & -4))
  FOR UPDATE NOWAIT
)"""

        // Insert the current `head_row` into history. The new tuple's version becomes the demoted row's next_version.
        val head_to_history = if (insert_into_history != null) """, head_to_history AS (
  INSERT INTO ${insert_into_history.quotedName} (${PgColumn.next_version}, ${effCopyHistory.joinToString(",") { it.name }})
  SELECT new_row.version AS ${PgColumn.next_version},
         ${effCopyHistory.joinToString(", ") { "head_row.${it.name} AS ${it.name}" }}
  FROM head_row
  LEFT JOIN new_row ON new_row.id = head_row.id
  RETURNING id, fn, version
)""" else ""

        // Delete `head_row` from HEAD.
        val head_deleted = """, head_deleted AS (
  DELETE FROM ${headTable.quotedName}
  WHERE (fn, version) IN (SELECT fn, version FROM head_row)
  RETURNING id, fn, version
)"""

        val inserted = """, inserted AS (
INSERT INTO ${headTable.quotedName} ($effHeadNames)
SELECT ${effHead.joinToString(", ") { col ->
    if (col in keepableByteCols)
        "CASE WHEN new_row.${col.name} = convert_to('undefined', 'UTF8') THEN head_row.${col.name} ELSE new_row.${col.name} END AS ${col.name}"
    else "new_row.${col.name} AS ${col.name}"
        }}
FROM new_row
LEFT JOIN head_row ON head_row.id = new_row.id
RETURNING id, fn, version${if (keepableByteCols.isNotEmpty()) keepableByteCols.joinToString("") { ", ${it.name}" } else ""}
)"""

        val SQL = """$query$head_select$head_row$head_to_history$head_deleted$inserted
SELECT
    new_row.id AS id,
    new_row.fn AS fn,
    new_row.version AS version,
    head_select.id AS existing_id,
    head_select.version AS existing_version,
    head_row.id AS head_id,
    ${if (head_to_history.isNotEmpty()) "head_to_history.id AS history_id," else ""}
    head_deleted.id AS head_deleted_id,
    inserted.id AS inserted_id,
    ${if (keepableByteCols.isNotEmpty()) keepableByteCols.joinToString(",\n    ") { "inserted.${it.name} AS ${it.name}" } else "null::bytea AS attachment"}
FROM new_row
LEFT JOIN head_select ON head_select.id = new_row.id
LEFT JOIN head_row ON head_row.id = new_row.id
${if (head_to_history.isNotEmpty()) "LEFT JOIN head_to_history ON head_to_history.id = new_row.id" else ""}
LEFT JOIN head_deleted ON head_deleted.id = new_row.id
LEFT JOIN inserted ON inserted.id = new_row.id
;"""
        val typeNames = inRows.typeNames()
        val pgPlan = conn.prepare(SQL, typeNames)
        return PgWriterPlan(pgPlan, SQL, typeNames)
    }

    override fun doExecute(conn: PgConnection) {
        if (writes.isEmpty()) return
        // All nullable BYTE_ARRAY columns may carry the "keep if undefined" sentinel and must be
        // read back from the DB so the in-memory tuple reflects the final stored value.
        val keepableByteCols = collection.effectiveHeadColumns.filter { it.type == PgType.BYTE_ARRAY && it !== PgColumn.feature }
        val rows = PgColumnRows()
            .withStorageNumber(storageNumber)
            .withMapNumber(mapNumber)
            .withCollectionNumber(collectionNumber)
            .addColumn("id", PgType.STRING)
            .addColumn("existing_id", PgType.STRING)
            .addColumn("existing_version", PgType.INT64)
            .addColumn("head_id", PgType.STRING)
        for (col in keepableByteCols) rows.addColumn(col.name, PgType.BYTE_ARRAY)
        val plan = plan(conn, collection)
        val array = this.inRows.values()
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
            logger.info("UPDATE of ${rows.size} rows took ${seconds * 1000}ms, therefore ${rows.size / seconds} features/s, partitions: $featureCountByPartitionJoined")
        }
        cursor.fetch().use {
            rows.addAll(cursor)
            for (rowNum in 0 until rows.size) {
                // The original `id` of the feature to update.
                val id  = rows.getString(rowNum, "id") ?: throw illegalState("Column 'id' in result must not be null")
                // The `id` and `tuple-number` currently in HEAD table.
                val existing_id = rows.getString(rowNum, "existing_id")
                if (existing_id != id) {
                    throw featureNotFound("Failed to update feature '$id', no such feature exists")
                }
                val existing_version = rows.getInt64(rowNum, "existing_version") ?: throw illegalState("Missing version in HEAD select for feature '$id'")
                // Fetch the original write and tuple for this row.
                val write = writeById[id] ?: throw illegalState("Missing write state for feature '$id'")
                val tuple = write.tuple ?: throw generalException("Missing tuple for feature '$id'")
                // The `id` from the eventually read head-row, this is only available, if the existing_id is the expected version!
                val head_id = rows.getString(rowNum, "head_id")
                if (head_id != id) { // Conflict!
                    val expectedVersion = write.version ?: throw illegalState("Missing expected version for feature '$id'")
                    throw conflict("The feature '$id' was expected in version $expectedVersion, but actually found in ${Version(existing_version)}")
                }
                // Patch back all BYTE_ARRAY columns whose stored value may differ from what the client sent
                // (sentinel "undefined" causes the DB to retain the existing value).
                val geo = if (PgColumn.geo in keepableByteCols) rows.getByteArray(rowNum, PgColumn.geo.name) else tuple.getByteArray(naksha.model.objects.StandardMembers.Geometry)
                val referencePoint = if (PgColumn.ref_point in keepableByteCols) rows.getByteArray(rowNum, PgColumn.ref_point.name) else tuple.getByteArray(naksha.model.objects.StandardMembers.ReferencePoint)
                val tags = tuple.getStringMember(naksha.model.objects.StandardMembers.Tags)
                val attachment = if (PgColumn.attachment in keepableByteCols) rows.getByteArray(rowNum, PgColumn.attachment.name) else tuple.getByteArray(naksha.model.objects.StandardMembers.Attachment)
                val oldGeo = tuple.getByteArray(naksha.model.objects.StandardMembers.Geometry)
                val oldRefPoint = tuple.getByteArray(naksha.model.objects.StandardMembers.ReferencePoint)
                val oldAttachment = tuple.getByteArray(naksha.model.objects.StandardMembers.Attachment)
                val needsPatch = (oldGeo == null || !oldGeo.contentEquals(geo ?: ByteArray(0)))
                    || (oldRefPoint == null || !oldRefPoint.contentEquals(referencePoint ?: ByteArray(0)))
                    || (oldAttachment == null || !oldAttachment.contentEquals(attachment ?: ByteArray(0)))
                if (needsPatch) {
                    val m = tuple.members
                    val newMembers = if (m is naksha.jbon.HeapBook) {
                        val dict = m.copy()
                        dict.put("geo", geo)
                        dict.put("ref_point", referencePoint)
                        dict.put("tags", tags)
                        dict.put("attachment", attachment)
                        dict
                    } else m
                    write.tuple = tuple.copy(members = newMembers)
                }
            }
        }
    }
}