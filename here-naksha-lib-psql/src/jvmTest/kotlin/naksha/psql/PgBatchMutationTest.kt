package naksha.psql

import naksha.base.Action
import naksha.base.Id
import naksha.base.NakshaError
import naksha.base.NakshaException
import naksha.base.Version
import naksha.model.NewTuple
import naksha.model.TagList
import naksha.model.TagMap
import naksha.base.Platform
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.JsonNode
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.Callable
import naksha.model.Tuple
import naksha.model.objects.IndexList
import naksha.model.objects.Member
import naksha.model.objects.MemberType
import naksha.model.objects.NakshaCollection
import naksha.model.objects.NakshaFeature
import naksha.model.objects.StandardMembers
import naksha.model.objects.StoreMode
import naksha.model.request.Write
import naksha.model.request.WriteRequest
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable
import org.mockito.Mockito
import kotlin.test.*

/** Real SQL batching, correlation and mixed-operation regression coverage; use a fresh dedicated database. */
@EnabledIfEnvironmentVariable(named = "NAKSHA_TEST_BATCH_DB_URL", matches = ".+")
class PgBatchMutationTest : PgTestBase(collection = null) {
  init {
    assertEquals(System.getenv("NAKSHA_TEST_BATCH_DB_URL"), System.getenv("NAKSHA_TEST_PSQL_DB_URL"))
  }

  private fun setup(name: String, history: Boolean = true) {
    useCollection(NakshaCollection(name, catalog.id).withMembers(
      Member("name"), Member("tags", MemberType.TAG_LIST), Member("attributes", MemberType.TAG_MAP), StandardMembers.ChangeCount.copy(), Member("__batch_order")
    ).withIndices(IndexList()).withStoreHistory(if (history) StoreMode.ON else StoreMode.OFF))
  }

  private fun physical(conn: PgConnection): PgCollection = assertNotNull(
    storage.database.getPgCollection(Id(catalog.id).intValue, Id(collection.id).intValue, conn))

  private fun encode(session: PgSession, id: String, action: Action = Action.CREATE): Tuple =
    Tuple.encodeFeature(NakshaFeature(id).apply { properties.setRaw("name", "new-$id") }, collection, session, action = action).tuple

  @Test
  fun multirowCreateUsesOneSqlExecutionAndMapsShuffledServerRows() {
    setup("batch_shuffled")
    storage.newWriteSession(newSessionOptions()).use { raw ->
      val session = raw as PgSession
      session.useTransaction()
      session.useConnNonNull(false) { conn ->
        val inputs = ArrayList<NewTuple>()
        for (i in 0 until 128) inputs.add(NewTuple(encode(session, if (i % 2 == 0) "${1000 + i}" else "hashed_$i")))
        var executions = 0
        val observed = Mockito.spy(conn)
        Mockito.doAnswer { call ->
          val sql = call.getArgument<String>(0)
          assertTrue(sql.contains("WITH input AS"))
          assertFalse(sql.contains("FOR UPDATE"), "All-new inserts do not need a preliminary HEAD lookup")
          assertTrue(sql.contains(physical(conn).qualifiedName))
          assertFalse(sql.contains("pg_advisory"))
          // Reverse the final output, not input or lock order. Correlation must not depend on SQL row order.
          val plan = conn.prepare("WITH shuffled AS ($sql) SELECT * FROM shuffled ORDER BY 1 DESC", call.getArgument(1))
          val counted = Mockito.spy(plan)
          Mockito.doAnswer { invocation ->
            executions++
            plan.execute(invocation.getArgument(0))
          }.`when`(counted).execute(Mockito.any())
          counted
        }.`when`(observed).prepare(Mockito.anyString(), Mockito.nullable(Array<String>::class.java))
        val p = physical(conn)
        val results = storage.database.rows_insert(observed, p.catalogId, p.id, inputs, p.storeHistory, p.columns)
        assertEquals(1, executions, "128 rows must be persisted by one bulk SQL execution")
        assertEquals(inputs.size, results.size)
        for (i in inputs.indices) assertSame(inputs[i].tuple, results[i])
        assertEquals(128L, count(conn, p.qualifiedName))
      }
    }
  }

