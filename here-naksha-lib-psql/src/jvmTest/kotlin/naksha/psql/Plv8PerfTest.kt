package naksha.psql

import kotlinx.coroutines.runBlocking
import naksha.base.JvmMap
import naksha.base.Platform
import naksha.base.PlatformUtil
import naksha.model.Naksha.NakshaCompanion.featureNumber
import naksha.model.Naksha.NakshaCompanion.partitionNumber
import naksha.model.NakshaContext
import naksha.model.objects.NakshaCollection
import naksha.model.objects.NakshaFeature
import naksha.model.objects.StoreMode
import naksha.model.request.SuccessResponse
import naksha.model.request.Write
import naksha.model.request.WriteRequest
import naksha.psql.PgTest.PgTest_C.TEST_MAP_ID
import naksha.psql.Plv8PerfTest.FeatureSource.*
import naksha.psql.base.PgTestBase
import naksha.psql.util.ProxyFeatureGenerator.generateRandomFeatures
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.Executors
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertIs

class Plv8PerfTest : PgTestBase(
    NakshaCollection(
        id = "insert_perf_test_c",
        mapId = TEST_MAP_ID,
        partitions = NUM_OF_PARTITIONS,
        storeHistory = StoreMode.ON,
        storeDeleted = StoreMode.ON
    )
) {
    companion object {
        val featureSource = JSON_TOPOLOGY_SMALL
        val NUM_OF_CPU = Runtime.getRuntime().availableProcessors() / 2
        val NUM_OF_PARTITIONS = NUM_OF_CPU * 2
        val numberOfBatches = NUM_OF_PARTITIONS * 4
        val numberOfFeaturesInBatch = if (featureSource == JSON_TOPOLOGY) 100 else 250
        val concurrency = NUM_OF_CPU

        val jsonPath = Companion::class.java.getResource("/topology.json")
        val json = Files.readString(Paths.get(jsonPath.toURI()))
        val topologyFeatureTemplate: NakshaFeature = (Platform.fromJSON(json) as JvmMap).proxy(NakshaFeature::class)

        val smallJsonPath = Companion::class.java.getResource("/small_topology.json")
        val smallJson = Files.readString(Paths.get(smallJsonPath.toURI()))
        val smallTopologyFeatureTemplate: NakshaFeature = (Platform.fromJSON(smallJson) as JvmMap).proxy(NakshaFeature::class)
    }

    @Test
    fun shouldInsertManyFeatures() {
        // Prepare
        val batchRequests = mutableListOf<WriteRequest>()
        for (i in 1..numberOfBatches) {
            val featuresInBatch = generateFeatures(featureSource, numberOfFeaturesInBatch)
            val writeFeaturesReq = WriteRequest().apply {
                featuresInBatch.forEach { featureToCreate ->
                    add(Write().createFeature(collection, featureToCreate))
                }
            }
            batchRequests.add(writeFeaturesReq)
        }

        // Execute
        println("Starting $numberOfBatches batches, $numberOfFeaturesInBatch features each, on $concurrency threads.")
        executeParallel(concurrency, batchRequests)
    }

    @Test
    fun shouldUpsertManyFeatures() {
        // Prepare
        val batchRequests = mutableListOf<WriteRequest>()
        for (i in 1..numberOfBatches) {
            val featuresInBatch = generateFeatures(featureSource, numberOfFeaturesInBatch)

            val writeFeaturesReq = WriteRequest().apply {
                featuresInBatch.forEach { featureToCreate ->
                    add(Write().upsertFeature(collection, featureToCreate))
                }
            }
            batchRequests.add(writeFeaturesReq)
        }

        // Execute
        println("Starting $numberOfBatches batches, $numberOfFeaturesInBatch features each, on $concurrency threads.")
        executeParallel(concurrency, batchRequests)
    }

    @Test
    fun shouldInsertGroupedByPartition() {
        val numberOfBatchesPerPartition = numberOfBatches / NUM_OF_PARTITIONS

        // Prepare
        val allFeatures = generateFeatures(featureSource, numberOfFeaturesInBatch * numberOfBatches)
        val groupedFeatures =
            allFeatures.groupBy { "${partitionNumber(featureNumber(it.id)) % NUM_OF_PARTITIONS}_${Random.nextInt(0, numberOfBatchesPerPartition)}" }

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
        println("Starting $numberOfBatches batches, $numberOfFeaturesInBatch features each, on $concurrency threads.")
        executeParallel(concurrency, batchRequests)
    }

    @Test
    fun shouldUpsertGroupedByPartition() {
        val numberOfBatchesPerPartition = numberOfBatches / NUM_OF_PARTITIONS

        // Prepare
        val allFeatures = generateFeatures(featureSource, numberOfFeaturesInBatch * numberOfBatches)
        val groupedFeatures =
            allFeatures.groupBy { "${partitionNumber(featureNumber(it.id)) % NUM_OF_PARTITIONS}_${Random.nextInt(0, numberOfBatchesPerPartition)}" }

        val batchRequests = mutableListOf<WriteRequest>()
        for (requestFeatures in groupedFeatures.values) {
            val writeFeaturesReq = WriteRequest().apply {
                requestFeatures.forEach { featureToCreate ->
                    add(Write().upsertFeature(collection, featureToCreate))
                }
            }
            batchRequests.add(writeFeaturesReq)
        }

        // Execute
        println("Starting $numberOfBatches batches, $numberOfFeaturesInBatch features each, on $concurrency threads.")
        executeParallel(concurrency, batchRequests)
    }

    private fun generateFeatures(source: FeatureSource, numberOfFeatures: Int): List<NakshaFeature> {
        return when (source) {
            GENERATED_RANDOM -> generateRandomFeatures(count = numberOfFeatures)
            JSON_TOPOLOGY -> List(numberOfFeatures) { featureCopy(topologyFeatureTemplate) }
            JSON_TOPOLOGY_SMALL -> List(numberOfFeatures) { featureCopy(smallTopologyFeatureTemplate) }
        }
    }

    private fun executeParallel(concurrency: Int, batchRequests: List<WriteRequest>) = runBlocking {
        val stats = Collections.synchronizedList(mutableListOf<Stats>())
        val threadPool = Executors.newFixedThreadPool(concurrency)
        val context = NakshaContext.currentContext()
        val tasks = batchRequests.map { batchRequest ->
            threadPool.submit {
                context.attachToCurrentThread()
                val start = System.currentTimeMillis()
                val response = executeWrite(batchRequest)
                assertIs<SuccessResponse>(response)
                val end = System.currentTimeMillis()
                stats.add(Stats(Thread.currentThread().name, end - start, batchRequest.writes.size))
            }
        }
        tasks.forEach { it.get() }
        threadPool.shutdown()
        val totalMs = stats.sumOf { it.timeMs }
        val numberOfBatches = batchRequests.count()
        val totalFeatures = stats.sumOf { it.featuresCount }
        println("Average batch execution (ms): ${totalMs.toDouble() / numberOfBatches} ")
        println("Features/s : ${totalFeatures / (totalMs / 1000.0)} ")
    }

    private fun featureCopy(feature: NakshaFeature): NakshaFeature {
        val copyF = feature.copy<NakshaFeature>()
        copyF.id = PlatformUtil.randomString(20)
        return copyF
    }

    data class Stats(
        val threadName: String,
        val timeMs: Long,
        val featuresCount: Int
    )

    enum class FeatureSource {
        JSON_TOPOLOGY_SMALL, JSON_TOPOLOGY, GENERATED_RANDOM
    }
}