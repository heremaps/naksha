package naksha.psql

import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import naksha.base.Action
import naksha.model.NakshaContext
import naksha.model.objects.NakshaCollection
import naksha.model.objects.StoreMode
import naksha.model.request.Write
import naksha.model.request.WriteRequest
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import kotlin.math.ceil
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Local end-to-end JVM write-path measurements with committed seeds, history and tombstones enabled. No server-capacity claim is implied. @since 3.0 */
@EnabledIfEnvironmentVariable(named = "NAKSHA_TEST_THROUGHPUT_DB_URL", matches = ".+")
class PgMutationThroughputTest : PgTestBase(collection = null) {
  @Test
  fun measureInsertUpdateUpsertAndDelete() {
    assertEquals(System.getenv("NAKSHA_TEST_THROUGHPUT_DB_URL"), System.getenv("NAKSHA_TEST_PSQL_DB_URL"))
    val workers = 4
    val featuresPerRound = 8000
    val context = NakshaContext.currentContext()
    val pool = Executors.newFixedThreadPool(workers)
    try {
      for (batchSize in listOf(100, 1000)) {
        for (operation in listOf("INSERT", "UPDATE", "UPSERT", "DELETE")) {
          useCollection(NakshaCollection("throughput_${operation.lowercase()}_$batchSize", catalog.id,
            partitions = 4, storeHistory = StoreMode.ON, storeDeleted = StoreMode.ON))
          val rates = ArrayList<Double>(3)
          for (round in 0 until 4) {
            val requests = ArrayList<WriteRequest>(featuresPerRound / batchSize)
            for (batch in 0 until featuresPerRound / batchSize) {
              val request = WriteRequest()
              val seeds = WriteRequest()
              for (i in 0 until batchSize) {
                val feature = Plv8PerfTest.smallTopologyFeatureTemplate.copy<naksha.model.objects.NakshaFeature>(recursive = true)
                feature.id = "${operation}_${batchSize}_${round}_${batch}_$i"
                if (operation != "INSERT") seeds.add(Write().createFeature(collection, feature.copy(recursive = true)))
                request.add(when (operation) {
                  "INSERT" -> Write().createFeature(collection, feature)
                  "UPDATE" -> Write().updateFeature(collection, feature, atomic = false)
                  "UPSERT" -> Write().upsertFeature(collection, feature, atomic = false)
                  else -> Write().deleteFeatureById(collection, feature.id)
                })
              }
              if (operation != "INSERT") executeWrite(seeds)
              requests.add(request)
            }
            val expectedAction = when (operation) {
              "INSERT" -> Action.CREATE
              "DELETE" -> Action.DELETE
              else -> Action.UPDATE
            }
            val started = System.nanoTime()
            val futures = ArrayList<java.util.concurrent.Future<Double>>(requests.size)
            for (request in requests) futures.add(pool.submit(Callable {
              context.attachToCurrentThread()
              val batchStarted = System.nanoTime()
              val response = executeWrite(request)
              val millis = (System.nanoTime() - batchStarted) / 1e6
              assertEquals(batchSize, response.featureTupleList.size)
              for (feature in response.featureTupleList) assertEquals(expectedAction, feature!!.tupleNumber.action)
              millis
            }))
            val latencies = ArrayList<Double>(requests.size)
            for (future in futures) latencies.add(future.get(3, TimeUnit.MINUTES))
            val seconds = (System.nanoTime() - started) / 1e9
            latencies.sort()
            val rate = featuresPerRound / seconds
            if (round > 0) rates.add(rate)
            val median = (latencies[(latencies.size - 1) / 2] + latencies[latencies.size / 2]) / 2
            val p95 = latencies[ceil(latencies.size * 0.95).toInt() - 1]
            println("MUTATION_THROUGHPUT operation=$operation batch=$batchSize workers=$workers round=$round warmup=${round == 0} features=$featuresPerRound seconds=$seconds features_per_second=$rate batch_median_ms=$median batch_p95_ms=$p95 encoding=true commit=true seeding=false history=true partitions=4 payload=small_topology")
          }
          rates.sort()
          println("MUTATION_THROUGHPUT_SUMMARY operation=$operation batch=$batchSize workers=$workers rounds=${rates.size} median_features_per_second=${rates[rates.size / 2]} min_features_per_second=${rates.first()} max_features_per_second=${rates.last()}")
        }
      }
    } finally {
      pool.shutdownNow()
      assertTrue(pool.awaitTermination(1, TimeUnit.MINUTES), "Throughput workers did not terminate")
    }
  }
}