  @Test
  fun mixedOperationsArchiveExactlyAndKeepNoOps() {
    for (history in listOf(false, true)) {
      setup("batch_mixed_$history", history)
      val seeds = insertFeatures(listOf("upsert_live", "42", "delete_live", "purge_tomb", "recreate", "keep_live", "already_deleted")
        .map { NakshaFeature(it).apply { properties.setRaw("name", "old-$it") } })
      val old = HashMap<String, Tuple>()
      for (feature in seeds.featureTupleList) {
        val tuple = assertNotNull(feature?.tuple)
        old[tuple.id.text] = tuple
      }
      val deletion = WriteRequest()
      for (id in listOf("purge_tomb", "recreate", "already_deleted")) deletion.add(Write().deleteFeatureById(collection, id))
      for (feature in executeWrite(deletion).featureTupleList) {
        val tuple = assertNotNull(feature?.tuple)
        old[tuple.id.text] = tuple
      }
      storage.newWriteSession(newSessionOptions()).use { raw ->
        val session = raw as PgSession
        val version = session.useTransaction().version.number
        session.useConnNonNull(false) { conn ->
          val p = physical(conn)
          val create = encode(session, "fresh")
          val upsert = encode(session, "upsert_live")
          val update = encode(session, "42", Action.UPDATE)
          val recreate = encode(session, "recreate")
          val inputs = listOf(
            PgMutationInput.fromTuple(PgMutationOp.CREATE, NewTuple(create)),
            PgMutationInput.fromTuple(PgMutationOp.UPSERT, NewTuple(upsert)),
            PgMutationInput.fromTuple(PgMutationOp.UPDATE, NewTuple(update, old["42"]!!.tupleNumber)),
            PgMutationInput(PgMutationOp.DELETE, Id("delete_live").number, "delete_live", version, expected = old["delete_live"]!!.tupleNumber),
            PgMutationInput(PgMutationOp.PURGE, Id("purge_tomb").number, "purge_tomb", version),
            PgMutationInput.fromTuple(PgMutationOp.UPSERT, NewTuple(recreate)),
            PgMutationInput(PgMutationOp.DELETE, Id("already_deleted").number, "already_deleted", version),
            PgMutationInput(PgMutationOp.DELETE, Id("missing").number, "missing", version),
            PgMutationInput(PgMutationOp.PURGE, Id("keep_live").number, "keep_live", version)
          )
          val observed = Mockito.spy(conn)
          Mockito.doAnswer { call ->
            val sql = call.getArgument<String>(0)
            assertTrue(sql.contains("FOR UPDATE OF h"))
            conn.prepare(sql + " DESC", call.getArgument(1))
          }.`when`(observed).prepare(Mockito.anyString(), Mockito.nullable(Array<String>::class.java))
          val results = PgBatchMutation(storage.database.id, p.catalogId, p.id, p.columns, history, p).execute(observed, inputs)
          assertEquals(listOf(true, true, true, true, true, true, false, false, false), results.map { it.changed })
          assertEquals(listOf(Action.CREATE, Action.UPDATE, Action.UPDATE, Action.DELETE, Action.DELETE, Action.CREATE,
            Action.DELETE, null, Action.CREATE), results.map { it.state?.action })
          assertEquals(old["upsert_live"]!!.tupleNumber, results[1].previous)
          assertEquals(old["42"]!!.tupleNumber, results[2].previous)
          assertEquals(old["delete_live"]!!.tupleNumber, results[3].previous)
          assertNull(results[5].previous)
          assertContentEquals(old["already_deleted"]!!.featureBytes, results[6].state!!.featureBytes)
          assertEquals(7L, count(conn, p.qualifiedName, "nv = ${Version.HEAD.number}"))
          assertEquals(if (history) 8L else 0L, count(conn, p.qualifiedName, "nv <> ${Version.HEAD.number}"))
          if (history) {
            for (i in listOf(1, 2, 3, 4, 5)) {
              val prior = assertNotNull(old[inputs[i].featureId])
              val successor = if (i == 4) version else assertNotNull(results[i].state).version
              val rows = PgRows().withCollection(p)
              conn.execute("SELECT * FROM ${p.qualifiedName} WHERE fn = $1::bigint AND version = $2::bigint AND nv = $3::bigint",
                arrayOf(Id.featureNumberToOrdinal(prior.tupleNumber.featureNumber).toString(), prior.version.toString(), successor.toString()))
                .fetch().use { rows.readAll(it) }
              val archived = assertNotNull(rows.getTuple(0, prior.id.text))
              assertContentEquals(prior.featureBytes, archived.featureBytes)
              assertEquals(prior.tupleNumber, archived.tupleNumber)
            }
          }
        }
      }
    }
  }

