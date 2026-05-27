package naksha.psql

import naksha.base.Platform
import naksha.base.Platform.PlatformCompanion.logger
import naksha.base.PlatformUtil
import naksha.model.*
import naksha.model.objects.StoreMode
import naksha.psql.PgColumn.PgColumnCompanion.headColumns

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
        inRows.addColumns(headColumns)
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
  SELECT * FROM UNNEST(${inRows.placeholders()}) AS t(${inRows.names()})
)"""

        // Select existing.
        val head_row = """, head_row AS (
  SELECT * FROM ${headTable.quotedName}
  WHERE id IN (SELECT id FROM new_row)
)"""

        // Insert the current `head_row` into history. next_version is the new tuple's version with action set to UPDATE.
        val head_to_history = if (insert_into_history != null) """, head_to_history AS (
  INSERT INTO ${insert_into_history.quotedName} (${PgColumn.next_version}, ${PgColumn.copyIntoHistoryColumnNames})
  SELECT ((new_row.version & -4) | 1) AS ${PgColumn.next_version},
         ${PgColumn.copyIntoHistoryColumns.joinToString(", ") { "head_row.${it.name} AS ${it.name}" }}
  FROM head_row
  LEFT JOIN new_row ON new_row.id = head_row.id
  RETURNING id, fn, version
)""" else ""

        // Delete `head_row` from HEAD.
        val head_deleted = """, head_deleted AS (
  DELETE FROM ${headTable.quotedName}
  WHERE id IN (SELECT id FROM head_row)
  RETURNING id, fn, version
)"""

        // Insert new_row's, so for which there was no existing HEAD version deleted.
        // Note, when the client selected UNDEFINED for attachment, we need to turn this value into `null`!
        val head_inserted = """, head_inserted AS (
  INSERT INTO ${headTable.quotedName} (${inRows.names()})
  SELECT ${inRows.columns.joinToString(", ") {
  if (PgColumn.attachment.name == it.name)
      "CASE WHEN attachment = convert_to('undefined', 'UTF8') THEN null ELSE attachment END AS attachment"
  else
      it.name
  }} FROM new_row
  WHERE new_row.id NOT IN (SELECT id FROM head_deleted)
  RETURNING id, fn, version
)"""

        // Update means insert new_rows, but with patched values.
        // We may need to read the attachment from the HEAD version, if the client used UNDEFINED.
        // The action is encoded in the lower two bits of `version`; we set it to UPDATED (=1).
        val head_updated = """, head_updated AS (
  INSERT INTO ${headTable.quotedName} (
    ${PgColumn.cc},
    ${PgColumn.attachment},
    ${PgColumn.fn},
    ${PgColumn.version},
    ${PgColumn.updateColumnsNames})
  SELECT
    (head_row.cc + 1) AS ${PgColumn.cc},
    CASE WHEN new_row.attachment = convert_to('undefined', 'UTF8') THEN head_row.attachment ELSE new_row.attachment END AS attachment,
    new_row.fn AS ${PgColumn.fn},
    ((new_row.version & -4) | 1) AS ${PgColumn.version},
    ${PgColumn.updateColumns.joinToString(", ") { "new_row.${it.name} AS ${it.name}" }}
  FROM new_row
  LEFT JOIN head_row ON head_row.id = new_row.id
  WHERE new_row.id IN (SELECT id FROM head_deleted)
  RETURNING id, fn, version, cc, attachment
)"""

        val SQL = """$new_row$head_row$head_deleted$head_to_history$head_inserted$head_updated
SELECT
    new_row.id AS id,
    new_row.fn AS fn,
    new_row.version AS version,
    head_updated.fn AS updated_fn,
    head_updated.version AS updated_version,
    head_updated.cc AS cc,
    head_updated.attachment AS attachment,
    head_row.version AS head_row_version,
    head_deleted.version AS head_deleted_version,
    head_inserted.version AS head_inserted_version,
    null AS clear_shadow_version,
    ${if (head_to_history.isNotEmpty()) "head_to_history.version AS head_to_history_version" else "null AS head_to_history_version"}
FROM new_row
LEFT JOIN head_updated ON head_updated.id = new_row.id
LEFT JOIN head_row ON head_row.id = new_row.id
LEFT JOIN head_deleted ON head_deleted.id = new_row.id
LEFT JOIN head_inserted ON head_inserted.id = new_row.id
${if (head_to_history.isNotEmpty()) "LEFT JOIN head_to_history ON head_to_history.id = new_row.id" else ""}
;"""
        val typeNames = inRows.typeNames();
        val pgPlan = conn.prepare(SQL, typeNames);
        return PgWriterPlan(pgPlan, SQL, typeNames)
    }

    override fun doExecute(conn: PgConnection) {
        val outRows = PgColumnRows()
            .withStorageNumber(storageNumber)
            .withMapNumber(mapNumber)
            .withCollectionNumber(collectionNumber)
            .addColumn(PgColumn.id)
            .addColumn(PgColumn.fn)
            .addColumn(PgColumn.version)
            .addColumn(PgColumn.attachment)
            .addColumn(PgColumn.cc)
            .addColumn("updated_fn", PgType.INT64)
            .addColumn("updated_version", PgType.INT64)
            .addColumn("head_row_version", PgType.INT64)
            .addColumn("clear_shadow_version", PgType.INT64)
            .addColumn("head_deleted_version", PgType.INT64)
            .addColumn("head_inserted_version", PgType.INT64)
            .addColumn("head_to_history_version", PgType.INT64)
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
                val id = outRows.getString(row, "id") ?: throw generalException("Missing 'id' in SQL result")
                val fn = outRows.getInt64(row, "fn") ?: throw generalException("Missing 'fn' in SQL result")
                val versionTxn = outRows.getInt64(row, "version") ?: throw generalException("Missing 'version' in SQL result")
                val tn = TupleNumber(storageNumber, mapNumber, collectionNumber, fn, Version(versionTxn))

                // We need to patch the tuple of all inserts, that were replaced with updates!
                // The content is the same, but the action, operation, and change-count change.
                val updatedFn = outRows.getInt64(row, "updated_fn")
                val updatedVersionTxn = outRows.getInt64(row, "updated_version")
                if (updatedFn != null && updatedVersionTxn != null) {
                    val updated_tn = TupleNumber(storageNumber, mapNumber, collectionNumber, updatedFn, Version(updatedVersionTxn))
                    // If an update was done, we need the following values to be available:
                    val changeCount: Int = outRows.getInt(row, "cc") ?:
                        throw generalException("Missing 'cc' in update result for feature '$id'")
                    val attachment: ByteArray? = outRows.getByteArray(row, "attachment")
                    val write = writeByTn[tn] ?: throw generalException("Missing write state for feature '$id'")
                    val tuple = write.tuple ?: throw generalException("Missing tuple for feature '$id'")
                    write.tupleNumber = updated_tn
                    write.tuple = tuple.copy(
                        meta = tuple.meta.copy(
                            tupleNumber = updated_tn,
                            changeCount = changeCount,
                        ),
                        attachment = attachment,
                    )
                    write.action = Action.UPDATED
                }
            }
        }
    }
}