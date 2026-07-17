package naksha.psql

import naksha.base.Action
import naksha.base.Int64
import naksha.base.Platform
import naksha.base.Platform.PlatformCompanion.logger
import naksha.base.PlatformUtil
import naksha.base.TupleNumber
import naksha.base.generalException
import naksha.jbon.HeapBook
import naksha.model.objects.MemberType
import naksha.model.objects.StandardMembers
import naksha.psql.PgColumn.PgColumn_C.FN
import naksha.psql.PgColumn.PgColumn_C.NEXT_VERSION
import naksha.psql.PgColumn.PgColumn_C.VERSION

/**
 * Execute [UPSERT][naksha.model.request.WriteOp.UPSERT] into a collection.
 * @since 3.0
 * @see [PgWriter]
 */
internal class PgWriterUpsert(
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
        loadAllTuple { _, tuple, pgWrite -> writeByFn[tuple.tupleNumber.featureNumber] = pgWrite }
    }

    private fun plan(conn: PgConnection): PgWriterPlan {
        // This is what we should INSERT or UPDATE.
        val new_row = """WITH new_row AS (
  SELECT ${inRows.newRowProjection()} FROM UNNEST(${inRows.placeholders()}) AS t(${inRows.aliases()})
)"""

        // Select existing.
        val head_row = """, head_row AS (
  SELECT * FROM $headIdent
  WHERE $FN IN (SELECT $FN FROM new_row)
)"""

        // Copy all current head row's into history that are in CREATE or UPDATE action.
        // We need to set `next_version` to the new tuple's version, which is new row's version plus action UPDATE.
        val head_to_history = if (historyTable != null) """, head_to_history AS (
  INSERT INTO $historyIdent ($NEXT_VERSION, 
         ${pgCollection.joinColumns { column -> if (column eq NEXT_VERSION) null else column.ident }})
  SELECT ((new_row.$VERSION & -4) | 1) AS $NEXT_VERSION,
         ${pgCollection.joinColumns { column -> if (column eq NEXT_VERSION) null else "head_row.$column AS $column" }}
  FROM head_row
  LEFT JOIN new_row ON new_row.$FN = head_row.$FN
  WHERE (head_row.$VERSION & 3) < 2 -- action = CREATE or UPDATE
  RETURNING $FN, $VERSION
)""" else ""

        // Copy all current head row's into history that are in DELETE action.
        // In this case, we need to set `next_version` to the old tuple's version to signal end of lifetime.
        val tombstone_to_history = if (historyTable != null) """, tombstone_to_history AS (
  INSERT INTO $historyIdent ($NEXT_VERSION, 
         ${pgCollection.joinColumns { column -> if (column eq NEXT_VERSION) null else column.ident }})
  SELECT head_row.$VERSION AS $NEXT_VERSION,
         ${pgCollection.joinColumns { column -> if (column eq NEXT_VERSION) null else "head_row.$column AS $column" }}
  FROM head_row
  LEFT JOIN new_row ON new_row.$FN = head_row.$FN
  WHERE (head_row.$VERSION & 3) = 2 -- action = DELETE
  RETURNING $FN, $VERSION
)""" else ""

        // Delete `head_row` from HEAD.
        val head_deleted = """, head_deleted AS (
  DELETE FROM $headIdent
  WHERE $FN IN (SELECT $FN FROM head_row)
  RETURNING $FN, $VERSION
)"""

        // Copy new rows for which there was no existing HEAD version or HEAD was in action DELETE.
        // Sentinel "undefined" on any BYTE_ARRAY column is treated as NULL on insert (no prior value to retain).
        // Note: We expact that the ASCII string `undefined` is put into the BYTE_ARRAY, when it should be undefined.
        //       That is why we check for `CASE WHEN $ident = convert_to('undefined', 'UTF8') ...`

        // ------------------------------------------ DO UPDATE --------------------------------------------------------
        val head_updated = """, head_updated AS (
  INSERT INTO $headIdent (${inRows.aliases()})
  SELECT ${inRows.columns.joinToString(", ") { colWithValue ->
    val ident = PgUtil.quoteIdent(colWithValue.alias)
    val pgColumn = colWithValue.pgColumn
    if (pgColumn eq CC) {
        "(head_row.$CC + 1) AS $CC" // Increment Change-Count
    } else if (pgColumn eq VERSION){
        "((new_row.$VERSION & -4) | 1) AS $VERSION" // Set lower 2 bit to UPDATE (1)
    } else if (pgColumn eq NEXT_VERSION){
        "NULL AS $NEXT_VERSION" // in HEAD, next version must always be NULL
    } else if (pgColumn.memberType == MemberType.BYTE_ARRAY) {
        // Keep the existing HEAD value when the client sent the `undefined` sentinel, else take the new value.
        "CASE WHEN new_row.$ident = convert_to('undefined', 'UTF8') THEN head_row.$ident ELSE new_row.$ident END AS $ident"
    } else "new_row.$ident"
  }} FROM new_row
  LEFT JOIN head_row ON new_row.$FN = head_row.$FN
  WHERE new_row.$FN IN (SELECT $FN FROM head_to_history)
    AND new_row.$FN IN (SELECT $FN FROM head_deleted)
  RETURNING $FN, $VERSION${if (CC != null) ", $CC" else ""}${if (byteArrayCols.isNotEmpty()) ", ${byteArrayCols.joinToString(", ") { it.ident }}" else ""}
)""" // Note: head_to_history contains all existing HEAD row in CREATE or UPDATE action.

        // ------------------------------------------ DO INSERT --------------------------------------------------------
        val head_inserted = """, head_inserted AS (
  INSERT INTO $headIdent (${inRows.aliases()})
  SELECT ${inRows.columns.joinToString(", ") { colWithValue ->
    val ident = PgUtil.quoteIdent(colWithValue.alias)
    val pgColumn = colWithValue.pgColumn
    if (pgColumn eq CC) {
        "1 AS $CC" // Change-Count = 1
    } else if (pgColumn eq VERSION){
        "($VERSION & -4) AS $VERSION" // Clear lower 2 bit to set action = CREATE (0)
    } else if (pgColumn eq NEXT_VERSION){
        "NULL AS $NEXT_VERSION" // in HEAD, next version must always be NULL
    } else if (pgColumn.memberType == MemberType.BYTE_ARRAY) {
        "CASE WHEN $ident = convert_to('undefined', 'UTF8') THEN null ELSE $ident END AS $ident"
    } else ident
}} FROM new_row
  WHERE new_row.$FN NOT IN (SELECT $FN FROM head_updated)
  RETURNING $FN, $VERSION
)""" // Note: for the real inserts, we do not need to return byte-array columns, because we send them, so its clear what the content will be.

        val SQL = """$new_row$head_row$head_to_history$tombstone_to_history$head_deleted$head_updated$head_inserted
SELECT
    new_row.$FN AS $FN,
    new_row.$VERSION AS $VERSION,
    ${if (byteArrayCols.isNotEmpty()) byteArrayCols.joinToString(",\n    ") { column -> "head_updated.$column AS $column" } + ",\n    " else ""}
    ${if (CC!=null) "head_updated.$CC AS $CC," else "null::int4 AS $CC,"}
    head_updated.$FN AS _updated_fn,
    head_updated.$VERSION AS _updated_version,
    head_row.$VERSION AS _head_row_version,
    head_deleted.$VERSION AS _head_deleted_version,
    head_inserted.$VERSION AS _head_inserted_version,
    ${if (head_to_history.isNotEmpty()) "head_to_history.$VERSION AS _head_to_history_version" else "null AS _head_to_history_version"}
FROM new_row
LEFT JOIN head_updated ON head_updated.$FN = new_row.$FN
LEFT JOIN head_row ON head_row.$FN = new_row.$FN
LEFT JOIN head_deleted ON head_deleted.$FN = new_row.$FN
LEFT JOIN head_inserted ON head_inserted.$FN = new_row.$FN
${if (head_to_history.isNotEmpty()) "LEFT JOIN head_to_history ON head_to_history.$FN = new_row.$FN" else ""}
;"""
        // Notes:
        // head_updated contains all rows that have been executed an UPDATE, all others performed an INSERT
        val typeNames = inRows.typeNames()
        val pgPlan = conn.prepare(SQL, typeNames)
        return PgWriterPlan(pgPlan, SQL, typeNames)
    }

    override fun doExecute(conn: PgConnection) {
        val outRows = PgRows()
            .withDatabaseNumber(storageNumber)
            .withCatalogNumber(catalogNumber)
            .withCollectionNumber(collectionNumber)
        outRows.addColumn(FN)
               .addColumn(VERSION)
        for (column in byteArrayCols) outRows.addColumn(column)
        if (CC!=null) outRows.addColumn(CC)
        outRows.addColumn("_updated_fn", MemberType.INT64)
               .addColumn("_updated_version", MemberType.INT64)
               .addColumn("_head_row_version", MemberType.INT64)
               .addColumn("_head_deleted_version", MemberType.INT64)
               .addColumn("_head_inserted_version", MemberType.INT64)
               .addColumn("_clear_shadow_version", MemberType.INT64)
               .addColumn("_head_to_history_version", MemberType.INT64)
        if (pgWrites.isEmpty()) return
        val plan = plan(conn)
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
        if (pgWrites.size != 1 || pgWrites[0].isFeatureModification) {
            logger.info("UPSERT of ${inRows.size} rows took ${seconds * 1000}ms, therefore ${inRows.size / seconds} features/s, partitions: $featureCountByPartitionJoined")
        }
        cursor.fetch().use {
            outRows.readAll(cursor)
            for (row in 0 until outRows.size) {
                val updated_fn = outRows.getInt64(row, "_updated_fn")
                val updated_version = outRows.getInt64(row, "_updated_version")
                if (updated_fn != null && updated_version != null) {
                    // UPDATE was executed.
                    val pgWrite = writeByFn[updated_fn] ?: throw generalException("Received _updated_fn '$updated_fn', but found no matching PgWrite")
                    val previousVersion = outRows.getInt64(row, "_head_row_version")
                        ?: throw generalException("Missing previous HEAD version for updated feature '${pgWrite.id}'")
                    val updatedTupleNumber = TupleNumber(storageNumber, catalogNumber, collectionNumber, updated_fn, updated_version)
                    val previousTupleNumber = TupleNumber(storageNumber, catalogNumber, collectionNumber, updated_fn, previousVersion)
                    // If an update was done, we need the following values to be available:
                    val change_count: Int = if (CC!=null) {
                        outRows.getInt(row, CC) ?: throw generalException("Missing '$CC' in update result for feature '${pgWrite.id}'")
                    } else 1

                    val insertTuple = pgWrite.tuple ?: throw generalException("Missing tuple for feature '${pgWrite.id}}'")
                    val insertMemberBook = insertTuple.membersBook
                    val updatedMembersBook = HeapBook.copyOf(insertMemberBook)
                    if (CC != null) updatedMembersBook.put(CC.name, change_count)
                    updatedMembersBook.put(StandardMembers.Tn.name, updatedTupleNumber)
                    // Update all BYTE_ARRAY members that have been updated.
                    for (column in byteArrayCols) {
                        val inValue = insertTuple.membersBook[column.name] as ByteArray?
                        val newValue = if (inValue == null || UNDEFINED.contentEquals(inValue)) outRows.getByteArray(row, column) else inValue
                        updatedMembersBook.put(column.name, newValue)
                    }
                    val updatedTuple = insertTuple.copy(
                        membersBook = updatedMembersBook,
                        previousTupleNumber = previousTupleNumber
                    )
                    pgWrite.tuple = updatedTuple
                    pgWrite.tupleNumber = updatedTupleNumber
                    pgWrite.action = Action.UPDATE
                } // else INSERT executed, that means the tuple was inserted as given.
//                val fn = outRows.getInt64(row, FN) ?: throw generalException("Missing 'fn' in SQL result")
//                val new_version = outRows.getInt64(row, VERSION) ?: throw generalException("Missing 'version' in SQL result")
//                val new_tn = TupleNumber(storageNumber, catalogNumber, collectionNumber, fn, new_version)
            }
        }
    }
}