  @Test
  fun semanticFailureKeepsOnlyTheSuccessfulInputPrefixPending() {
    setup("batch_prefix")
    val existing = insertFeatures(NakshaFeature("existing")).featureTupleList.single()!!.tuple!!
    storage.newWriteSession(newSessionOptions()).use { raw ->
      val session = raw as PgSession
      session.useTransaction()
      session.useConnNonNull(false) { conn ->
        val p = physical(conn)
        val inputs = listOf(NewTuple(encode(session, "before")), NewTuple(encode(session, "existing")), NewTuple(encode(session, "after")))
        val error = assertFailsWith<NakshaException> {
          storage.database.rows_insert(conn, p.catalogId, p.id, inputs, p.storeHistory, p.columns)
        }.error
        assertEquals(NakshaError.FEATURE_EXISTS, error.code)
        assertEquals(existing.version, error.actualVersion)
        assertEquals(1L, count(conn, p.qualifiedName, "fn = ${Id.featureNumberToOrdinal(Id("before").number)}"))
        assertEquals(0L, count(conn, p.qualifiedName, "fn = ${Id.featureNumberToOrdinal(Id("after").number)}"))
        assertEquals(2L, count(conn, p.qualifiedName))
      }
    }
  }

  @Test
  fun duplicateCreateRetainsErrorPriorityOverCandidateConstraints() {
    setup("batch_constraint_priority")
    val existing = insertFeatures(NakshaFeature("existing").apply { properties.setRaw("name", "valid") })
      .featureTupleList.single()!!.tuple!!
    storage.newWriteSession(newSessionOptions()).use { raw ->
      val session = raw as PgSession
      session.useTransaction()
      session.useConnNonNull(false) { conn ->
        val p = physical(conn)
        val name = p.column("name")!!.ident
        conn.execute("ALTER TABLE ${p.qualifiedName} ADD CHECK ($name <> 'invalid')").close()
        val candidate = Tuple.encodeFeature(NakshaFeature("existing").apply { properties.setRaw("name", "invalid") }, collection, session, action = Action.CREATE).tuple
        val error = assertFailsWith<NakshaException> {
          storage.database.rows_insert(conn, p.catalogId, p.id, listOf(NewTuple(candidate)), p.storeHistory, p.columns)
        }.error
        assertEquals(NakshaError.FEATURE_EXISTS, error.code)
        assertEquals(existing.version, error.actualVersion)
        assertEquals(1L, count(conn, p.qualifiedName))
        assertEquals(1L, conn.execute("SELECT 1::text AS value").fetch().use { it.get<String>("value").toLong() })
      }
    }
  }

  private fun count(conn: PgConnection, relation: String, predicate: String = "TRUE"): Long =
    conn.execute("SELECT count(*)::text AS count FROM $relation WHERE $predicate").fetch().use { it.get<String>("count").toLong() }

  @Test
  @DisabledIfEnvironmentVariable(named = "NAKSHA_SUPPRESS_PERF_TEST", matches = "true")
  fun bulkSqlBenchmarkExcludesEncodingCoverageAndCommit() {
    setup("batch_sql_benchmark")
    storage.newWriteSession(newSessionOptions()).use { raw ->
      val session = raw as PgSession
      val version = session.useTransaction().version.number and -4L
      session.useConnNonNull(false) { conn ->
        val p = physical(conn)
        for (size in listOf(1, 100, 1000)) {
          val samples = ArrayList<Double>()
          for (sample in 0 until 23) {
            val inputs = ArrayList<NewTuple>(size)
            for (i in 0 until size) inputs.add(Tuple.encodeRaw(NakshaFeature("sql_${size}_${sample}_$i"),
              storage.database.id.number, p.catalogId.intValue, p.id.intValue, p.naksha.validatedMembers, version, requestedAction = Action.CREATE))
            val start = System.nanoTime()
            val result = storage.database.rows_insert(conn, p.catalogId, p.id, inputs, true, p.columns)
            val millis = (System.nanoTime() - start) / 1e6
            assertEquals(size, result.size)
            if (sample >= 8) samples.add(millis)
          }
          samples.sort()
          println("BATCH_SQL_BENCH rows=$size samples=${samples.size} median_ms=${samples[samples.size / 2]} p95_ms=${samples.last()} encoding=false coverage=false commit=false")
        }
      }
    }
  }

