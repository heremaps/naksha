package naksha.psql

import java.nio.file.Files
import java.nio.file.Path
import naksha.base.Action
import naksha.base.Id
import naksha.base.NakshaException
import naksha.base.Platform
import naksha.model.Naksha
import naksha.model.Tuple
import naksha.model.objects.NakshaCatalog
import naksha.model.objects.NakshaCollection
import naksha.model.objects.NakshaFeature
import naksha.model.objects.StoreMode
import naksha.model.request.Write
import naksha.model.request.WriteRequest
import naksha.model.request.SuccessResponse
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.mockito.Mockito
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertIs

/** Captures exact generated mutation plans against fresh local profiling fixtures; EXPLAIN ANALYZE writes are rolled back. @since 3.0 */
@EnabledIfEnvironmentVariable(named = "NAKSHA_TEST_MUTATION_PROFILE_DIR", matches = ".+")
class PgMutationPlanProfileTest {
  @Test
  fun explainExistingMutationBatches() {
    assertEquals(System.getenv("NAKSHA_TEST_THROUGHPUT_DB_URL"), System.getenv("NAKSHA_TEST_PSQL_DB_URL"))
    val directory = Path.of(System.getenv("NAKSHA_TEST_MUTATION_PROFILE_DIR"))
    Files.createDirectories(directory)
    val storage = PgStorage(PgConfig(Platform.getTestStorageId()).apply {
      className = "naksha.psql.PgStorage"
      master = PgInstanceConfig.fromUri(System.getenv("NAKSHA_TEST_PSQL_DB_URL"))
    }, true, false)
    val catalog = NakshaCatalog(storage.id, "mutation_profile")
    val collection = NakshaCollection("states", catalog.id, partitions = 4, storeHistory = StoreMode.ON, storeDeleted = StoreMode.ON)
    write(storage, WriteRequest().add(Write().createCatalog(catalog)))
    write(storage, WriteRequest().add(Write().createCollection(collection)))
    var seeded = 0
    for (population in listOf(8000, 32000)) {
      while (seeded < population) {
        val request = WriteRequest()
        for (i in seeded until seeded + 1000) {
          val feature = Plv8PerfTest.smallTopologyFeatureTemplate.copy<NakshaFeature>(recursive = true)
          feature.id = "profile_$i"
          request.add(Write().createFeature(collection, feature))
        }
        write(storage, request)
        seeded += 1000
      }
      for ((size, operation) in listOf(100 to "UPDATE", 100 to "UPSERT", 100 to "DELETE",
        1000 to "UPDATE", 1000 to "UPSERT", 1000 to "DELETE")) {
        storage.newWriteSession(Naksha.useAdminOptions()).use { raw ->
          val session = raw as PgSession
          val version = session.useTransaction().version.number
          session.useConnNonNull(false) { conn ->
            val collectionId = Id(collection.id)
            val p = assertNotNull(storage.database.getPgCollection(Id(catalog.id).intValue, collectionId.intValue, conn))
            val inputs = ArrayList<PgMutationInput>(size)
            for (i in 0 until size) {
              val id = "profile_$i"
              if (operation == "DELETE") {
                inputs.add(PgMutationInput(PgMutationOp.DELETE, Id(id).number, id, version))
              } else {
                val tuple = Tuple.encodeRaw(Plv8PerfTest.smallTopologyFeatureTemplate.copy<NakshaFeature>(recursive = true).apply { this.id = id },
                  storage.database.id.number, p.catalogId.intValue, p.id.intValue, p.naksha.validatedMembers, version,
                  requestedAction = if (operation == "UPDATE") Action.UPDATE else Action.CREATE).tuple
                inputs.add(PgMutationInput(if (operation == "UPDATE") PgMutationOp.UPDATE else PgMutationOp.UPSERT,
                  Id(id).number, id, tuple.version, tuple))
              }
            }
            var capturedSql: String? = null
            var capturedTypes: Array<String>? = null
            var capturedArguments: Array<Any?>? = null
            val observed = Mockito.spy(conn)
            Mockito.doAnswer { call ->
              val sql = call.getArgument<String>(0)
              if (!sql.contains("FOR UPDATE OF h")) return@doAnswer conn.prepare(sql, call.getArgument(1))
              capturedSql = sql
              capturedTypes = call.getArgument(1)
              val plan = Mockito.mock(PgPlan::class.java)
              Mockito.doAnswer { execution ->
                capturedArguments = execution.getArgument(0)
                throw IllegalStateException("Mutation arguments captured for rollback-only profiling")
              }.`when`(plan).executeMutation(Mockito.any())
              plan
            }.`when`(observed).prepare(Mockito.anyString(), Mockito.nullable(Array<String>::class.java))
            try {
              PgBatchMutation(storage.database.id, p.catalogId, p.id, p.columns, p.storeHistory, p).execute(observed, inputs)
            } catch (_: NakshaException) {
              assertNotNull(capturedArguments)
            }
            val sql = assertNotNull(capturedSql)
            val types = assertNotNull(capturedTypes)
            val arguments = assertNotNull(capturedArguments)
            for (sample in 0 until 2) {
              val started = System.nanoTime()
              var json: String? = null
              assertTrue(!conn.tryBatch {
                conn.prepare("EXPLAIN (ANALYZE, BUFFERS, WAL, SETTINGS, FORMAT JSON) $sql", types).use { plan ->
                  plan.executeMutation(arguments).use { cursor ->
                    assertTrue(cursor.next())
                    json = cursor.column("QUERY PLAN") as String
                  }
                }
                false
              })
              Files.writeString(directory.resolve("${operation.lowercase()}_${population}_${size}_$sample.json"), assertNotNull(json))
              println("MUTATION_PLAN_PROFILE operation=$operation population=$population rows=$size sample=$sample total_ms=${(System.nanoTime() - started) / 1e6} rolled_back=true")
            }
          }
        }
      }
    }
  }

  private fun write(storage: PgStorage, request: WriteRequest) {
    storage.newWriteSession(Naksha.useAdminOptions()).use { session ->
      assertIs<SuccessResponse>(session.execute(request))
      session.commit()
    }
  }
}
