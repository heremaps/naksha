package naksha.psql

import naksha.base.JvmMap
import naksha.base.Platform
import naksha.base.Id
import naksha.model.NakshaContext
import naksha.model.objects.NakshaCollection
import naksha.model.objects.NakshaFeature
import naksha.model.objects.StoreMode
import naksha.model.request.SuccessResponse
import naksha.model.request.Write
import naksha.model.request.WriteRequest
import naksha.psql.Plv8PerfTest.FeatureSource.*
import naksha.model.RandomFeatures.RandomFeatures_C.randomFeatures
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.ceil
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

@Suppress("HasPlatformType", "MayBeConstant")
class Plv8PerfTest : PgTestBase(
    NakshaCollection(
        id = "",
        partitions = NUM_OF_PARTITIONS,
        storeHistory = StoreMode.ON,
        storeDeleted = StoreMode.ON
    )// closed-enum opt-in list removed; default indices applied automatically
) {
    companion object {
        val featureSource = JSON_TOPOLOGY_SMALL
        val NUM_OF_PARTITIONS = 4
        val OVERLOAD_FACTOR = 1
        val BATCHES_PER_WORKER = 3
        val FEATURES_PER_BATCH = 100
        val numberOfBatches = NUM_OF_PARTITIONS * BATCHES_PER_WORKER * OVERLOAD_FACTOR
        val concurrency = NUM_OF_PARTITIONS * OVERLOAD_FACTOR

        val jsonPath = Companion::class.java.getResource("/topology.json")
        val json = Files.readString(Paths.get(jsonPath.toURI()))
        val topologyFeatureTemplate: NakshaFeature = (Platform.fromJSON(json) as JvmMap).proxy(NakshaFeature::class)

        val smallJsonPath = Companion::class.java.getResource("/small_topology.json")
        val smallJson = Files.readString(Paths.get(smallJsonPath.toURI()))
        val smallTopologyFeatureTemplate: NakshaFeature = (Platform.fromJSON(smallJson) as JvmMap).proxy(NakshaFeature::class)
    }

    @Ignore
    fun shouldBeIgnored() {
    }

    @DisabledIfEnvironmentVariable(named = "NAKSHA_SUPPRESS_PERF_TEST", matches = "true")
    @Test
    fun shouldInsertManyFeatures() {
        // Prepare
        val batchRequests = mutableListOf<WriteRequest>()
        for (i in 1..numberOfBatches) {
            val featuresInBatch = generateFeatures(featureSource, FEATURES_PER_BATCH, "insert_$i")
            val writeFeaturesReq = WriteRequest().apply {
                featuresInBatch.forEach { featureToCreate ->
                    add(Write().createFeature(collection, featureToCreate))
                }
            }
            batchRequests.add(writeFeaturesReq)
        }

        // Execute
        println("Starting $numberOfBatches batches, $FEATURES_PER_BATCH features each, on $concurrency threads.")
        executeParallel(concurrency, batchRequests)
    }

    @DisabledIfEnvironmentVariable(named = "NAKSHA_SUPPRESS_PERF_TEST", matches = "true")
    @Test
    fun shouldUpsertManyFeatures() {
        // Prepare
        val batchRequests = mutableListOf<WriteRequest>()
        for (i in 1..numberOfBatches) {
            val featuresInBatch = generateFeatures(featureSource, FEATURES_PER_BATCH, "upsert_$i")
            seedFeatures(featuresInBatch)

            val writeFeaturesReq = WriteRequest().apply {
                featuresInBatch.forEach { featureToCreate ->
                    add(Write().upsertFeature(collection, featureToCreate, atomic = false))
                }
            }
            batchRequests.add(writeFeaturesReq)
        }

        // Execute
        println("Starting $numberOfBatches batches, $FEATURES_PER_BATCH features each, on $concurrency threads.")
        executeParallel(concurrency, batchRequests)
    }

    @DisabledIfEnvironmentVariable(named = "NAKSHA_SUPPRESS_PERF_TEST", matches = "true")
    @Test
    fun shouldInsertGroupedByPartition() {
        // Prepare
        val allFeatures = generateFeatures(featureSource, FEATURES_PER_BATCH * numberOfBatches, "grouped_insert")
        val groupedFeatures = groupByPartition(allFeatures)

        val batchRequests = mutableListOf<WriteRequest>()
        for (requestFeatures in groupedFeatures.values) {
            val writeFeaturesReq = WriteRequest().apply {
                requestFeatures.forEach { featureToCreate ->
                    add(Write().createFeature(collection, featureToCreate))
                }
            }
            batchRequests.add(writeFeaturesReq)
        }

        // Execute
        println("Starting ${batchRequests.size} partition-grouped batches, ${allFeatures.size} total features, on $concurrency threads.")
        executeParallel(concurrency, batchRequests)
    }

    @DisabledIfEnvironmentVariable(named = "NAKSHA_SUPPRESS_PERF_TEST", matches = "true")
    @Test
    fun shouldUpsertGroupedByPartition() {
        // Prepare
        val allFeatures = generateFeatures(featureSource, FEATURES_PER_BATCH * numberOfBatches, "grouped_upsert")
        val groupedFeatures = groupByPartition(allFeatures)

        val batchRequests = mutableListOf<WriteRequest>()
        for (requestFeatures in groupedFeatures.values) {
            seedFeatures(requestFeatures)
            val writeFeaturesReq = WriteRequest().apply {
                requestFeatures.forEach { featureToCreate ->
                    add(Write().upsertFeature(collection, featureToCreate, atomic = false))
                }
            }
            batchRequests.add(writeFeaturesReq)
        }

        // Execute
        println("Starting ${batchRequests.size} partition-grouped batches, ${allFeatures.size} total features, on $concurrency threads.")
        executeParallel(concurrency, batchRequests)
    }

    private fun generateFeatures(source: FeatureSource, numberOfFeatures: Int, idPrefix: String): List<NakshaFeature> {
        val features = when (source) {
            GENERATED_RANDOM -> randomFeatures(count = numberOfFeatures)
            JSON_TOPOLOGY -> List(numberOfFeatures) { featureCopy(topologyFeatureTemplate) }
            JSON_TOPOLOGY_SMALL -> List(numberOfFeatures) { featureCopy(smallTopologyFeatureTemplate) }
        }
        for (i in features.indices) features[i].id = "${idPrefix}_$i"
        return features
    }

    private fun groupByPartition(features: List<NakshaFeature>): Map<Pair<Int, Int>, List<NakshaFeature>> {
        val batchesPerPartition = numberOfBatches / NUM_OF_PARTITIONS
        val partitionSizes = IntArray(NUM_OF_PARTITIONS)
        return features.groupBy {
            val partition = Id(it.id).partitionIndex(NUM_OF_PARTITIONS)
            partition to (partitionSizes[partition]++ % batchesPerPartition)
        }
    }

    private fun seedFeatures(features: List<NakshaFeature>) {
        val seedRequest = WriteRequest()
        for (feature in features) seedRequest.add(Write().createFeature(collection, featureCopy(feature)))
        // executeWrite opens a separate session and commits before the timed UPSERT run.
        executeWrite(seedRequest)
    }

    private fun executeParallel(concurrency: Int, batchRequests: List<WriteRequest>) {
        val stats = Collections.synchronizedList(mutableListOf<Stats>())
        val context = NakshaContext.currentContext()
        val threadPool = Executors.newFixedThreadPool(concurrency)
        val start = System.nanoTime()
        val end: Long
        try {
            val tasks = batchRequests.map { batchRequest ->
                threadPool.submit {
                    context.attachToCurrentThread()
                    val innerStart = System.nanoTime()
                    val response = executeWrite(batchRequest)
                    assertIs<SuccessResponse>(response)
                    val innerEnd = System.nanoTime()
                    stats.add(Stats(Thread.currentThread().name, (innerEnd - innerStart) / 1e6, batchRequest.writes.size))
                }
            }
            tasks.forEach { it.get() }
            end = System.nanoTime()
        } finally {
            threadPool.shutdownNow()
            assertTrue(threadPool.awaitTermination(1, TimeUnit.MINUTES), "Performance workers did not terminate")
        }
        val totalMs = stats.sumOf { it.timeMs }
        val totalSeconds = (end.toDouble() - start.toDouble()) / 1e9
        val numberOfBatches = batchRequests.count()
        val totalFeatures = stats.sumOf { it.featuresCount }
        val batchLatencies = stats.map { it.timeMs }.sorted()
        val medianMs = (batchLatencies[(numberOfBatches - 1) / 2] + batchLatencies[numberOfBatches / 2]) / 2
        val p95Ms = batchLatencies[ceil(numberOfBatches * 0.95).toInt() - 1]
        println("Total features: $totalFeatures, total runtime in seconds: $totalSeconds")
        println("Average batch execution (ms): ${totalMs / numberOfBatches} ")
        println("Median batch execution (ms): $medianMs, p95 batch execution (ms): $p95Ms")
        println("Features/s : ${totalFeatures / totalSeconds} ")
    }

    private fun featureCopy(feature: NakshaFeature): NakshaFeature {
        // Deep copy: a shallow copy shares `properties`, which encode mutates (writes xyz.uuid).
        return feature.copy<NakshaFeature>(recursive = true)
    }

    data class Stats(
        val threadName: String,
        val timeMs: Double,
        val featuresCount: Int
    )

    enum class FeatureSource {
        JSON_TOPOLOGY_SMALL, JSON_TOPOLOGY, GENERATED_RANDOM
    }
}
