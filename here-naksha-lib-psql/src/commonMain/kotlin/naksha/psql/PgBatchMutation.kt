package naksha.psql

import naksha.base.Action
import naksha.base.Id
import naksha.base.NakshaError
import naksha.base.NakshaException
import naksha.base.TupleNumber
import naksha.base.Version
import naksha.base.illegalArg
import naksha.base.illegalState
import naksha.jbon.MemberValuesBook
import naksha.model.NewTuple
import naksha.model.Tuple
import naksha.model.objects.MemberType
import naksha.model.objects.StandardMembers
import naksha.psql.PgUtil.PgUtilCompanion.quoteIdent

internal enum class PgMutationOp(val code: Int) { CREATE(0), UPSERT(1), UPDATE(2), DELETE(3), PURGE(4), EXPLICIT_DELETE(5) }

/** Immutable logical inputs; fn is a logical feature number and is converted to its ordinal exactly once by the transport. */
internal class PgMutationInput(
  val op: PgMutationOp,
  val fn: Long,
  val featureId: String,
  val version: Long,
  val tuple: Tuple? = null,
  val expected: TupleNumber? = null,
  val emptyTombstone: Boolean = false
) {
  companion object {
    fun fromTuple(op: PgMutationOp, value: NewTuple): PgMutationInput = PgMutationInput(
      op, value.tuple.tupleNumber.featureNumber, value.tuple.id.text, value.tuple.version, value.tuple, value.previousTupleNumber)
  }
}

internal class PgMutationResult(val state: Tuple?, val previous: TupleNumber?, val changed: Boolean)

/**
 * Set-based HEAD mutations. SQL captures predecessors with ordered row locks, applies only the valid input prefix, and archives changed predecessors through RETURNING dependencies. Invisible insert winners or lost physical rows retry an internal subtransaction, never caller work. Semantic errors leave the valid prefix pending for caller rollback. No per-feature advisory locks, DDL, version allocation or transaction commit occurs here.
 * @since 3.0
 */
