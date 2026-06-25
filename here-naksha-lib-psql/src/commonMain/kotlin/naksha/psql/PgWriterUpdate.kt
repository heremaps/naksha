package naksha.psql

import naksha.base.Int64
import naksha.base.Platform
import naksha.base.Platform.PlatformCompanion.logger
import naksha.base.PlatformUtil
import naksha.model.*
import naksha.model.objects.MemberType
import naksha.model.objects.StandardMembers
import naksha.model.objects.StoreMode
import naksha.psql.PgColumn.PgColumn_C.FN
import naksha.psql.PgColumn.PgColumn_C.NEXT_VERSION
import naksha.psql.PgColumn.PgColumn_C.VERSION

/**
 * Execute a [UPDATE][naksha.model.request.WriteOp.UPDATE].
 * @since 3.0
 * @see [PgWriter]
 */
internal class PgWriterUpdate(
    pgWriter: PgWriter,
    pgCollection: PgCollection,
    pgWrites: List<PgWrite>,
    start: Int,
    end: Int
) : PgWriterBase(pgWriter, pgCollection, pgWrites, start, end) {

    // All columns that are BYTE_ARRAYs (can be empty)
    private val byteArrayCols = pgCollection.columns.filter { it.memberType == MemberType.BYTE_ARRAY }
    private val writeByFn = mutableMapOf<Int64, PgWrite>()
    init {
        inRows.addColumns(pgCollection.columns)
        inRows.addColumn("expected_version", MemberType.INT64) // needed to do atomic updates
        loadAllTuple { row, tuple, pgWrite ->
            writeByFn[tuple.tupleNumber.featureNumber] = pgWrite
            // Separate column for the expected HEAD version.
            inRows.set(row, "expected_version", pgWrite.version?.number)
        }
    }

    private fun plan(conn: PgConnection): PgWriterPlan {
        // All input provided by client (the updates)
        val query = """WITH new_row AS (
  SELECT * FROM UNNEST(${inRows.placeholders()}) AS t(${inRows.aliases()})
)"""

        // Select head rows that we want to update, lock the rows for update
        // We do not select rows in deleted state, as they can't be updated.
        // The reason is: Update explicitly only updates a living object
        // So it must fail when there is no existing object or only a tombstone exists (logically the same as not existing)
        val head_row = """, head_row AS (
  SELECT ${pgCollection.joinColumns { column -> "head.$column" }}
  FROM $headIdent AS head, new_row
  WHERE head.$FN = new_row.$FN AND (new_row.expected_version IS NULL OR (new_row.expected_version & -4) = (head.$VERSION & -4))
  FOR UPDATE NOWAIT
)"""

        // Copy the current HEAD row into HISTORY; set the next version to the version for the history row.
        val head_to_history = if (historyTable != null) """, head_to_history AS (
  INSERT INTO $historyIdent ($NEXT_VERSION, ${pgCollection.joinColumns { column -> if (column eq NEXT_VERSION) null else column.ident }})
  SELECT new_row.$VERSION AS $NEXT_VERSION, ${pgCollection.joinColumns { column -> if (column eq NEXT_VERSION) null else "head_row.$column" }})
  FROM head_row
  LEFT JOIN new_row ON new_row.$FN = head_row.$FN
  RETURNING $FN, $VERSION
)""" else ""

        // Delete HEAD rows that have been copied into history.
        val head_deleted = """, head_deleted AS (
  DELETE FROM $headIdent
  WHERE $FN IN (SELECT $FN FROM head_row)
  RETURNING $FN, $VERSION
)"""

        val inserted = """, inserted AS (
INSERT INTO $headIdent ($NEXT_VERSION, ${pgCollection.joinColumns { column -> if (column eq NEXT_VERSION) null else column.ident }})
SELECT NULL AS $NEXT_VERSION, ${pgCollection.joinColumns { column -> 
    if (column eq NEXT_VERSION)
        null
    else if (column.memberType == MemberType.BYTE_ARRAY)
        "CASE WHEN new_row.$column = convert_to('undefined', 'UTF8') THEN head_row.$column ELSE new_row.$column END AS $column"
    else
        "head_row.$column" 
}})
FROM new_row
LEFT JOIN head_row ON head_row.$FN = new_row.$FN
RETURNING $FN, $VERSION${if (byteArrayCols.isNotEmpty()) ", ${byteArrayCols.joinToString(", ") { column ->
    // We return NULL, when the input contained data, because the client knows this already, no need to send back.
    // If the client provided `undefined`, we return the actual value so we can build a correct tuple.
    "CASE WHEN new_row.$column = convert_to('undefined', 'UTF8') THEN $column ELSE null END AS $column"
  }}" else ""}
)"""

        val SQL = """$query$head_row$head_to_history$head_deleted$inserted
SELECT
    new_row.$FN AS $FN,
    new_row.$VERSION AS $VERSION,
    head_row.$FN AS _existing_fn,
    head_row.$VERSION AS _existing_version,
    ${if (byteArrayCols.isNotEmpty()) byteArrayCols.joinToString(",\n    ") { column -> "inserted.$column AS $column" } + ",\n    " else ""}
    ${if (head_to_history.isNotEmpty()) "head_to_history.$FN AS _history_fn," else "NULL AS _history_fn"}
    head_deleted.$FN AS _head_deleted_fn,
    inserted.$FN AS _inserted_fn,
FROM new_row
LEFT JOIN head_row ON head_row.$FN = new_row.$FN
${if (head_to_history.isNotEmpty()) "LEFT JOIN head_to_history ON head_to_history.$FN = new_row.$FN" else ""}
LEFT JOIN head_deleted ON head_deleted.$FN = new_row.$FN
LEFT JOIN inserted ON inserted.$FN = new_row.$FN
;"""
        val typeNames = inRows.typeNames()
        val pgPlan = conn.prepare(SQL, typeNames)
        return PgWriterPlan(pgPlan, SQL, typeNames)
    }

    override fun doExecute(conn: PgConnection) {
        if (pgWrites.isEmpty()) return
        // All nullable BYTE_ARRAY columns may carry the "keep if undefined" sentinel and must be
        // read back from the DB so the in-memory tuple reflects the final stored value.
        val rows = PgRows()
            .withDatabaseNumber(storageNumber)
            .withCatalogNumber(catalogNumber)
            .withCollectionNumber(collectionNumber)
        rows.addColumn(FN)
            .addColumn(VERSION)
            .addColumn("_existing_fn", MemberType.INT64)
            .addColumn("_existing_version", MemberType.INT64)
        for (column in byteArrayCols) rows.addColumn(column)
        rows.addColumn("_history_fn", MemberType.INT64)
        rows.addColumn("_head_deleted_fn", MemberType.INT64)
        rows.addColumn("_inserted_fn", MemberType.INT64)

        val plan = plan(conn)
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
        if (pgWrites.size != 1 || pgWrites[0].isFeatureModification) {
            logger.info("UPDATE of ${rows.size} rows took ${seconds * 1000}ms, therefore ${rows.size / seconds} features/s, partitions: $featureCountByPartitionJoined")
        }
        cursor.fetch().use {
            rows.readAll(cursor)
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
//                val write = writeById[id] ?: throw illegalState("Missing write state for feature '$id'")
//                val tuple = write.tuple ?: throw generalException("Missing tuple for feature '$id'")
//                // The `id` from the eventually read head-row, this is only available, if the existing_id is the expected version!
//                val head_id = rows.getString(rowNum, "head_id")
//                if (head_id != id) { // Conflict!
//                    val expectedVersion = write.version ?: throw illegalState("Missing expected version for feature '$id'")
//                    throw conflict("The feature '$id' was expected in version $expectedVersion, but actually found in ${Version(existing_version)}")
//                }
//                // Patch back all BYTE_ARRAY columns whose stored value may differ from what the client sent
//                // (sentinel "undefined" causes the DB to retain the existing value).
//                val geo = if (PgColumn.geo in keepableByteCols) rows.getByteArray(rowNum, PgColumn.geo.name) else tuple.getByteArray(StandardMembers.Geometry)
//                val referencePoint = if (PgColumn.ref_point in keepableByteCols) rows.getByteArray(rowNum, PgColumn.ref_point.name) else tuple.getByteArray(StandardMembers.ReferencePoint)
//                val tags = tuple.getString(StandardMembers.XyzTags)
//                val attachment = if (PgColumn.attachment in keepableByteCols) rows.getByteArray(rowNum, PgColumn.attachment.name) else tuple.getByteArray(StandardMembers.XyzAttachment)
//                val oldGeo = tuple.getByteArray(StandardMembers.Geometry)
//                val oldRefPoint = tuple.getByteArray(StandardMembers.ReferencePoint)
//                val oldAttachment = tuple.getByteArray(StandardMembers.XyzAttachment)
//                val needsPatch = (oldGeo == null || !oldGeo.contentEquals(geo ?: ByteArray(0)))
//                    || (oldRefPoint == null || !oldRefPoint.contentEquals(referencePoint ?: ByteArray(0)))
//                    || (oldAttachment == null || !oldAttachment.contentEquals(attachment ?: ByteArray(0)))
//                if (needsPatch) {
//                    val m = tuple.membersBook
//                    val newMembers = if (m is naksha.jbon.HeapBook) {
//                        val dict = m.copy()
//                        dict.put(StandardMembers.Geometry.name, geo)
//                        dict.put(StandardMembers.ReferencePoint.name, referencePoint)
//                        dict.put(StandardMembers.XyzTags.name, tags)
//                        dict.put(StandardMembers.XyzAttachment.name, attachment)
//                        dict
//                    } else m
//                    write.tuple = tuple.copy(membersBook = newMembers)
//                }
            }
        }
    }
}