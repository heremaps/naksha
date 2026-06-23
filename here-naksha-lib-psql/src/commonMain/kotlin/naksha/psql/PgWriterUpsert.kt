package naksha.psql

import naksha.base.Platform
import naksha.base.Platform.PlatformCompanion.logger
import naksha.base.PlatformUtil
import naksha.model.*
import naksha.model.objects.StandardMembers
import naksha.model.objects.StoreMode

/**
 * Execute [UPSERT][naksha.model.request.WriteOp.UPSERT] into a collection.
 * @since 3.0
 * @see [PgWriter]
 */
internal class PgWriterUpsert(writer: PgWriter, collection: PgCollection, partition: Int, writes: List<PgWrite>)
    : PgWriterBase(writer, collection, partition, writes)
{
    private val writeByTn = mutableMapOf<TupleNumber, PgWrite>()

    init {
        inRows.addColumns(collection.effectiveHeadColumns)
        val members = collection.head.members
        inRows.addCustomMembers(members)
        var i = 0
        for (write in writes) {
            val tuple = write.tuple
            if (tuple != null) {
                inRows[i] = tuple
                inRows.setCustomMembers(i, write.feature, members)
                writeByTn[tuple.tupleNumber] = write
                i++
            }
        }
    }

    private fun plan(conn: PgConnection, collection: PgCollection): PgWriterPlan {
        val insert_into_history = if (historyTable != null && collection.head.storeHistory == StoreMode.ON) historyTable else null

        // This is what we should INSERT or UPDATE.
        val new_row = """WITH new_row AS (
  SELECT * FROM UNNEST(${inRows.placeholders()}) AS t(${inRows.aliases()})
)"""

        // Select existing.
        val head_row = """, head_row AS (
  SELECT * FROM ${headTable.quotedName}
  WHERE fn IN (SELECT fn FROM new_row)
)"""

        // Insert the current `head_row` into history. next_version is the new tuple's version with action set to UPDATE.
        val effCopyHistory = collection.effectiveCopyIntoHistoryColumns
        val effUpdate = collection.effectiveUpdateColumns
        val head_to_history = if (insert_into_history != null) """, head_to_history AS (
  INSERT INTO ${insert_into_history.quotedName} (${PgColumn.next_version}, ${effCopyHistory.joinToString(",") { it.name }})
  SELECT ((new_row.version & -4) | 1) AS ${PgColumn.next_version},
         ${effCopyHistory.joinToString(", ") { "head_row.${it.name} AS ${it.name}" }}
  FROM head_row
  LEFT JOIN new_row ON new_row.fn = head_row.fn
  RETURNING id, fn, version
)""" else ""

        // Delete `head_row` from HEAD.
        val head_deleted = """, head_deleted AS (
  DELETE FROM ${headTable.quotedName}
  WHERE fn IN (SELECT fn FROM head_row)
  RETURNING id, fn, version
)"""

        // All nullable BYTE_ARRAY columns support the "keep if undefined" sentinel.
        // `feature` is mandatory (NOT NULL) and never carries the sentinel.
        val keepableByteCols = collection.effectiveHeadColumns.filter { it.type == PgType.BYTE_ARRAY && it !== PgColumn.feature }

        // Insert new rows for which there was no existing HEAD version.
        // Sentinel "undefined" on any BYTE_ARRAY column is treated as NULL on insert (no prior value to retain).
        val head_inserted = """, head_inserted AS (
  INSERT INTO ${headTable.quotedName} (${inRows.aliases()})
  SELECT ${inRows.columns.joinToString(", ") { col ->
  val q = PgUtil.quoteIdent(col.name)
  if (keepableByteCols.any { it.name == col.name })
      "CASE WHEN ${col.name} = convert_to('undefined', 'UTF8') THEN null ELSE ${col.name} END AS ${col.name}"
  else
      q
  }} FROM new_row
  WHERE new_row.fn NOT IN (SELECT fn FROM head_deleted)
  RETURNING id, fn, version
)"""

        // Update means insert new_rows, but with patched values.
        // For any BYTE_ARRAY column that carries the sentinel, keep the value from the existing HEAD row.
        // The action is encoded in the lower two bits of `version`; we set it to UPDATED (=1).
        // Only include columns that exist in the table (effectiveUpdateColumns).
        val hasCc = PgColumn.cc in collection.effectiveHeadColumns
        val updColNames = effUpdate.joinToString(",") { it.name }
        // keepable cols that are NOT in effUpdate need their own INSERT slot (currently: attachment)
        val keepableExtraCols = keepableByteCols.filter { it !in effUpdate }
        val keepableExtraColNames = keepableExtraCols.joinToString(",") { it.name }
        val head_updated = """, head_updated AS (
  INSERT INTO ${headTable.quotedName} (
    ${if (hasCc) "${PgColumn.cc}," else ""}
    ${if (keepableExtraCols.isNotEmpty()) "$keepableExtraColNames," else ""}
    ${PgColumn.fn},
    ${PgColumn.version}${if (updColNames.isNotEmpty()) ",\n    $updColNames" else ""})
  SELECT
    ${if (hasCc) "(head_row.cc + 1) AS ${PgColumn.cc}," else ""}
    ${if (keepableExtraCols.isNotEmpty()) keepableExtraCols.joinToString(", ") { col ->
        "CASE WHEN new_row.${col.name} = convert_to('undefined', 'UTF8') THEN head_row.${col.name} ELSE new_row.${col.name} END AS ${col.name}"
    } + "," else ""}
    new_row.fn AS ${PgColumn.fn},
    ((new_row.version & -4) | 1) AS ${PgColumn.version}${if (effUpdate.isNotEmpty()) ",\n    ${effUpdate.joinToString(", ") { col ->
        if (col in keepableByteCols)
            "CASE WHEN new_row.${col.name} = convert_to('undefined', 'UTF8') THEN head_row.${col.name} ELSE new_row.${col.name} END AS ${col.name}"
        else
            "new_row.${col.name} AS ${col.name}"
    }}" else ""}
  FROM new_row
  LEFT JOIN head_row ON head_row.fn = new_row.fn
  WHERE new_row.fn IN (SELECT fn FROM head_deleted)
  RETURNING id, fn, version${if (hasCc) ", cc" else ""}${keepableByteCols.joinToString("") { ", ${it.name}" }}
)"""

        val SQL = """$new_row$head_row$head_deleted$head_to_history$head_inserted$head_updated
SELECT
    new_row.id AS id,
    new_row.fn AS fn,
    new_row.version AS version,
    head_updated.fn AS updated_fn,
    head_updated.version AS updated_version,
    ${if (hasCc) "head_updated.cc AS cc," else "null::int4 AS cc,"}
    ${if (keepableByteCols.isNotEmpty()) keepableByteCols.joinToString(",\n    ") { "head_updated.${it.name} AS ${it.name}" } + "," else "null::bytea AS attachment,"}
    head_row.version AS head_row_version,
    head_deleted.version AS head_deleted_version,
    head_inserted.version AS head_inserted_version,
    null AS clear_shadow_version,
    ${if (head_to_history.isNotEmpty()) "head_to_history.version AS head_to_history_version" else "null AS head_to_history_version"}
FROM new_row
LEFT JOIN head_updated ON head_updated.fn = new_row.fn
LEFT JOIN head_row ON head_row.fn = new_row.fn
LEFT JOIN head_deleted ON head_deleted.fn = new_row.fn
LEFT JOIN head_inserted ON head_inserted.fn = new_row.fn
${if (head_to_history.isNotEmpty()) "LEFT JOIN head_to_history ON head_to_history.fn = new_row.fn" else ""}
;"""
        val typeNames = inRows.typeNames();
        val pgPlan = conn.prepare(SQL, typeNames);
        return PgWriterPlan(pgPlan, SQL, typeNames)
    }

    override fun doExecute(conn: PgConnection) {
        val keepableByteCols = collection.effectiveHeadColumns.filter { it.type == PgType.BYTE_ARRAY && it !== PgColumn.feature }
        val outRows = PgRows()
            .withDatabaseNumber(storageNumber)
            .withCatalogNumber(catalogNumber)
            .withCollectionNumber(collectionNumber)
            .addColumn(PgColumn.id)
            .addColumn(PgColumn.fn)
            .addColumn(PgColumn.version)
            .addColumn(PgColumn.cc)
            .addColumn("updated_fn", PgType.INT64)
            .addColumn("updated_version", PgType.INT64)
            .addColumn("head_row_version", PgType.INT64)
            .addColumn("clear_shadow_version", PgType.INT64)
            .addColumn("head_deleted_version", PgType.INT64)
            .addColumn("head_inserted_version", PgType.INT64)
            .addColumn("head_to_history_version", PgType.INT64)
        for (col in keepableByteCols) outRows.addColumn(col.name, PgType.BYTE_ARRAY)
        if (writes.isEmpty()) return
        val plan = plan(conn, collection)
        // TupleNumber.fromB128(inRows.columns[11].values_field[0] as ByteArray, naksha.base.Int64(0), 0, 0).partitionNumber % 16
        val array = inRows.values()
        val session = this.session
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
            logger.info("UPSERT of ${inRows.size} rows took ${seconds * 1000}ms, therefore ${inRows.size / seconds} features/s, partitions: $featureCountByPartitionJoined")
        }
        cursor.fetch().use {
            outRows.addAll(cursor)
            for (row in 0 until outRows.size) {
                val fn = outRows.getInt64(row, "fn") ?: throw generalException("Missing 'fn' in SQL result")
                val id = outRows.getString(row, "id") ?: fn.toString()
                val versionTxn = outRows.getInt64(row, "version") ?: throw generalException("Missing 'version' in SQL result")
                val tn = TupleNumber(storageNumber, catalogNumber, collectionNumber, fn, Version(versionTxn))

                // We need to patch the tuple of all inserts, that were replaced with updates!
                // The content is the same, but the action, operation, and change-count change.
                val updatedFn = outRows.getInt64(row, "updated_fn")
                val updatedVersionTxn = outRows.getInt64(row, "updated_version")
                if (updatedFn != null && updatedVersionTxn != null) {
                    val updated_tn = TupleNumber(storageNumber, catalogNumber, collectionNumber, updatedFn, Version(updatedVersionTxn))
                    // If an update was done, we need the following values to be available:
                    val hasCc = PgColumn.cc in collection.effectiveHeadColumns
                    val changeCount: Int = if (hasCc) {
                        outRows.getInt(row, "cc") ?:
                            throw generalException("Missing 'cc' in update result for feature '$id'")
                    } else 1
                    val write = writeByTn[tn] ?: throw generalException("Missing write state for feature '$id'")
                    val tuple = write.tuple ?: throw generalException("Missing tuple for feature '$id'")
                    // Read back all keepable BYTE_ARRAY columns — the DB may have substituted the sentinel
                    // with the existing value, so the in-memory tuple must reflect the final stored state.
                    val geo = if (PgColumn.geo in keepableByteCols) outRows.getByteArray(row, PgColumn.geo.name) else tuple.getByteArray(StandardMembers.Geometry)
                    val referencePoint = if (PgColumn.ref_point in keepableByteCols) outRows.getByteArray(row, PgColumn.ref_point.name) else tuple.getByteArray(StandardMembers.ReferencePoint)
                    val tags = tuple.getString(StandardMembers.XyzTags)
                    val attachment = if (PgColumn.attachment in keepableByteCols) outRows.getByteArray(row, PgColumn.attachment.name) else tuple.getByteArray(StandardMembers.XyzAttachment)
                    write.tupleNumber = updated_tn
                    val m = tuple.membersBook
                    val newMembers = if (m is naksha.jbon.HeapBook) {
                        val dict = m.copy()
                        dict.put(StandardMembers.Geometry.name, geo)
                        dict.put(StandardMembers.ReferencePoint.name, referencePoint)
                        dict.put(StandardMembers.XyzTags.name, tags)
                        dict.put(StandardMembers.XyzAttachment.name, attachment)
                        dict
                    } else m
                    write.tuple = tuple.copy(
                        version = updated_tn.version,
                        membersBook = newMembers
                    )
                    write.action = Action.UPDATE
                }
            }
        }
    }
}