  @Test
  fun generatedMutationSqlExecutesThroughRealPlv8WithExactBinaryAndBigintValues() {
    setup("batch_real_plv8")
    storage.newWriteSession(newSessionOptions()).use { raw ->
      val session = raw as PgSession
      val version = session.useTransaction().version.number and -4L
      session.useConnNonNull(false) { conn ->
        val p = physical(conn)
        conn.execute("CREATE TEMP TABLE batch_spi_bootstrap (value integer) ON COMMIT DROP").close()
        val observed = Mockito.spy(conn)
        var nativeCall = 0
        Mockito.doAnswer { call ->
          val sql = call.getArgument<String>(0)
          val types = call.getArgument<Array<String>>(1)
          val plan = Mockito.mock(PgPlan::class.java)
          Mockito.doAnswer { execution ->
            val arguments = execution.getArgument<Array<Any?>>(0)
            val script = """
              const p = plv8.prepare(${Platform.toJSON(sql)}, ${Platform.toJSON(types)});
              try {
                return JSON.parse(JSON.stringify(p.execute(${javascriptValue(arguments)}), function(k,v) {
                  if (typeof v === 'bigint') return v.toString();
                  if (v instanceof ArrayBuffer) return Array.from(new Uint8Array(v));
                  if (v && ArrayBuffer.isView(v)) return Array.from(new Uint8Array(v.buffer,v.byteOffset,v.byteLength));
                  return v;
                }));
              } finally { p.free(); }
            """.trimIndent()
            val function = "pg_temp.batch_spi_execute_${nativeCall++}"
            conn.execute("CREATE FUNCTION $function() RETURNS jsonb LANGUAGE plv8 AS ${PgUtil.quoteLiteral(script)}").close()
            val json = conn.execute("SELECT $function()::text AS value").fetch().use { it.get<String>("value") }
            nativeResultCursor(ObjectMapper().readTree(json), p)
          }.`when`(plan).executeMutation(Mockito.any())
          plan
        }.`when`(observed).prepare(Mockito.anyString(), Mockito.nullable(Array<String>::class.java))
        val ids = listOf("0", Long.MAX_VALUE.toString(), "native-hashed")
        val first = ArrayList<NewTuple>()
        for (id in ids) {
          first.add(Tuple.encodeRaw(NakshaFeature(id).apply {
            properties.setRaw("name", "native-$id")
            properties.setRaw("tags", TagList("one", "two"))
            properties.setRaw("attributes", TagMap("source", "native"))
          }, storage.database.id.number, p.catalogId.intValue, p.id.intValue, p.naksha.validatedMembers, version, requestedAction = Action.CREATE))
        }
        val created = storage.database.rows_insert(observed, p.catalogId, p.id, first, true, p.columns)
        for (i in first.indices) {
          assertSame(first[i].tuple, created[i])
          assertNativeStoredState(conn, p, created[i])
        }
        val next = ArrayList<NewTuple>()
        for (id in ids) next.add(Tuple.encodeRaw(NakshaFeature(id).apply { properties.setRaw("name", "updated-$id") },
          storage.database.id.number, p.catalogId.intValue, p.id.intValue, p.naksha.validatedMembers, version + 4, requestedAction = Action.CREATE))
        val updated = storage.database.rows_upsert(observed, p.catalogId, p.id, next, true, p.columns)
        for (i in updated.indices) {
          assertEquals(first[i].tuple.tupleNumber, updated[i].previousTupleNumber)
          assertEquals(Action.UPDATE, updated[i].tuple.action)
          assertNativeStoredState(conn, p, updated[i].tuple)
          assertNativeStoredState(conn, p, first[i].tuple, updated[i].tuple.version)
        }
        val deleted = storage.database.rows_delete_by_id_with_result(observed, p, version + 8, listOf(Id(ids.last()))).single()
        assertEquals(Action.DELETE, deleted.tuple.action)
        assertEquals(updated.last().tuple.tupleNumber, deleted.previousTupleNumber)
        val purged = storage.database.rows_purge(observed, p, version + 12, listOf(Id(ids.last()))).single()
        assertEquals(deleted.tuple.tupleNumber, purged?.tupleNumber)
        assertEquals(2L, count(conn, p.qualifiedName, "nv = ${Version.HEAD.number}"))
        assertEquals(5L, count(conn, p.qualifiedName, "nv <> ${Version.HEAD.number}"))
      }
    }
  }

