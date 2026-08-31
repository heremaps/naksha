package naksha.psql

import naksha.base.Platform
import naksha.base.Platform.PlatformCompanion.logger
import naksha.base.PlatformUtil
import naksha.base.TupleNumber
import naksha.base.conflict
import naksha.base.featureNotFound
import naksha.base.generalException
import naksha.base.illegalState
import naksha.jbon.HeapBook
import naksha.model.objects.MemberType
import naksha.model.objects.StandardMembers
import naksha.psql.PgColumn.PgColumn_C.FnColumn
import naksha.psql.PgColumn.PgColumn_C.NextVersionColumn
import naksha.psql.PgColumn.PgColumn_C.VersionColumn

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
    private val writeByFn = mutableMapOf<Long, PgWrite>()
    init {
        inRows.addColumns(pgCollection.columns)
        inRows.addColumn("expected_version", MemberType.INT64) // needed to do atomic updates
        loadAllTuple { row, tuple, pgWrite ->
            writeByFn[tuple.tupleNumber.featureNumber] = pgWrite
            // Separate column for the expected HEAD version.
            inRows.setColumn(row, "expected_version", pgWrite.version?.number)
        }
    }

    private fun plan(conn: PgConnection): PgWriterPlan {
        // All input provided by client (the updates)
        val query = """WITH new_row AS (
  SELECT ${inRows.decodedColumns()} FROM UNNEST(${inRows.placeholders()}) AS t(${inRows.aliases()})
)"""

        // Select rows from HEAD that we want to update, lock the rows for update
        val existing_rows = """, existing_rows AS (
  SELECT head.$FnColumn AS $FnColumn, head.$VersionColumn AS $VersionColumn
  FROM $headIdent AS head, new_row
  WHERE head.$FnColumn = new_row.$FnColumn
  FOR UPDATE NOWAIT
)"""

        // Select all rows from HEAD that we want to update AND that have the correct version.
        val head_row = """, head_row AS (
  SELECT ${pgCollection.joinColumns { column -> "head.$column" }}
  FROM $headIdent AS head, new_row
  WHERE head.$FnColumn = new_row.$FnColumn AND (new_row.expected_version IS NULL OR (new_row.expected_version & -4) = (head.$VersionColumn & -4))
)"""

        // Copy the current HEAD row into HISTORY; set the next version to the version for the history row.
        val head_to_history = if (historyTable != null) """, head_to_history AS (
  INSERT INTO $historyIdent ($NextVersionColumn, ${pgCollection.joinColumns { column -> if (column eq NextVersionColumn) null else column.ident }})
  SELECT new_row.$VersionColumn AS $NextVersionColumn, ${pgCollection.joinColumns { column -> if (column eq NextVersionColumn) null else "head_row.$column AS $column" }}
  FROM head_row
  LEFT JOIN new_row ON new_row.$FnColumn = head_row.$FnColumn
  RETURNING $FnColumn, $VersionColumn
)""" else ""

        // Delete HEAD rows that have been copied into history.
        val head_deleted = """, head_deleted AS (
  DELETE FROM $headIdent
  WHERE $FnColumn IN (SELECT $FnColumn FROM head_row)
  RETURNING $FnColumn, $VersionColumn
)"""

        val inserted = """, inserted AS (
INSERT INTO $headIdent ($NextVersionColumn, ${pgCollection.joinColumns { column -> if (column eq NextVersionColumn) null else column.ident }})
SELECT NULL AS $NextVersionColumn, ${pgCollection.joinColumns { column ->
    if (column eq NextVersionColumn)
        null
    else if (column.memberType == MemberType.BYTE_ARRAY)
        "CASE WHEN new_row.$column = convert_to('undefined', 'UTF8') THEN head_row.$column ELSE new_row.$column END AS $column"
    else
        "new_row.$column"
}}
FROM new_row
LEFT JOIN head_row ON head_row.$FnColumn = new_row.$FnColumn
RETURNING $FnColumn, $VersionColumn${if (byteArrayCols.isNotEmpty()) ", ${byteArrayCols.joinToString(", ") { column ->
    // Return the inserted value (RETURNING can't reference new_row) so the caller can rebuild the tuple.
    column.ident
  }}" else ""}
)"""

        val SQL = """$query$existing_rows$head_row$head_to_history$head_deleted$inserted
SELECT
    new_row.$FnColumn AS $FnColumn,
    new_row.$VersionColumn AS $VersionColumn,
    existing_rows.$FnColumn AS _existing_fn,
    existing_rows.$VersionColumn AS _existing_version,
    ${if (byteArrayCols.isNotEmpty()) byteArrayCols.joinToString(",\n    ") { column -> "inserted.$column AS $column" } + ",\n    " else ""}
    ${if (head_to_history.isNotEmpty()) "head_to_history.$FnColumn AS _history_fn," else "NULL AS _history_fn,"}
    head_deleted.$FnColumn AS _head_deleted_fn,
    inserted.$FnColumn AS _inserted_fn
FROM new_row
LEFT JOIN existing_rows ON existing_rows.$FnColumn = new_row.$FnColumn
LEFT JOIN head_row ON head_row.$FnColumn = new_row.$FnColumn
${if (head_to_history.isNotEmpty()) "LEFT JOIN head_to_history ON head_to_history.$FnColumn = new_row.$FnColumn" else ""}
LEFT JOIN head_deleted ON head_deleted.$FnColumn = new_row.$FnColumn
LEFT JOIN inserted ON inserted.$FnColumn = new_row.$FnColumn
;"""
        val typeNames = inRows.typeNames()
        val pgPlan = conn.prepare(SQL, typeNames)
        return PgWriterPlan(pgPlan, SQL, typeNames)
    }

    override fun doExecute(conn: PgConnection) {
        if (pgWrites.isEmpty()) return
        // All nullable BYTE_ARRAY columns may carry the "keep if undefined" sentinel and must be
        // read back from the DB so the in-memory tuple reflects the final stored value.
        val outRows = PgRows()
            .withDatabaseNumber(storageNumber)
            .withCatalogNumber(catalogNumber)
            .withCollectionNumber(collectionNumber)
        outRows.addColumn(FnColumn)
            .addColumn(VersionColumn)
            .addColumn("_existing_fn", MemberType.INT64)
            .addColumn("_existing_version", MemberType.INT64)
        for (column in byteArrayCols) outRows.addColumn(column)
        outRows.addColumn("_history_fn", MemberType.INT64)
        outRows.addColumn("_head_deleted_fn", MemberType.INT64)
        outRows.addColumn("_inserted_fn", MemberType.INT64)

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
            logger.info("UPDATE of ${outRows.size} rows took ${seconds * 1000}ms, therefore ${outRows.size / seconds} features/s, partitions: $featureCountByPartitionJoined")
        }
        cursor.fetch().use {
            outRows.readAll(cursor)
            for (row in 0 until outRows.size) {
                val fn = outRows.getLong(row, FnColumn) ?: throw illegalState("Column '$FnColumn' in result must not be null")
                val version = outRows.getLong(row, VersionColumn) ?: throw illegalState("Column '$VersionColumn' in result must not be null")
                val newTn = TupleNumber(storageNumber, catalogNumber, collectionNumber, fn, version)
                val pgWrite = writeByFn[fn] ?: throw illegalState("Missing write record for feature-number: $fn")
                val expected_version: Long? = pgWrite.version?.number

                // Feature should have existed.
                val existing_fn = outRows.getLong(row, "_existing_fn")
                    ?: throw featureNotFound("Failed to update feature '${pgWrite.id}', no such feature exists")
                if (existing_fn != fn) {
                    // We do not expect this to ever happen!
                    throw generalException("Internal error, feature-number mismatch for feature '${pgWrite.id}', expected fn: $fn, existing fn: $existing_fn")
                }
                val existing_version = outRows.getLong(row, "_existing_version")
                    // We do not expect this to ever happen, when we have an existing_fn there must be as well an existing_version!
                    ?: throw generalException("Internal error, missing existing version for feature '${pgWrite.id}' in result-set")
                val previousTupleNumber = TupleNumber(storageNumber, catalogNumber, collectionNumber, existing_fn, existing_version)

                // We should have updated the feature
                val inserted_fn = outRows.getLong(row, "_inserted_fn") ?: {
                    // The only defined reason is that the expected version did not match.
                    if (expected_version != null && (expected_version and -4L) != (existing_version and -4L)) {
                        throw conflict("Atomic update failed, feature '${pgWrite.id}' was expected in version $existing_version, but found to be in $existing_version")
                    }
                    // Otherwise, there is an internal error.
                    throw generalException("Internal error, failed to update feature '${pgWrite.id}', update was skipped for unknown reason")
                }

                val tuple = pgWrite.tuple ?: throw generalException("Missing tuple for feature '${pgWrite.id}}'")
                val memberBook = tuple.membersBook
                val updatedMembersBook = HeapBook.copyOf(memberBook)
                updatedMembersBook.put(StandardMembers.Tn.name, newTn)
                // Update all BYTE_ARRAY members that have been updated.
                for (column in byteArrayCols) {
                    val inValue = tuple.membersBook[column.name] as ByteArray?
                    val newValue = if (inValue == null || UNDEFINED.contentEquals(inValue)) outRows.getByteArray(row, column) else inValue
                    updatedMembersBook.put(column.name, newValue)
                }
                val updatedTuple = tuple.copy(
                    membersBook = updatedMembersBook,
                    previousTupleNumber = previousTupleNumber
                )
                pgWrite.tuple = updatedTuple
                pgWrite.tupleNumber = newTn
            }
        }
    }
}