internal class PgBatchMutation(
  private val databaseId: Id,
  private val schema: Id,
  private val table: Id,
  private val columns: Array<PgColumn>,
  private val history: Boolean,
  private val collection: PgCollection? = null
) {
  private val databaseNumber = databaseId.number
  private val head = Version.HEAD.number
  private val root = "${schema.schemaName().quotedName}.${table.tableName().quotedName}"
  private val headTable = "${schema.schemaName().quotedName}.${quoteIdent(table.tableName().headName)}"
  private val rows = PgRows().addColumns(columns)
    .withDatabaseNumber(databaseNumber).withCatalogNumber(schema.intValue).withCollectionNumber(table.intValue)
  private val names = columns.joinToString(", ") { it.ident }
  private val featureOrdinalParameter = "${'$'}${(rows.getColumn("fn") ?: throw illegalState("Missing feature ordinal column")).index + 1}"
  private val cc = columns.firstOrNull { it.name == StandardMembers.ChangeCount.name }?.ident
  private val used = columns.map { it.columnName }.toMutableSet()
  private fun field(name: String): String {
    var candidate = "__batch_$name"
    var suffix = 0
    while (!used.add(candidate)) candidate = "__batch_${name}_${++suffix}"
    return candidate
  }
  private val ordinal = field("order")
  private val mode = field("mode")
  private val expected = field("expected")
  private val empty = field("empty")
  private val actual = field("actual")
  private val writeVersion = field("version")
  private val error = field("error")
  private val boundary = field("stop")
  private val applied = field("applied")
  private val rawFn = field("fn")
  private val returnedVersion = field("returned_version")
  private val returnedCc = field("cc")
  private val needsChange = field("change")
  private fun q(name: String): String = quoteIdent(name)

  fun execute(conn: PgConnection, inputs: List<PgMutationInput>): List<PgMutationResult> {
    if (conn.autoCommit) throw illegalState("Batch mutation requires a caller-owned transaction")
    validate(inputs)
    if (inputs.isEmpty()) return emptyList()
    rows.size = inputs.size
    val modes = arrayOfNulls<Int>(inputs.size)
    val expectations = arrayOfNulls<String>(inputs.size)
    val emptyFlags = arrayOfNulls<Boolean>(inputs.size)
    for (i in inputs.indices) {
      val input = inputs[i]
      if (input.tuple != null) rows.setRow(i, input.tuple)
      else {
        rows.setColumn("fn", i, input.fn)
        rows.setColumn("version", i, input.version)
        rows.setColumn("nv", i, head)
      }
      modes[i] = input.op.code
      expectations[i] = input.expected?.version?.toString()
      emptyFlags[i] = input.emptyTombstone
    }
    val types = arrayOf(*rows.typeNames(), "int4[]", "text[]", "bool[]")
    val arguments = arrayOf(*rows.values(), modes, expectations, emptyFlags)
    try {
      var optimistic = true
      for (input in inputs) if (input.op != PgMutationOp.CREATE && input.op != PgMutationOp.UPSERT) optimistic = false
      if (optimistic) {
        try {
          val result = optimisticInsert(conn, arguments, types, inputs)
          if (result != null) return result
        } catch (failure: Throwable) {
          // The fast attempt has already rolled back. Classify committed HEADs before surfacing candidate conversion/constraint errors, retaining duplicate-CREATE error precedence.
          val state = PgExceptionMapper.sqlState(failure)
          if (state == null || !(state.startsWith("22") || state.startsWith("23"))) throw failure
        }
      }
      val sql = sql()
      var retryIndex = 0
      var retryActual: Long? = null
      conn.prepare(sql, types).use { plan ->
        for (recheck in 0 until 4) {
          var attempt: Attempt? = null
          val completed = conn.tryBatch {
            attempt = read(plan, arguments, inputs)
            !attempt!!.retry
          }
          if (completed) {
            val outcome = attempt ?: throw illegalState("Batch attempt returned no result")
            outcome.failure?.let { throw it }
            val results = ArrayList<PgMutationResult>(inputs.size)
            for (result in outcome.results) results.add(result ?: PgMutationResult(null, null, false))
            return results
          }
          retryIndex = attempt?.retryIndex ?: 0
          retryActual = attempt?.retryActual
        }
      }
      throw failure(inputs[retryIndex], 8, retryActual)
    } catch (failure: Throwable) {
      val state = PgExceptionMapper.sqlState(failure)
      val mapped = PgExceptionMapper.map(failure)
      val e = if (state == "40P01" || state == "40001")
        NakshaException(NakshaError.CONFLICT, "Concurrent batch mutation requires retrying the complete transaction", mapped)
      else mapped
      if (e.error.database == null) e.error.database = databaseId.text
      if (e.error.catalog == null) e.error.catalog = schema.text
      if (e.error.collection == null) e.error.collection = table.text
      throw e
    }
  }

  /** All-returned CREATE candidates need no HEAD classification. Partial results roll back this attempt before the full CTE rechecks a fresh snapshot. */
  private fun optimisticInsert(conn: PgConnection, arguments: Array<Any?>, types: Array<String>, inputs: List<PgMutationInput>): List<PgMutationResult>? {
    val input = inputSql()
    val sql = "WITH input AS ($input) INSERT INTO $root ($names) SELECT $names FROM input ORDER BY fn " +
      "ON CONFLICT (fn, nv) DO NOTHING RETURNING fn::text AS ${q(rawFn)}, version::text AS ${q(returnedVersion)}"
    val byFn = HashMap<Long, Int>(inputs.size)
    val fnColumn = rows.getColumn("fn") ?: throw illegalState("Missing feature ordinal column")
    for (i in inputs.indices) byFn[fnColumn.values[i] as Long] = i
    val seen = BooleanArray(inputs.size)
    var count = 0
    conn.prepare(sql, types).use { plan ->
      val complete = conn.tryBatch {
        plan.executeMutation(arguments).use { cursor ->
          while (cursor.next()) {
            val fn = cursor.get<String>(rawFn).toLong()
            val i = byFn[fn] ?: throw illegalState("Unknown feature in INSERT RETURNING")
            if (seen[i] || cursor.get<String>(returnedVersion).toLong() != inputs[i].version) throw illegalState("Invalid INSERT RETURNING correlation")
            seen[i] = true
            count++
          }
        }
        count == inputs.size
      }
      if (!complete) return null
    }
    val results = ArrayList<PgMutationResult>(inputs.size)
    for (inputRow in inputs) results.add(PgMutationResult(inputRow.tuple, null, true))
    return results
  }

  private fun inputSql(): String {
    val n = columns.size
    val extras = "${q(mode)}, ${q(expected)}, ${q(empty)}, ${q(ordinal)}"
    return "SELECT ${rows.decodedColumns()}, $extras FROM UNNEST(${rows.placeholders()}, " +
      "${'$'}${n + 1}, ${'$'}${n + 2}, ${'$'}${n + 3}) WITH ORDINALITY AS i($names, $extras)"
  }

  private fun validate(inputs: List<PgMutationInput>) {
    val keys = HashSet<Long>(inputs.size)
    for (input in inputs) {
      if (!keys.add(input.fn)) throw illegalArg("Duplicate feature in a mutation batch")
      val previous = input.expected
      if (previous != null && (previous.databaseNumber != databaseNumber || previous.catalogNumber != schema.intValue ||
          previous.collectionNumber != table.intValue || previous.featureNumber != input.fn)) {
        throw invalid(input, "Predecessor does not belong to the target feature")
      }
      val tuple = input.tuple ?: continue
      val tn = tuple.tupleNumber
      val action = when (input.op) {
        PgMutationOp.CREATE, PgMutationOp.UPSERT -> Action.CREATE
        PgMutationOp.UPDATE -> Action.UPDATE
        PgMutationOp.EXPLICIT_DELETE -> Action.DELETE
        else -> throw invalid(input, "Unexpected explicit tuple in an ID mutation")
      }
      val reason = when {
        input.op == PgMutationOp.CREATE && previous != null -> "Row insertion requires a null predecessor"
        tn.databaseNumber != databaseNumber || tn.catalogNumber != schema.intValue || tn.collectionNumber != table.intValue || tn.featureNumber != input.fn -> "Tuple does not belong to the target collection"
        tn.action != action -> "Mutation requires $action action"
        tuple.nextVersion != head -> "Mutation requires a HEAD state"
        tuple.featureBytes.isEmpty() -> "Mutation requires a fully encoded tuple"
        tn.version < 0L || tn.version >= head -> "Mutation requires a finite version"
        else -> null
      }
      if (reason != null) throw invalid(input, reason)
    }
  }

  private fun invalid(input: PgMutationInput, message: String): NakshaException = NakshaException(NakshaError(
    NakshaError.ILLEGAL_ARGUMENT, message).apply {
    database = databaseId.text; catalog = schema.text; collection = table.text; id = input.featureId
  })

  private fun sql(): String {
    val order = q(ordinal); val op = q(mode); val exp = q(expected); val isEmpty = q(empty)
    val old = q(actual); val ver = q(writeVersion); val err = q(error); val stop = q(boundary); val change = q(needsChange)
    val oldVersion = "l.version"
    val live = "($oldVersion & 3) < 2"
    val successor = "i.version >= 0 AND i.version < $head AND (i.version & -4) > ($oldVersion & -4)"
    val countOverflow = if (cc == null) "FALSE" else "l.$cc >= 2147483647"
    val conflictCode = "CASE WHEN $op = 0 AND $live THEN 1 " +
      "WHEN $op = 2 AND ($oldVersion IS NULL OR NOT ($live)) THEN 2 " +
      "WHEN $op IN (3,5) AND $oldVersion IS NULL AND ($op = 5 OR i.$exp IS NOT NULL) THEN 2 " +
      "WHEN $op = 5 AND NOT ($live) AND i.$exp IS NULL THEN 6 " +
      "WHEN $op = 5 AND i.$exp IS NOT NULL AND i.$exp::bigint <> $oldVersion THEN 3 " +
      "WHEN $op IN (2,3) AND $live AND i.$exp IS NOT NULL AND (i.$exp::bigint & -4) <> ($oldVersion & -4) THEN 3 " +
      "WHEN $oldVersion IS NOT NULL AND ($op IN (0,1,2,5) OR $op = 3 AND $live OR $op = 4 AND NOT ($live)) AND NOT ($successor) THEN 4 " +
      "WHEN $op = 1 AND $live AND i.$exp IS NOT NULL AND (i.$exp::bigint & -4) <> ($oldVersion & -4) THEN 3 " +
      "WHEN $op IN (1,3) AND $live AND $countOverflow THEN 5 ELSE 0 END"
    val finalVersion = "CASE WHEN $op = 1 AND $live THEN (i.version & -4) | 1 WHEN $op = 3 THEN (i.version & -4) | 2 ELSE i.version END"
    val changeNeeded = "CASE WHEN $op IN (0,1) THEN TRUE WHEN $op IN (2,5) THEN $oldVersion IS NOT NULL " +
      "WHEN $op = 3 THEN $oldVersion IS NOT NULL AND $live ELSE $oldVersion IS NOT NULL AND NOT ($live) END"
    val replacement = columns.joinToString(", ") { column ->
      val name = column.ident
      when (column.columnName) {
        "version" -> "e.$ver"
        "nv" -> head.toString()
        "fn" -> "e.fn"
        else -> when {
          column.name == StandardMembers.ChangeCount.name -> "CASE WHEN e.$op = 3 THEN CASE WHEN e.$isEmpty THEN NULL WHEN l.$name IS NULL THEN NULL ELSE l.$name + 1 END " +
            "WHEN e.$op = 1 AND (e.$old & 3) < 2 THEN coalesce(l.$name, 0) + 1 ELSE e.$name END"
          else -> "CASE WHEN e.$op = 3 THEN CASE WHEN e.$isEmpty THEN NULL ELSE l.$name END ELSE e.$name END"
        }
      }
    }
    val archival = columns.joinToString(", ") { if (it.columnName == "nv") "e.$ver" else "l.${it.ident}" }
    val inserted = columns.joinToString(", ") { if (it.columnName == "version") "e.$ver" else "e.${it.ident}" }
    val outputColumns = columns.joinToString(", ") { column ->
      val expression = "CASE WHEN c.$op IN (3,4) THEN CASE WHEN u.fn IS NOT NULL THEN u.${column.ident} ELSE l.${column.ident} END ELSE NULL END"
      "${if (column.pgType == PgType.INT64) "($expression)::text" else expression} AS ${column.ident}"
    }
    val newCc = if (cc == null) "NULL::text" else "CASE WHEN c.$op = 1 AND (c.$old & 3) < 2 THEN u.$cc::text ELSE NULL::text END"
    return """
WITH input AS (
 ${inputSql()}
), locked AS MATERIALIZED (
 SELECT h.* FROM $headTable h WHERE h.fn = ANY($featureOrdinalParameter::bigint[])
 ORDER BY h.fn FOR UPDATE OF h
), classified AS MATERIALIZED (
 SELECT i.*, $oldVersion AS $old, $finalVersion AS $ver, $conflictCode AS $err, $changeNeeded AS $change
 FROM input i LEFT JOIN locked l ON l.fn = i.fn
), first_error AS (
 SELECT min($order) FILTER (WHERE $err <> 0) AS $stop FROM classified
), eligible AS (
 SELECT c.* FROM classified c CROSS JOIN first_error b
 WHERE c.$err = 0 AND (b.$stop IS NULL OR c.$order < b.$stop)
), changed AS (
 UPDATE $root h SET ($names) = ($replacement)
 FROM eligible e JOIN locked l ON l.fn = e.fn
 WHERE h.fn = e.fn AND h.nv = $head AND e.$change AND e.$op <> 4
 RETURNING h.*
), removed AS (
 DELETE FROM $root h USING eligible e
 WHERE h.fn = e.fn AND h.nv = $head AND e.$change AND e.$op = 4
 RETURNING h.fn
), archived AS (
 INSERT INTO $root ($names)
 SELECT $archival FROM eligible e JOIN locked l ON l.fn = e.fn
 JOIN (SELECT fn FROM changed UNION ALL SELECT fn FROM removed) affected ON affected.fn = e.fn
 WHERE ${if (history) "TRUE" else "FALSE"}
 RETURNING fn
), inserted AS (
 INSERT INTO $root ($names)
 SELECT $inserted FROM eligible e WHERE e.$old IS NULL AND e.$op IN (0,1)
 ORDER BY e.fn ON CONFLICT (fn, nv) DO NOTHING RETURNING fn, version
)
SELECT c.$order::int AS ${q(ordinal)}, c.fn::text AS ${q(rawFn)}, c.$old::text AS ${q(actual)},
 c.$err AS ${q(error)}, b.$stop::int AS ${q(boundary)}, c.$change AS ${q(needsChange)},
 (u.fn IS NOT NULL OR r.fn IS NOT NULL OR a.fn IS NOT NULL) AS ${q(applied)},
 coalesce(u.version, a.version, c.$old)::text AS ${q(returnedVersion)}, $newCc AS ${q(returnedCc)}, $outputColumns
FROM classified c CROSS JOIN first_error b
LEFT JOIN locked l ON l.fn = c.fn
LEFT JOIN changed u ON u.fn = c.fn LEFT JOIN removed r ON r.fn = c.fn LEFT JOIN inserted a ON a.fn = c.fn
ORDER BY c.$order
""".trimIndent()
  }

  private class Attempt(val results: Array<PgMutationResult?>, val failure: NakshaException?, val retry: Boolean,
    val retryIndex: Int, val retryActual: Long?)

  private fun read(plan: PgPlan, arguments: Array<Any?>, inputs: List<PgMutationInput>): Attempt {
    val results = arrayOfNulls<PgMutationResult>(inputs.size)
    val seen = BooleanArray(inputs.size)
    val output = collection?.let { PgRows().withCollection(it).apply { size = 1 } }
    var failure: NakshaException? = null
    var firstFailure = inputs.size
    var retry = false
    var retryIndex = inputs.size
    var retryActual: Long? = null
    plan.executeMutation(arguments).use { cursor ->
      while (cursor.next()) {
        val index = (cursor.column(ordinal) as Number).toInt() - 1
        if (index !in inputs.indices || seen[index]) throw illegalState("Invalid batch result correlation")
        seen[index] = true
        val input = inputs[index]
        if (cursor.get<String>(rawFn).toLong() != Id.featureNumberToOrdinal(input.fn)) throw illegalState("Batch result has a different feature identity")
        val old = (cursor.column(actual) as? String)?.toLong()
        val code = (cursor.column(error) as Number).toInt()
        val stop = (cursor.column(boundary) as? Number)?.toInt()?.minus(1) ?: inputs.size
        if (code != 0 && index < firstFailure) {
          firstFailure = index
          failure = this.failure(input, code, old)
        }
        if (index >= stop) continue
        val changed = cursor.column(applied) == true
        if (cursor.column(needsChange) == true && !changed) {
          retry = true
          if (index < retryIndex) { retryIndex = index; retryActual = old }
        }
        val previous = if (changed && old != null && input.op != PgMutationOp.PURGE &&
          !((input.op == PgMutationOp.CREATE || input.op == PgMutationOp.UPSERT) && Action.fromVersion(old) == Action.DELETE))
          TupleNumber(databaseNumber, schema.intValue, table.intValue, input.fn, old) else null
        val state: Tuple? = if (input.tuple != null) {
          val version = (cursor.column(returnedVersion) as? String)?.toLong() ?: input.version
          val newCc = (cursor.column(returnedCc) as? String)?.toInt()
          if (version == input.tuple.version && !(input.op == PgMutationOp.UPSERT && previous != null && cc != null)) input.tuple
          else {
            val book = MemberValuesBook.copyOf(input.tuple.membersBook)
            book.setNamedValue(StandardMembers.TN, TupleNumber.copy(input.tuple.tupleNumber, version = version))
            if (newCc != null) book.setNamedValue(StandardMembers.ChangeCount.name, newCc)
            Tuple(input.tuple.featureBytes, book)
          }
        } else if (old != null && output != null) {
          for (column in output.columns) {
            val value = cursor.column(column.sqlAlias)
            column.values[0] = if (column.pgColumn.pgType == PgType.INT64 && value is String) value.toLong() else value
          }
          output.getTuple(0, input.featureId) ?: throw illegalState("Failed to decode batch mutation state")
        } else null
        results[index] = PgMutationResult(state, previous, changed)
      }
    }
    if (seen.any { !it }) throw illegalState("Missing batch result correlation")
    return Attempt(results, failure, retry, if (retryIndex == inputs.size) 0 else retryIndex, retryActual)
  }

  private fun failure(input: PgMutationInput, code: Int, actual: Long?): NakshaException {
    val errorCode = when (code) {
      1 -> when {
        schema.intValue == Id.ADMIN_CATALOG_ID.intValue && table.intValue == Id.CATALOGS_COL_ID.intValue -> NakshaError.CATALOG_EXISTS
        table.intValue == Id.COLLECTIONS_COL_ID.intValue -> NakshaError.COLLECTION_EXISTS
        else -> NakshaError.FEATURE_EXISTS
      }
      2 -> NakshaError.FEATURE_NOT_FOUND
      5 -> NakshaError.ILLEGAL_STATE
      else -> NakshaError.CONFLICT
    }
    val message = when (code) {
      1 -> "Feature already has a HEAD state"
      2 -> if (input.op == PgMutationOp.UPDATE) "Feature '${input.featureId}' has no live HEAD state to update"
        else "Feature has no HEAD state to delete"
      3 -> when (input.op) {
        PgMutationOp.UPDATE -> "Atomic update failed, the HEAD version does not match the expected predecessor"
        PgMutationOp.UPSERT -> "Atomic upsert failed, the HEAD version does not match the expected predecessor"
        PgMutationOp.DELETE -> "Atomic delete failed, the HEAD version does not match the expected version"
        PgMutationOp.EXPLICIT_DELETE -> "The explicit delete state does not match the HEAD"
        else -> "Atomic mutation failed, HEAD does not match the expected predecessor"
      }
      4 -> "The successor transaction version must be newer than HEAD"
      5 -> "Change count overflows int32"
      6 -> "An explicit delete state requires a live HEAD or an exact predecessor"
      else -> "Concurrent HEAD changed during batch mutation; retry the transaction"
    }
    return NakshaException(NakshaError(errorCode, message).apply {
      database = databaseId.text; catalog = schema.text; collection = table.text; id = input.featureId
      actualVersion = actual; expectedVersion = input.expected?.version
    })
  }
}
