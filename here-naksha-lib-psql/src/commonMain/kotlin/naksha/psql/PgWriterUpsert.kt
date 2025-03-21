package naksha.psql

import naksha.base.Platform
import naksha.base.Platform.PlatformCompanion.logger
import naksha.model.*
import naksha.model.objects.StoreMode
import naksha.psql.PgColumn.PgColumnCompanion.allColumns

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
        inRows.addColumns(allColumns)
        var i = 0
        for (write in writes) {
            val tuple = write.tuple
            if (tuple != null) {
                inRows[i++] = tuple
                writeByTn[tuple.tupleNumber] = write
            }
        }
    }

    private fun plan(conn: PgConnection, collection: PgCollection): PgPlan {
        val headTable = if (partition >= 0) collection.headTable.partitions[partition] else collection.headTable
        val deletedTable = collection.deletedTable
        val shadowTable: PgTable? = if (deletedTable != null && partition >= 0) deletedTable.partitions[partition] else deletedTable
        val hstYear = collection.historyTable?.get(version)
        val historyTable = if (hstYear != null && partition >= 0) hstYear.partitions[partition] else hstYear
        val insert_into_history = if (historyTable != null && collection.head.storeHistory == StoreMode.ON) historyTable else null

        // This is what we should INSERT or UPDATE.
        val new_row = """WITH new_row AS NOT MATERIALIZED (
  SELECT * FROM UNNEST(${inRows.placeholders()}) AS t(${inRows.names()})
)"""

        // Select existing.
        val head_row = """, head_row AS NOT MATERIALIZED (
  SELECT * FROM ${headTable.quotedName}
  WHERE id IN (SELECT id FROM new_row)
)"""

        // If the shadow table exists, delete old states.
        val clear_shadow = if (shadowTable != null) """, clear_shadow AS (
  DELETE FROM ${shadowTable.quotedName}
  WHERE id IN (SELECT id FROM head_row)
  RETURNING id, tn
)""" else ""

        // Insert the current `head_row` into history.
        val head_to_history = if (insert_into_history != null) """, head_to_history AS (
  INSERT INTO ${insert_into_history.quotedName} (${PgColumn.next_tn}, ${PgColumn.copyIntoHistoryColumnNames})
  SELECT substring(new_row.tn, 9) AS ${PgColumn.next_tn},
         ${PgColumn.copyIntoHistoryColumns.joinToString(", ") { "head_row.${it.name} AS ${it.name}" }}
  FROM head_row
  LEFT JOIN new_row ON new_row.id = head_row.id
  RETURNING id, tn
)""" else ""

        // Delete `head_row` from HEAD.
        val head_deleted = """, head_deleted AS (
  DELETE FROM ${headTable.quotedName}
  WHERE id IN (SELECT id FROM head_row)
  RETURNING id, tn
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
  RETURNING id, tn
)"""

        // Update means insert new_rows, but with patched values.
        // We may need to read the attachment from the HEAD version, if the client used UNDEFINED.
        val head_updated = """, head_updated AS (
  INSERT INTO ${headTable.quotedName} (
    ${PgColumn.flags},
    ${PgColumn.cc},
    ${PgColumn.prev_tn},
    ${PgColumn.attachment},
    ${PgColumn.tn},
    ${PgColumn.updateColumnsNames})
  SELECT
    ((new_row.flags & -196609) | (1 << 16) | (1 << 12)) AS ${PgColumn.flags},
    (head_row.cc + 1) AS ${PgColumn.cc},
    substring(head_row.tn, 9) AS ${PgColumn.prev_tn},
    CASE WHEN new_row.attachment = convert_to('undefined', 'UTF8') THEN head_row.attachment ELSE new_row.attachment END AS attachment,
    naksha_tn_160(naksha_tn_feature_number(new_row.tn), naksha_tn_version(new_row.tn), ((naksha_tn_uid(new_row.tn) & -4) | 1)) AS ${PgColumn.tn},
    ${PgColumn.updateColumns.joinToString(", ") { "new_row.${it.name} AS ${it.name}" }}
  FROM new_row
  LEFT JOIN head_row ON head_row.id = new_row.id
  WHERE new_row.id IN (SELECT id FROM head_deleted) 
  RETURNING id, tn, prev_tn, cc, attachment
)"""

        val SQL = """$new_row$head_row$clear_shadow$head_deleted$head_to_history$head_inserted$head_updated
SELECT
    new_row.id AS id,
    new_row.tn AS tn,
    head_updated.tn AS updated_tn,
    head_updated.prev_tn AS prev_tn,
    head_updated.cc AS cc,
    head_updated.attachment AS attachment,
    head_row.tn AS head_row_tn,
    clear_shadow.tn AS clear_shadow_tn,
    head_deleted.tn AS head_deleted_tn,
    head_inserted.tn AS head_inserted_tn,
    ${if (head_to_history.isNotEmpty()) "head_to_history.tn AS head_to_history_tn" else "null AS head_to_history_tn"}
FROM new_row
LEFT JOIN head_updated ON head_updated.id = new_row.id
LEFT JOIN head_row ON head_row.id = new_row.id
LEFT JOIN clear_shadow ON clear_shadow.id = new_row.id
LEFT JOIN head_deleted ON head_deleted.id = new_row.id
LEFT JOIN head_inserted ON head_inserted.id = new_row.id
${if (head_to_history.isNotEmpty()) "LEFT JOIN head_to_history ON head_to_history.id = new_row.id" else ""}
;"""
        return conn.prepare(SQL, inRows.typeNames())
    }

    override fun doExecute(conn: PgConnection) {
        val outRows = PgColumnRows()
            .withStorageNumber(storageNumber)
            .withMapNumber(mapNumber)
            .withCollectionNumber(collectionNumber)
            .addColumn("id", PgType.STRING)
            .addColumn("tn", PgType.BYTE_ARRAY)
            .addColumn("updated_tn", PgType.BYTE_ARRAY)
            .addColumn("prev_tn", PgType.BYTE_ARRAY)
            .addColumn("cc", PgType.INT)
            .addColumn("attachment", PgType.BYTE_ARRAY)
            .addColumn("head_row_tn", PgType.BYTE_ARRAY)
            .addColumn("clear_shadow_tn", PgType.BYTE_ARRAY)
            .addColumn("head_deleted_tn", PgType.BYTE_ARRAY)
            .addColumn("head_inserted_tn", PgType.BYTE_ARRAY)
            .addColumn("head_to_history_tn", PgType.BYTE_ARRAY)
        if (writes.isEmpty()) return
        val plan = plan(conn, collection)
        // TupleNumber.fromB160(inRows.columns[11].values_field[0] as ByteArray, naksha.base.Int64(0), 0, 0).partitionNumber % 16
        val array = inRows.values()
        val start = Platform.currentNanos()
        val cursor = plan.execute(array)
        val end = Platform.currentNanos()
        val seconds = (end.toDouble() - start.toDouble()) / 1e9
        if (writes.size != 1 || writes[0].isFeatureModification) {
            logger.info("UPSERT of ${inRows.size} rows took ${seconds * 1000}ms, therefore ${inRows.size / seconds} features/s, partitions: $featureCountByPartitionJoined")
        }
        cursor.fetch().use {
            outRows.addAll(cursor)
            for (row in 0 until outRows.size) {
                val id = outRows.getString(row, "id") ?: throw generalException("Missing 'id' in SQL result")
                val tn = outRows.getB160(row, "tn") ?: throw generalException("Missing 'tn' in SQL result")

                // We need to patch the tuple of all inserts, that were replaced with updates!
                // The content is the same, but the action, operation, change-count, and prev_tn change!
                val updated_tn = outRows.getB160(row, "updated_tn")
                if (updated_tn != null) {
                    // If an update was done, we need the following values to be available:
                    val changeCount: Int = outRows.getInt(row, "cc") ?:
                        throw generalException("Missing 'cc' in update result for feature '$id'")
                    val attachment: ByteArray? = outRows.getByteArray(row, "attachment")
                    val prev_tn = outRows.getB96(row, "prev_tn", tn.featureNumber)
                    val write = writeByTn[tn] ?: throw generalException("Missing write state for feature '$id'")
                    val tuple = write.tuple ?: throw generalException("Missing tuple for feature '$id'")
                    write.tupleNumber = updated_tn
                    write.tuple = tuple.copy(
                        meta = tuple.meta.copy(
                            tupleNumber = updated_tn,
                            flags = tuple.meta.flags.withAction(Action.UPDATED).withOperation(Operation.UPDATED),
                            changeCount = changeCount,
                            prevTupleNumber = prev_tn,
                        ),
                        attachment = attachment,
                    )
                    write.action = Action.UPDATED
                }
            }
        }
    }
}