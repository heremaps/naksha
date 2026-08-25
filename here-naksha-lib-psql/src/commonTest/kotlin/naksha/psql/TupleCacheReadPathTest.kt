package naksha.psql

import naksha.base.TupleNumber
import naksha.model.Naksha
import naksha.model.RandomFeatures
import naksha.model.request.FeatureTuple
import naksha.model.request.Write
import naksha.model.request.WriteRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * End-to-end coverage for the tuple-cache fix: a write warms the heap tier, the tuple survives a GC
 * (SoftReference, not WeakRef), and after the cache is cleared a read reloads it from storage and
 * re-warms the cache (the path that used to need the removed `PgSession` strong-ref workaround).
 */
class TupleCacheReadPathTest : PgTestBase(collection = null, catalogId = "") {

    /** Writes one feature and returns only its [TupleNumber], leaving the tuple otherwise unreferenced. */
    private fun writeAndGetTupleNumber(): TupleNumber {
        val writeOp = Write().createFeature(collection, RandomFeatures.randomFeature())
        val response = executeWrite(WriteRequest().add(writeOp))
        return assertNotNull(response.featureTupleList[0]).tupleNumber
    }

    /** Forces a full GC and waits until it reclaims a reachable object. */
    private fun forceGc() {
        val sentinel = java.lang.ref.WeakReference(Any())
        var spins = 0
        while (sentinel.get() != null && spins < 1_000) {
            System.gc()
            @Suppress("UNUSED_VARIABLE")
            val pressure = ByteArray(2 * 1024 * 1024)
            Thread.sleep(2)
            spins++
        }
    }

    @Test
    fun writeWarmedTupleSurvivesGc() {
        testWithCollection("writeWarmedTupleSurvivesGc")
        Naksha.cache.clear()

        val tn = writeAndGetTupleNumber()
        forceGc()

        val cached = Naksha.cache[tn]
        assertNotNull(cached, "a write-warmed tuple must survive GC via SoftReference (the weak-only tier dropped it)")
        assertEquals(tn, cached.tupleNumber)
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
    fun writeResponseRetainsTuplesAcrossGc() {
        testWithCollection("cacheHardRefTest")
        val writeOp = Write().createFeature(collection, RandomFeatures.randomFeature())
        val response = executeWrite(WriteRequest().add(writeOp))

        Naksha.cache.clear()
        forceGc()

        val featureTuple = assertNotNull(response.featureTupleList[0])
        assertNotNull(featureTuple.tuple, "the write response must carry the tuple by hard reference, not via the cache")
        assertNotNull(response.features[0], "the response must still convert to a feature after the cache is cleared and GC runs")
    }
}
