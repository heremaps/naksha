package naksha.psql

import naksha.base.TupleNumber
import naksha.model.Naksha
import naksha.model.RandomFeatures
import naksha.model.request.FeatureTuple
import naksha.model.request.Write
import naksha.model.request.WriteRequest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * End-to-end coverage for the tuple-cache fix: after the cache is cleared a read reloads the tuple from storage and re-warms the cache, and a write response carries its tuples by hard reference independent of the cache. JVM-specific garbage-collection coverage lives in TupleCacheGcReadPathTest.
 */
class TupleCacheReadPathTest : PgTestBase(collection = null, catalogId = "") {

    /** Writes one feature and returns only its [TupleNumber], leaving the tuple otherwise unreferenced. */
    private fun writeAndGetTupleNumber(): TupleNumber {
        val writeOp = Write().createFeature(collection, RandomFeatures.randomFeature())
        val response = executeWrite(WriteRequest().add(writeOp))
        return assertNotNull(response.featureTupleList[0]).tupleNumber
    }

    @Test
    fun readAfterCacheClearReloadsFromStorageAndRewarms() {
        testWithCollection("cacheReloadRewarm")

        val tn = writeAndGetTupleNumber()
        Naksha.cache.clear()
        assertNull(Naksha.cache[tn], "precondition: the cache was cleared")

        val featureTuples = listOf<FeatureTuple?>(FeatureTuple(tn))
        Naksha.cache.loadFromCacheOrStorage(featureTuples)

        assertNotNull(featureTuples[0]?.tuple, "a cache miss must reload the tuple from storage")
        assertNotNull(Naksha.cache[tn], "reloading from storage must re-warm the cache")
    }

    @Test
    fun writeResponseRetainsTuplesAfterCacheClear() {
        testWithCollection("cacheHardRefPortableTest")
        val writeOp = Write().createFeature(collection, RandomFeatures.randomFeature())
        val response = executeWrite(WriteRequest().add(writeOp))

        Naksha.cache.clear()

        val featureTuple = assertNotNull(response.featureTupleList[0])
        assertNotNull(featureTuple.tuple, "the write response must carry the tuple by hard reference, not via the cache")
        assertNotNull(response.features[0], "the response must still convert to a feature after the cache is cleared")
    }
}