  private fun assertNativeStoredState(conn: PgConnection, collection: PgCollection, expected: Tuple, nextVersion: Long = Version.HEAD.number) {
    val rows = PgRows().withCollection(collection)
    conn.execute("SELECT * FROM ${collection.qualifiedName} WHERE fn = $1::bigint AND version = $2::bigint AND nv = $3::bigint",
      arrayOf(Id.featureNumberToOrdinal(expected.tupleNumber.featureNumber).toString(), expected.version.toString(), nextVersion.toString()))
      .fetch().use { rows.readAll(it) }
    val actual = assertNotNull(rows.getTuple(0, expected.id.text))
    assertEquals(expected.tupleNumber, actual.tupleNumber)
    assertContentEquals(expected.featureBytes, actual.featureBytes)
    for (name in listOf("name", "tags", "attributes")) {
      assertEquals(Platform.toJSON(expected.membersBook[name]), Platform.toJSON(actual.membersBook[name]), name)
    }
  }

  private fun javascriptValue(value: Any?): String = when (value) {
    null -> "null"
    is ByteArray -> "new Uint8Array([${value.joinToString(",") { (it.toInt() and 255).toString() }}])"
    is Array<*> -> value.joinToString(",", "[", "]") { javascriptValue(it) }
    is Long -> "BigInt('${value}')"
    is Number, is Boolean -> value.toString()
    is String -> Platform.toJSON(value)
    else -> error("Unsupported native test parameter ${value::class}")
  }

  private fun nativeResultCursor(rows: JsonNode, collection: PgCollection): PgCursor {
    var index = -1
    val types = collection.columns.associateBy { it.columnName }
    return Mockito.mock(PgCursor::class.java) { call ->
      when (call.method.name) {
        "next" -> ++index < rows.size()
        "isRow" -> index in 0 until rows.size()
        "get", "column" -> {
          val name = call.getArgument<String>(0)
          val value = rows[index][name]
          when {
            value == null || value.isNull -> null
            types[name]?.pgType == PgType.BYTE_ARRAY -> ByteArray(value.size()) { value[it].asInt().toByte() }
            value.isTextual -> value.asText()
            value.isBoolean -> value.asBoolean()
            value.isNumber -> value.numberValue()
            value.isArray -> Array(value.size()) { value[it].asText() }
            else -> Platform.fromJSON(value.toString())
          }
        }
        else -> Mockito.RETURNS_DEFAULTS.answer(call)
      }
    }
  }

  @Test
  fun deadlockSignalsWholeTransactionRetryWithoutDiscardingEarlierCallerWork() {
    setup("batch_deadlock_retry")
    val seeds = insertFeatures(NakshaFeature("lock_a"), NakshaFeature("lock_b"))
    val version = seeds.featureTupleList.first()!!.tupleNumber.version and -4L
    val executor = Executors.newFixedThreadPool(2)
    storage.newConnection(newSessionOptions(), false).use { first ->
      storage.newConnection(newSessionOptions(), false).use { second ->
        val p = physical(first)
        val connections = listOf(first, second)
        val ids = listOf("lock_a", "lock_b")
        for (i in connections.indices) {
          val conn = connections[i]
          conn.execute("SET LOCAL lock_timeout = '15s'; SET LOCAL statement_timeout = '30s'").close()
          conn.execute("CREATE TEMP TABLE held_before_batch (value integer); INSERT INTO held_before_batch VALUES (1)").close()
          conn.execute("SELECT fn FROM ${p.qualifiedName} WHERE fn = $1::bigint AND nv = ${Version.HEAD.number} FOR UPDATE",
            arrayOf(Id.featureNumberToOrdinal(Id(ids[i]).number).toString())).close()
        }
        try {
          val tasks = connections.indices.map { i -> executor.submit(Callable {
            val conn = connections[i]
            val target = ids[1 - i]
            val tuple = Tuple.encodeRaw(NakshaFeature(target), storage.database.id.number, p.catalogId.intValue,
              p.id.intValue, p.naksha.validatedMembers, version + 8, requestedAction = Action.UPDATE)
            try {
              storage.database.rows_update(conn, p.catalogId, p.id, listOf(tuple), true, p.columns)
              null
            } catch (failure: NakshaException) {
              assertEquals("40P01", PgExceptionMapper.sqlState(failure))
              assertEquals(NakshaError.CONFLICT, failure.error.code)
              assertTrue(failure.message.orEmpty().contains("complete transaction"))
              assertEquals(1L, count(conn, "held_before_batch"), "The failed attempt must not roll back earlier caller SQL")
              failure
            } finally { conn.rollback() }
          }) }
          assertEquals(1, tasks.map { it.get(30, TimeUnit.SECONDS) }.count { it != null })
        } finally {
          first.rollback(); second.rollback()
          executor.shutdownNow()
          assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS))
        }
      }
    }
  }
}
