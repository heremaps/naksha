package naksha.psql

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import naksha.base.Id
import naksha.base.Version
import naksha.model.objects.NakshaCollection
import naksha.model.objects.NakshaFeature
import naksha.model.objects.StandardMembers.StandardMembers_C.FeatureNumber
import naksha.model.objects.StandardMembers.StandardMembers_C.NextVersion
import naksha.model.objects.StandardMembers.StandardMembers_C.Id as IdMember
import naksha.model.request.ReadFeatures
import naksha.model.request.Write
import naksha.model.request.WriteRequest
import naksha.model.request.ops.*
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import kotlin.test.*

/**
 * Dedicated-database regressions and repeatable EXPLAIN benchmark. Set NAKSHA_TEST_QUERY_DB_URL and NAKSHA_TEST_PSQL_DB_URL to the same disposable database; NAKSHA_QUERY_BASELINE=true disables pruning assertions for a pre-change run. Uses 768 features per collection, one warmup and five measured plans, reporting median server planning/execution times and a full JSON plan.
 *
 * Captured before builder changes on 2026-11-05: named equality at N=7/16/256 visited 7/16/256 leaves, planning 0.386/0.599/913.813 ms, execution 0.041/0.046/2.773 ms, shared hits 8/17/257. After: 1/1/1 leaves, planning 0.183/0.150/0.174 ms, execution 0.018/0.012/0.011 ms, two hits each. All samples were cached (zero shared reads); these small-fixture results measure fanout, not production throughput.
 * @since 3.0
 */
@EnabledIfEnvironmentVariable(named = "NAKSHA_TEST_QUERY_DB_URL", matches = ".+")
class PgQueryHistoryPartitionTest : PgTestBase(collection = null) {
  init {
    assertEquals(System.getenv("NAKSHA_TEST_QUERY_DB_URL"), System.getenv("NAKSHA_TEST_PSQL_DB_URL"))
  }

  @Test
  fun broadNextVersionKeepsOneStatePerFeature() {
    useCollection("query_broad_nv")
    val states = seedHistory()
    for (predicate in listOf<Op>(Gt(NextVersion, 0L),
      Or(Equals(NextVersion, states[1]), Equals(IdMember, "named-a")),
      Not(Equals(NextVersion, 0L)), IsAnyOf(NextVersion, states[1], states[2], Version.HEAD.number))) {
      val rows = executeRead(request(predicate)).features
      val expectedIds = if (predicate is Or) setOf("named-a") else setOf("named-a", "256")
      assertEquals(expectedIds.size, rows.size, "versions=1 must cap each feature: $predicate")
      assertEquals(expectedIds, rows.map { assertNotNull(it).id }.toSet())
      for (row in rows) assertEquals("state-2", assertNotNull(row).title)
    }
  }

  @Test
  fun snapshotsExactPredecessorsAndLatestN() {
    useCollection("query_temporal")
    val states = seedHistory()
    for (i in states.indices) {
      val rows = executeRead(request().apply { version = states[i] }).features
      assertEquals(2, rows.size)
      for (row in rows) assertEquals("state-$i", assertNotNull(row).title)
      val broad = executeRead(request(Gt(NextVersion, 0L)).apply { version = states[i] }).features
      assertEquals(2, broad.size)
      for (row in broad) assertEquals("state-$i", assertNotNull(row).title)
    }
    for (predicate in listOf<Op>(Equals(NextVersion, states[1]), IsAnyOf(NextVersion, states[1]),
      And(IsAnyOf(IdMember, "named-a", "256"), Equals(NextVersion, states[1])))) {
      val rows = executeRead(request(predicate)).features
      assertEquals(2, rows.size)
      for (row in rows) assertEquals("state-0", assertNotNull(row).title)
    }
    val latest = executeRead(request().apply { versions = 2 }).features
    assertEquals(4, latest.size)
    for (id in listOf("named-a", "256")) {
      assertEquals(listOf("state-2", "state-1"), latest.filter { it?.id == id }.map { it!!.title })
    }
    val bounded = executeRead(request().apply { versions = 2; version = states[1]; minVersion = states[0] }).features
    assertEquals(4, bounded.size)
    for (id in listOf("named-a", "256")) {
      assertEquals(listOf("state-1", "state-0"), bounded.filter { it?.id == id }.map { it!!.title })
    }
    executeWrite(WriteRequest().add(Write().deleteFeatureById(collection, "named-a")))
    assertEquals(listOf("256"), executeRead(request()).features.map { it!!.id })
    assertEquals(2, executeRead(request().apply { queryDeleted = true }).features.size)
    assertEquals(2, executeRead(request().apply { version = states[2] }).features.size)
  }

  @Test
  fun positiveIdPruningRetainsTextAndDoesNotConvertRangeBounds() {
    useCollection("query_id_semantics")
    insertFeatures(NakshaFeature("named-a"), NakshaFeature("named-b"), NakshaFeature("128"), NakshaFeature("255"), NakshaFeature("256"))
    storage.newReadSession(newSessionOptions()).use { session ->
      val query = PgQueryBuilder(session as PgSession, request()).build()
      for (predicate in listOf<Op>(Equals(IdMember, "named-a"), IsAnyOf(IdMember, "named-a"), IsAnyOf(IdMember, "named-a", "128"))) {
        val clause = assertNotNull(PgQueryWhereBuilder(request(predicate), query.pgCollection).build())
        assertTrue("\"id\"" in clause.where)
        assertTrue("\"fn\"" in clause.where)
        // Simulate a hash collision at the candidate ordinal, without corrupting stored tuples.
        storage.adminConnection().use { conn ->
          conn.prepare("SELECT 1 FROM (SELECT $1::bigint AS fn, 'different-id'::text AS id) candidate WHERE ${clause.where.replace(Regex("\\$(\\d+)")) { "\$${it.groupValues[1].toInt() + 1}" }}",
            arrayOf(PgType.INT64.toString(), *clause.argTypeNames)).use { plan ->
            plan.execute(arrayOf(Id("named-a").featureNumberToOrdinal, *clause.argValues.toTypedArray())).use { cursor -> assertFalse(cursor.next()) }
          }
        }
      }
    }
    for (member in listOf(IdMember, FeatureNumber)) {
      val bound: Any = if (member == IdMember) "255" else 255L
      val rows = executeRead(request(Gt(member, bound))).features
      assertEquals(listOf("256"), rows.map { it!!.id })
    }
    assertEquals(setOf("named-b"),
      executeRead(request(Not(Equals(IdMember, "named-a")))).features.map { it!!.id }.toSet())
  }

