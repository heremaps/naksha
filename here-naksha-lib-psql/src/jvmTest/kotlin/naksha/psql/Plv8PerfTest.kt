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
import naksha.psql.Plv8PerfTest.FeatureSource.*
import naksha.model.RandomFeatures.RandomFeatures_C.randomFeatures
import naksha.model.objects.NakshaCollection.NakshaCollection_C.GIST_2D_IDX
import naksha.model.objects.NakshaCollection.NakshaCollection_C.HERE_TILE_IDX
import naksha.model.objects.NakshaCollection.NakshaCollection_C.ID_IDX
import naksha.model.objects.NakshaCollection.NakshaCollection_C.TAGS_IDX
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OutputTimeUnit
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertIs

@Suppress("HasPlatformType", "MayBeConstant")
@BenchmarkMode(Mode.AverageTime) // Measures average execution time
@OutputTimeUnit(TimeUnit.MILLISECONDS)
class Plv8PerfTest : PgTestBase(
    NakshaCollection(
        id = "",
        partitions = NUM_OF_PARTITIONS,
        storeHistory = StoreMode.ON,
        storeDeleted = StoreMode.ON
    ).withIndices(ID_IDX, GIST_2D_IDX, TAGS_IDX, HERE_TILE_IDX)//.withIndices(ID_IDX)
) {
    companion object {
        val featureSource = JSON_TOPOLOGY_SMALL
        val NUM_OF_PARTITIONS = 4
        val OVERLOAD_FACTOR = 4
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

    //@Ignore
    @Test
    fun shouldInsertManyFeatures() {
        // Prepare
        val batchRequests = mutableListOf<WriteRequest>()
        for (i in 1..numberOfBatches) {
            val featuresInBatch = generateFeatures(featureSource, FEATURES_PER_BATCH)
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

    //@Ignore
    @Test
    fun shouldUpsertManyFeatures() {
        // Prepare
        val batchRequests = mutableListOf<WriteRequest>()
        for (i in 1..numberOfBatches) {
            val featuresInBatch = generateFeatures(featureSource, FEATURES_PER_BATCH)

            val writeFeaturesReq = WriteRequest().apply {
                featuresInBatch.forEach { featureToCreate ->
                    add(Write().upsertFeature(collection, featureToCreate))
                }
            }
            batchRequests.add(writeFeaturesReq)
        }

        // Execute
        println("Starting $numberOfBatches batches, $FEATURES_PER_BATCH features each, on $concurrency threads.")
        executeParallel(concurrency, batchRequests)
    }

    //@Ignore
    @Test
    fun shouldInsertGroupedByPartition() {
        val numberOfBatchesPerPartition = numberOfBatches / NUM_OF_PARTITIONS

        // Prepare
        val allFeatures = generateFeatures(featureSource, FEATURES_PER_BATCH * numberOfBatches)
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
        println("Starting $numberOfBatches batches, $FEATURES_PER_BATCH features each, on $concurrency threads.")
        executeParallel(concurrency, batchRequests)
    }

// TODO: Fix the test, as it does not group correctly!
//    fun groupByPartition(features: List<NakshaFeature>): Map<Int, List<NakshaFeature>> {
//        val group = mutableMapOf<Int, List<NakshaFeature>>()
//    }

    //@Ignore
    @Test
    fun shouldUpsertGroupedByPartition() {
        val numberOfBatchesPerPartition = numberOfBatches / NUM_OF_PARTITIONS

        // Prepare
        val allFeatures = generateFeatures(featureSource, FEATURES_PER_BATCH * numberOfBatches)
        allFeatures[0].id = "A7l9RsIxWZCp2I6i3wXo" // fn=-6293233423437375615, pn=22401
        allFeatures[1].id = "A6ixOLtAZF8IhKez25zY" // fn=-318328739946057960, pn=44824
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
        println("Starting $numberOfBatches batches, $FEATURES_PER_BATCH features each, on $concurrency threads.")
        executeParallel(concurrency, batchRequests)
    }

    private fun generateFeatures(source: FeatureSource, numberOfFeatures: Int): List<NakshaFeature> {
        return when (source) {
            GENERATED_RANDOM -> randomFeatures(count = numberOfFeatures)
            JSON_TOPOLOGY -> List(numberOfFeatures) { featureCopy(topologyFeatureTemplate) }
            JSON_TOPOLOGY_SMALL -> List(numberOfFeatures) { featureCopy(smallTopologyFeatureTemplate) }
        }
    }

    private fun executeParallel(concurrency: Int, batchRequests: List<WriteRequest>) = runBlocking {
        val stats = Collections.synchronizedList(mutableListOf<Stats>())
        val threadPool = Executors.newFixedThreadPool(concurrency)
        val context = NakshaContext.currentContext()
        val start = System.nanoTime()
        val tasks = batchRequests.map { batchRequest ->
            threadPool.submit {
                context.attachToCurrentThread()
                val innerStart = System.currentTimeMillis()
                val response = executeWrite(batchRequest)
                assertIs<SuccessResponse>(response)
                val innerEnd = System.currentTimeMillis()
                stats.add(Stats(Thread.currentThread().name, innerEnd - innerStart, batchRequest.writes.size))
            }
        }
        tasks.forEach { it.get() }
        val end = System.nanoTime()
        threadPool.shutdown()
        val totalMs = stats.sumOf { it.timeMs }
        val totalSeconds = (end.toDouble() - start.toDouble()) / 1e9
        val numberOfBatches = batchRequests.count()
        val totalFeatures = stats.sumOf { it.featuresCount }
        println("Total features: $totalFeatures, total runtime in seconds: $totalSeconds")
        println("Average batch execution (ms): ${totalMs / numberOfBatches} ")
        println("Features/s : ${totalFeatures / totalSeconds} ")
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