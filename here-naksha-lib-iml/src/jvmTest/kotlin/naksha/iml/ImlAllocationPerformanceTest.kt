@file:Suppress("OPT_IN_USAGE", "DEPRECATION")
@file:OptIn(naksha.model.v30_experimental::class)

package naksha.iml

import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Statement
import naksha.base.TupleNumber
import naksha.base.NakshaError
import naksha.base.NakshaException
import naksha.model.NakshaContext
import naksha.model.Naksha
import naksha.model.objects.IndexList
import naksha.model.objects.NakshaCollection
import naksha.model.objects.NakshaFeature
import naksha.model.objects.StandardIndices
import naksha.model.objects.XyzIndices
import naksha.model.objects.XyzMembers
import naksha.model.request.ReadFeatures
import naksha.model.request.ops.And
import naksha.model.request.ops.Equals
import naksha.model.request.ops.Gte
import naksha.model.request.ops.TagListContains
import naksha.model.request.SuccessResponse
import naksha.model.request.Write
import naksha.model.request.WriteRequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ImlAllocationPerformanceTest {
  @Test
  fun `allocation strategies probe once per physical table and checkpoint safely`() {
    val statements = ArrayList<String>()
    val connection = proxy(Connection::class.java) { method, args ->
      when (method) {
        "prepareStatement" -> {
          statements.add(args[0] as String)
          proxy(PreparedStatement::class.java) { name, _ ->
            if (name == "executeQuery") rows("NO", null) else null
          }
        }
        "createStatement" -> proxy(Statement::class.java) { name, args ->
          if (name == "executeQuery") { statements.add(args[0] as String); rows(40L) } else null
        }
        else -> null
      }
    }
    val collection = NakshaCollection("one", "schema")
    val tuple = ImlTuple.encode(collection, TupleNumber(1, 1, 1, 1, 4))
    val layer = ImlMetadata.Layer("one", "schema", "table", collection, tuple)
    val alias = ImlMetadata.Layer("alias", "schema", "table", collection, tuple)
    val allocation = ImlAllocation()
    assertEquals(41L, allocation.next(connection, layer))
    val checkpoint = allocation.checkpoint()
    for (i in 0 until 100) assertEquals(42L + i, allocation.next(connection, alias))
    assertEquals(2, statements.size)
    allocation.clear()
    allocation.restore(checkpoint)
    assertEquals(42L, allocation.next(connection, layer))
    assertEquals(2, statements.size)
    allocation.clear()
    assertEquals(41L, allocation.next(connection, layer))
    assertEquals(4, statements.size)
    val other = ImlMetadata.Layer("one", "other-schema", "table", collection, tuple)
    assertEquals(41L, allocation.next(connection, other))
    assertEquals(6, statements.size)
  }

  @Test
  fun `unresolvable nextval expression rejects allocation without falling back to owned sequence`() {
    val connection = proxy(Connection::class.java) { method, args ->
      when (method) {
        "prepareStatement" -> {
          val sql = args[0] as String
          proxy(PreparedStatement::class.java) { name, _ ->
            if (name != "executeQuery") null else if (sql.contains("information_schema")) {
              rows("NO", "nextval(('schema.'::text || 'dynamic'::text)::regclass)")
            } else if (sql.contains("pg_get_serial_sequence")) rows("schema.unrelated_owned_seq") else throw AssertionError(sql)
          }
        }
        "createStatement" -> proxy(Statement::class.java) { name, _ -> if (name == "executeQuery") rows(20L) else null }
        else -> null
      }
    }
    val collection = NakshaCollection("one", "schema")
    val layer = ImlMetadata.Layer("one", "schema", "table", collection,
      ImlTuple.encode(collection, TupleNumber(1, 1, 1, 1, 4)))
    val error = assertThrows(NakshaException::class.java) { ImlAllocation().next(connection, layer) }
    assertEquals(NakshaError.CONFLICT, error.error.code)
  }

  @Test
  fun `identity and nextval defaults allocate on server but constants do not`() {
    for ((identity, default, server) in arrayOf(
      Triple("YES", null, true), Triple("NO", "nextval('row_seq'::regclass)", true),
      Triple("NO", "7", false), Triple("NO", "custom_allocator()", false))) {
      var probes = 0
      var maxima = 0
      val connection = proxy(Connection::class.java) { method, args ->
        when (method) {
          "prepareStatement" -> {
            val sql = args[0] as String
            proxy(PreparedStatement::class.java) { name, _ ->
              if (name != "executeQuery") null else when {
                sql.contains("information_schema") -> { probes++; rows(identity, default) }
                sql.contains("pg_get_serial_sequence") -> rows("schema.row_seq")
                sql.contains("pg_sequence") -> rows("schema", "row_seq", 1L, false)
                else -> throw AssertionError(sql)
              }
            }
          }
          "createStatement" -> proxy(Statement::class.java) { name, parameters ->
            if (name != "executeQuery") null else if ((parameters[0] as String).contains("max(i)")) {
              maxima++; rows(5L)
            } else rows(100L, true)
          }
          else -> null
        }
      }
      val collection = NakshaCollection("one", "schema")
      val layer = ImlMetadata.Layer("one", "schema", "table", collection,
        ImlTuple.encode(collection, TupleNumber(1, 1, 1, 1, 4)))
      val allocation = ImlAllocation()
      for (i in 0 until 100) {
        val number = allocation.next(connection, layer)
        if (server) assertNull(number) else assertEquals(6L + i, number)
      }
      assertEquals(1, probes)
      assertEquals(1, maxima)
    }
  }

  @Test
  fun `legacy batch and separate requests have one probe and one max read and coalesce without allocation`() = withWriter { fixture, writer ->
    val counts = countSql(writer)
    val collection = NakshaCollection(fixture.layer, fixture.schema)
    val request = WriteRequest().apply {
      returnResults = true
      for (i in 0 until 100) add(Write().createFeature(collection, NakshaFeature("batch-$i")))
    }
    val result = writer.executeWrite(request) as SuccessResponse
    for (i in 0 until 100) assertEquals(17L + i, result.featureTupleList[i]?.tupleNumber?.featureNumber)
    assertEquals(1, counts.probes)
    assertEquals(1, counts.maxima)
    assertEquals(100, counts.inserts)
    println("allocation legacy batch: rows=100 metadata=1 max=1 inserts=100; before-static metadata=100 max=100")
    val first = checkNotNull(result.featureTupleList[0]).tupleNumber
    val coalesced = write(writer, Write().upsertFeature(collection, NakshaFeature("batch-0")))
    assertEquals(first, coalesced.featureTupleList[0]?.tupleNumber)
    assertEquals(1, counts.probes)
    assertEquals(1, counts.maxima)
    assertEquals(100, counts.inserts)
    println("allocation coalesced update: rows=0 metadata=0 max=0 inserts=0 (delta)")
    for (i in 0 until 10) write(writer, Write().createFeature(collection, NakshaFeature("request-$i")))
    assertEquals(1, counts.probes)
    assertEquals(1, counts.maxima)
    assertEquals(110, counts.inserts)
    println("allocation legacy batch plus requests: rows=110 metadata=${counts.probes} max=${counts.maxima} inserts=${counts.inserts}; before-static metadata=110 max=110")
    writer.commit()
    val nextCounts = countSql(writer)
    assertEquals(127L, write(writer, Write().createFeature(collection, NakshaFeature("next-tx"))).featureTupleList[0]?.tupleNumber?.featureNumber)
    assertEquals(1, nextCounts.probes)
    assertEquals(1, nextCounts.maxima)
    assertEquals(1, nextCounts.inserts)
    println("allocation after commit: rows=1 metadata=${nextCounts.probes} max=${nextCounts.maxima} inserts=${nextCounts.inserts}")
    writer.rollback()
    val rollbackCounts = countSql(writer)
    write(writer, Write().createFeature(collection, NakshaFeature("after-rollback")))
    assertEquals(1, rollbackCounts.probes)
    assertEquals(1, rollbackCounts.maxima)
    assertEquals(1, rollbackCounts.inserts)
    println("allocation after rollback: rows=1 metadata=${rollbackCounts.probes} max=${rollbackCounts.maxima} inserts=${rollbackCounts.inserts}")
  }

  @Test
  fun `sequence default survives rollback and reconciles max once`() = ImlTestFixture.withFixture { fixture ->
    fixture.config.writeEnabled = true
    val sequence = "${ImlMetadata.quote(fixture.schema)}.row_seq"
    fixture.connection.createStatement().use {
      it.execute("CREATE SEQUENCE $sequence START WITH 1000")
      it.execute("ALTER TABLE ${fixture.sqlTable} ALTER COLUMN i SET DEFAULT nextval('$sequence'::regclass)")
    }
    (fixture.storage.newWriteSession() as ImlWriteSession).use { writer ->
      val collection = NakshaCollection(fixture.layer, fixture.schema)
      val counts = countSql(writer)
      for (i in 0 until 20) assertEquals(1000L + i,
        write(writer, Write().createFeature(collection, NakshaFeature("sequence-$i"))).featureTupleList[0]?.tupleNumber?.featureNumber)
      assertEquals(1, counts.probes)
      assertEquals(1, counts.maxima)
      assertEquals(20, counts.inserts)
      assertEquals(1, counts.reconciliations)
      assertEquals(0, counts.advances)
      println("allocation sequence default: rows=20 metadata=${counts.probes} max=${counts.maxima} sequence-state=${counts.reconciliations} setval=${counts.advances} inserts=${counts.inserts}; before-static metadata=20 max=20")
      writer.rollback()
      assertEquals(1020L, write(writer, Write().createFeature(collection, NakshaFeature("after-rollback"))).featureTupleList[0]?.tupleNumber?.featureNumber)
    }
  }

  @Test
  fun `identity inserts probe once and failed administrative requests restore allocation strategies`() = withWriter { fixture, writer ->
    val collection = NakshaCollection("identity", fixture.schema).apply { indices = IndexList() }
    write(writer, Write().createCollection(collection))
    val counts = countSql(writer)
    for (i in 0 until 20) assertEquals(1L + i,
      write(writer, Write().createFeature(collection, NakshaFeature("identity-$i"))).featureTupleList[0]?.tupleNumber?.featureNumber)
    assertEquals(1, counts.probes)
    assertEquals(1, counts.maxima)
    assertEquals(20, counts.inserts)
    assertEquals(1, counts.reconciliations)
    assertEquals(0, counts.advances)
    println("allocation identity: rows=20 metadata=${counts.probes} max=${counts.maxima} sequence-state=${counts.reconciliations} setval=${counts.advances} inserts=${counts.inserts}; before-static metadata=20 max=0")
    assertThrows(Exception::class.java) {
      writer.executeWrite(WriteRequest().add(Write().deleteCollectionById(fixture.schema, collection.id))
        .add(Write().createCollection(collection)).add(Write().createFeature(collection, NakshaFeature("temporary")))
        .add(Write().createCollection(collection)))
    }
    assertEquals(21L, write(writer, Write().createFeature(collection, NakshaFeature("restored"))).featureTupleList[0]?.tupleNumber?.featureNumber)
  }

  @Test
  fun `unowned stale quoted sequence defaults advance beyond explicit primary keys across rollback and storage identities`() = ImlTestFixture.withFixture { fixture ->
    fixture.config.writeEnabled = true
    val collection = NakshaCollection(fixture.layer, fixture.schema)
    val sequence = "${ImlMetadata.quote(fixture.schema)}.${ImlMetadata.quote("row'\";--seq")}"
    fixture.connection.createStatement().use {
      it.execute("CREATE SEQUENCE $sequence START WITH 1")
      it.execute("ALTER TABLE ${fixture.sqlTable} ADD PRIMARY KEY (i)")
      it.execute("ALTER TABLE ${fixture.sqlTable} ALTER COLUMN i SET DEFAULT nextval('${sequence.replace("'", "''")}'::regclass)")
      it.executeUpdate("INSERT INTO ${fixture.sqlTable} (id, version, operation, i, jsondata) " +
        "SELECT 'explicit-' || n, 1, 'I', n, '{}'::jsonb FROM generate_series(1,20) n " +
        "WHERE NOT EXISTS (SELECT 1 FROM ${fixture.sqlTable} existing WHERE existing.i = n)")
      it.executeQuery("SELECT pg_get_serial_sequence('${fixture.sqlTable.replace("'", "''")}', 'i')").use { rows ->
        assertTrue(rows.next()); assertNull(rows.getString(1))
      }
    }
    (fixture.storage.newWriteSession() as ImlWriteSession).use { writer ->
      val counts = countSql(writer)
      assertEquals(21L, write(writer, Write().createFeature(collection, NakshaFeature("stale-first"))).featureTupleList[0]?.tupleNumber?.featureNumber)
      assertEquals(22L, write(writer, Write().createFeature(collection, NakshaFeature("stale-second"))).featureTupleList[0]?.tupleNumber?.featureNumber)
      assertEquals(1, counts.probes)
      assertEquals(1, counts.maxima)
      assertEquals(1, counts.reconciliations)
      assertEquals(1, counts.advances)
      println("allocation stale unowned sequence: rows=2 metadata=${counts.probes} max=${counts.maxima} sequence-state=${counts.reconciliations} setval=${counts.advances} inserts=${counts.inserts}")
      writer.rollback()
      val retried = countSql(writer)
      assertEquals(23L, write(writer, Write().createFeature(collection, NakshaFeature("stale-retry"))).featureTupleList[0]?.tupleNumber?.featureNumber)
      assertEquals(1, retried.maxima)
      assertEquals(1, retried.reconciliations)
      assertEquals(0, retried.advances)
      println("allocation stale sequence after rollback: rows=1 metadata=${retried.probes} max=${retried.maxima} sequence-state=${retried.reconciliations} setval=${retried.advances} inserts=${retried.inserts}")
      writer.commit()
    }
    val config = ImlConfig("${fixture.config.id}-peer", fixture.config.jdbcUrl).apply {
      schema = fixture.schema
      metadataSchema = fixture.metadataSchema
      writeEnabled = true
    }
    try {
      val other = Naksha.useStorage(config) as ImlStorage
      (other.newWriteSession() as ImlWriteSession).use { writer ->
        val counts = countSql(writer)
        assertEquals(24L, write(writer, Write().createFeature(collection, NakshaFeature("stale-peer"))).featureTupleList[0]?.tupleNumber?.featureNumber)
        assertEquals(1, counts.probes)
        assertEquals(1, counts.maxima)
        assertEquals(1, counts.reconciliations)
        assertEquals(0, counts.advances)
        println("allocation second storage: rows=1 metadata=${counts.probes} max=${counts.maxima} sequence-state=${counts.reconciliations} setval=${counts.advances} inserts=${counts.inserts}")
        writer.commit()
      }
    } finally { Naksha.removeStorage(config) }
  }

  @Test
  fun `identity reconciles explicit override and uncalled sequence at max without rewinding`() = withWriter { fixture, writer ->
    val collection = NakshaCollection("identity-behind", fixture.schema).apply { indices = IndexList() }
    write(writer, Write().createCollection(collection))
    val layer = checkNotNull(writer.sessionMetadata.getLayer(fixture.schema, collection.id))
    writer.withConnection { connection ->
      connection.createStatement().use {
        it.executeUpdate("INSERT INTO ${layer.sqlTable} (id, version, operation, i, jsondata) OVERRIDING SYSTEM VALUE " +
          "SELECT 'explicit-' || n, 1, 'I', n, '{}'::jsonb FROM generate_series(1,20) n")
      }
      connection.prepareStatement("SELECT setval(pg_get_serial_sequence(?, 'i'), 20, false)").use {
        it.setString(1, layer.sqlTable); it.executeQuery().close()
      }
    }
    val counts = countSql(writer)
    assertEquals(21L, write(writer, Write().createFeature(collection, NakshaFeature("identity-after-explicit"))).featureTupleList[0]?.tupleNumber?.featureNumber)
    assertEquals(1, counts.maxima)
    assertEquals(1, counts.advances)
    writer.commit()
    val nextCounts = countSql(writer)
    assertEquals(22L, write(writer, Write().createFeature(collection, NakshaFeature("identity-next-tx"))).featureTupleList[0]?.tupleNumber?.featureNumber)
    assertEquals(1, nextCounts.maxima)
    assertEquals(0, nextCounts.advances)
    println("allocation stale identity: first=21 next-tx=22 max-per-tx=1 setval-first=1 setval-next=0")
  }

  @Test
  fun `standard indices match numeric predicates and raw tags and only obsolete owned indices are dropped`() = withWriter { fixture, writer ->
    val collection = NakshaCollection("indexed", fixture.schema).apply { indices = IndexList(XyzIndices.ALL) }
    write(writer, Write().createCollection(collection))
    val layer = checkNotNull(writer.sessionMetadata.getLayer(fixture.schema, collection.id))
    writer.withConnection { connection ->
      connection.prepareStatement("SELECT indexname, indexdef FROM pg_indexes WHERE schemaname = ? AND tablename = ?").use {
        it.setString(1, layer.schema); it.setString(2, layer.table)
        it.executeQuery().use { rows ->
          var numeric = false
          var tags = false
          while (rows.next()) {
            val sql = rows.getString(2)
            if (sql.contains("updatedAt")) { assertTrue(sql.contains("jsonb_typeof")); assertTrue(sql.contains("numeric")); numeric = true }
            if (sql.contains("USING gin")) { assertTrue(sql.contains(" -> 'tags'::text")); assertFalse(sql.contains("#>")); tags = true }
          }
          assertTrue(numeric)
          assertTrue(tags)
        }
      }
      connection.createStatement().use { it.execute("CREATE INDEX ${ImlMetadata.quote("${layer.table}_xyz_external")} ON ${layer.sqlTable} (id)") }
    }
    collection.indices = IndexList(listOf(StandardIndices.Geometry))
    write(writer, Write().updateCollection(collection, false))
    writer.withConnection { connection ->
      connection.prepareStatement("SELECT indexname FROM pg_indexes WHERE schemaname = ? AND tablename = ?").use {
        it.setString(1, layer.schema); it.setString(2, layer.table)
        it.executeQuery().use { rows ->
          val names = HashSet<String>()
          while (rows.next()) names.add(rows.getString(1))
          assertTrue("${layer.table}_xyz_external" in names)
          assertTrue("${layer.table}_xyz_${StandardIndices.Geometry.name}" in names)
          assertTrue("${layer.table}_xyz_${XyzIndices.XyzTags.name}" in names)
          for (index in XyzIndices.ALL) if (index.name != StandardIndices.Geometry.name && index.name != XyzIndices.XyzTags.name) assertFalse("${layer.table}_xyz_${index.name}" in names)
        }
      }
    }
  }

  @Test
  fun `compiler timestamp and tag predicates use matching indices in explain`() = withWriter { fixture, writer ->
    val collection = NakshaCollection("planner", fixture.schema).apply {
      indices = IndexList(listOf(XyzIndices.XyzAppId, XyzIndices.XyzTags))
    }
    write(writer, Write().createCollection(collection))
    val layer = checkNotNull(writer.sessionMetadata.getLayer(fixture.schema, collection.id))
    writer.withConnection { connection ->
      connection.createStatement().use {
        it.executeUpdate("INSERT INTO ${layer.sqlTable} (id, version, operation, jsondata) " +
          "SELECT n::text, 1, 'I', jsonb_build_object('properties', jsonb_build_object('@ns:com:here:xyz', " +
          "jsonb_build_object('appId', 'planner', 'updatedAt', n, 'tags', jsonb_build_array(CASE WHEN n = 100 THEN 'needle' ELSE 'other' END)))) " +
          "FROM generate_series(1,100) n")
        it.execute("ANALYZE ${layer.sqlTable}")
        it.execute("SET LOCAL enable_seqscan = false")
      }
      for ((op, index) in arrayOf(
        And(Equals(XyzMembers.XyzAppId, "planner"), Gte("updated_at", 100)) to XyzIndices.XyzAppId,
        TagListContains(XyzMembers.XyzTags, "needle") to XyzIndices.XyzTags)) {
        val query = ImlQuery(ReadFeatures().apply { queryMembers = op }, 100, collection.useMembers())
        connection.prepareStatement("SELECT count(*) FROM ${layer.sqlTable} WHERE ${query.predicate}").use {
          query.bind(it)
          it.executeQuery().use { rows -> assertTrue(rows.next()); assertEquals(1, rows.getInt(1)) }
        }
        val plan = connection.prepareStatement("EXPLAIN (COSTS OFF) SELECT id FROM ${layer.sqlTable} WHERE ${query.predicate}").use {
          query.bind(it)
          it.executeQuery().use { rows ->
            val lines = ArrayList<String>()
            while (rows.next()) lines.add(rows.getString(1))
            lines.joinToString("\n")
          }
        }
        assertTrue(plan.contains("${layer.table}_xyz_${index.name}"), plan)
        assertTrue(plan.contains("Index Cond:"), plan)
        assertFalse(plan.contains("Seq Scan"), plan)
        val condition = plan.substringAfter("Index Cond:").substringBefore("\n")
        if (index == XyzIndices.XyzAppId) {
          assertTrue(condition.contains("updatedAt"), plan)
          assertTrue(condition.contains(">="), plan)
        } else assertTrue(condition.contains("@>"), plan)
        println("planner ${index.name}: matched rows=1 enable_seqscan=false\n$plan")
      }
    }
  }

  private class Counts(var probes: Int = 0, var maxima: Int = 0, var inserts: Int = 0,
    var reconciliations: Int = 0, var advances: Int = 0)

  private fun countSql(writer: ImlWriteSession): Counts {
    writer.useTransaction()
    val field = ImlWriteSession::class.java.getDeclaredField("connection").apply { isAccessible = true }
    val real = field.get(writer) as Connection
    val counts = Counts()
    val wrapped = Proxy.newProxyInstance(Connection::class.java.classLoader, arrayOf(Connection::class.java)) { _, method, args ->
      val result = try { method.invoke(real, *(args ?: emptyArray())) } catch (error: InvocationTargetException) { throw error.targetException }
      if (result is Statement) {
        val sql = if (method.name == "prepareStatement") args?.get(0) as String else null
        val type = if (result is PreparedStatement) PreparedStatement::class.java else Statement::class.java
        Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, operation, parameters ->
          if (operation.name == "executeQuery") {
            val query = sql ?: parameters?.get(0) as? String ?: ""
            if (query.contains("FROM information_schema.columns") && query.contains("column_name = 'i'")) counts.probes++
            if (query.contains("max(i)")) counts.maxima++
            if (query.startsWith("SELECT last_value, is_called")) counts.reconciliations++
            if (query.startsWith("SELECT setval(?::regclass")) counts.advances++
            if (query.startsWith("INSERT INTO") && query.contains("RETURNING")) { counts.inserts++; assertFalse(query.contains("max(i)")) }
          }
          try { operation.invoke(result, *(parameters ?: emptyArray())) } catch (error: InvocationTargetException) { throw error.targetException }
        }
      } else result
    } as Connection
    field.set(writer, wrapped)
    return counts
  }

  private fun rows(vararg values: Any?): ResultSet {
    var first = true
    return proxy(ResultSet::class.java) { method, args ->
      when (method) {
        "next" -> first.also { first = false }
        "getString" -> values[(args[0] as Int) - 1] as String?
        "getLong" -> values[(args[0] as Int) - 1] as Long
        "getBoolean" -> values[(args[0] as Int) - 1] as Boolean
        else -> null
      }
    }
  }

  private fun <T> proxy(type: Class<T>, call: (String, Array<out Any?>) -> Any?): T = type.cast(
    Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, args -> call(method.name, args ?: emptyArray()) })

  private fun write(writer: ImlWriteSession, operation: Write): SuccessResponse =
    writer.executeWrite(WriteRequest().add(operation).apply { returnResults = true }) as SuccessResponse

  private fun withWriter(block: (ImlTestFixture, ImlWriteSession) -> Unit) = ImlTestFixture.withFixture { fixture ->
    fixture.config.writeEnabled = true
    val context = NakshaContext.currentContext()
    val previous = context.su
    context.su = true
    try { (fixture.storage.newWriteSession() as ImlWriteSession).use { block(fixture, it) } } finally { context.su = previous }
  }
}