  @Test
  fun partitionFanoutBenchmarkAndPhysicalRouting() {
    val baseline = System.getenv("NAKSHA_QUERY_BASELINE") == "true"
    for (partitions in intArrayOf(7, 16, 256)) {
      useCollection(NakshaCollection("query_fanout_$partitions", catalog.id, partitions = partitions))
      val writes = WriteRequest()
      for (i in 0 until 512) writes.add(Write().createFeature(collection, NakshaFeature("named-$i")))
      for (i in 0 until 256) writes.add(Write().createFeature(collection, NakshaFeature(i.toString())))
      executeWrite(writes)
      val predicates = listOf<Op>(Equals(IdMember, "named-42"), IsAnyOf(IdMember, "named-42", "named-43"),
        IsAnyOf(IdMember, "named-42", "256", "128"))
      for ((index, predicate) in predicates.withIndex()) {
        storage.newReadSession(newSessionOptions()).use { session ->
          val query = PgQueryBuilder(session as PgSession, request(predicate).apply { queryHistory = false }).build()
          storage.adminConnection().use { conn ->
            conn.execute("ANALYZE ${query.pgCollection.qualifiedName}").close()
            val clause = assertNotNull(PgQueryWhereBuilder(request(predicate), query.pgCollection).build())
            val expectedIds = when (index) {
              0 -> listOf("named-42")
              1 -> listOf("named-42", "named-43")
              else -> listOf("named-42", "128")
            }
            val expectedTables = HashSet<String>()
            for (id in expectedIds) {
              conn.execute("SELECT tableoid::regclass::text AS physical FROM ${query.pgCollection.qualifiedName} WHERE fn = $1 AND nv = ${Version.HEAD.number}",
                arrayOf(Id(id).featureNumberToOrdinal)).use { cursor ->
                assertTrue(cursor.next())
                val physical = cursor.get<String>("physical")
                assertTrue(physical.endsWith("\$${Id(id).partitionIndex(partitions)}\""), physical)
                expectedTables.add(physical.substringAfter('.').trim('"'))
              }
            }
            conn.prepare("SELECT DISTINCT tableoid::regclass::text AS physical FROM ${query.pgCollection.qualifiedName} WHERE nv = ${Version.HEAD.number} AND ${clause.where}", clause.argTypeNames).use { plan ->
              plan.execute(clause.argValues.toTypedArray()).use { cursor ->
                val tables = HashSet<String>()
                while (cursor.next()) tables.add(cursor.get<String>("physical").substringAfter('.').trim('"'))
                assertEquals(expectedTables, tables)
              }
            }
            val samples = ArrayList<JsonNode>()
            repeat(6) { run ->
              conn.prepare("EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) ${query.sql}", query.argTypes).use { plan ->
                plan.execute(query.argValues).use { cursor ->
                  assertTrue(cursor.next())
                  val json = cursor.get<Any>("QUERY PLAN").toString()
                  val sample = ObjectMapper().readTree(json)[0]
                  if (run > 0) samples.add(sample)
                  if (run == 1) println("QUERY_PLAN baseline=$baseline N=$partitions predicate=$index $json")
                }
              }
            }
            val relations = HashSet<String>()
            collectRelations(samples[0]["Plan"], relations)
            if (!baseline) assertEquals(expectedTables + if (index == 2) setOf("${Id(collection.id).collectionName()}\$head\$${Id("256").partitionIndex(partitions)}") else emptySet(), relations)
            val planning = samples.map { it["Planning Time"].asDouble() }.sorted()[2]
            val execution = samples.map { it["Execution Time"].asDouble() }.sorted()[2]
            val plan = samples[0]["Plan"]
            println("QUERY_BENCH baseline=$baseline N=$partitions predicate=$index leaves=${relations.size} planning_ms=$planning execution_ms=$execution hit=${plan["Shared Hit Blocks"]} read=${plan["Shared Read Blocks"]}")
          }
        }
      }
    }
  }

  private fun collectRelations(node: JsonNode, relations: MutableSet<String>) {
    if (node.has("Relation Name")) relations.add(node["Relation Name"].asText())
    val children = node["Plans"] ?: return
    for (child in children) collectRelations(child, relations)
  }

  private fun request(predicate: Op? = null) = ReadFeatures().apply {
    catalogId = collection.catalogId
    collectionId = collection.id
    queryHistory = true
    queryMembers = predicate
  }

  private fun seedHistory(): List<Long> {
    val versions = ArrayList<Long>()
    for (i in 0..2) {
      val writes = WriteRequest()
      for (id in listOf("named-a", "256")) {
        val feature = NakshaFeature(id).apply { title = "state-$i" }
        writes.add(if (i == 0) Write().createFeature(collection, feature) else Write().updateFeature(collection, feature, false))
      }
      val result = executeWrite(writes)
      versions.add(assertNotNull(result.features[0]).properties.xyz.guid!!.tupleNumber.version)
    }
    return versions
  }
}
