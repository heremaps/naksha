package naksha.psql

import naksha.model.Naksha
import naksha.model.RandomFeatures
import naksha.model.request.Write
import naksha.model.request.WriteRequest
import java.lang.ref.WeakReference
import kotlin.test.Test
import kotlin.test.assertNotNull

/** JVM-specific GC pressure verifies response tuples remain strongly referenced independently of the cache. */
class TupleCacheGcReadPathTest : PgTestBase(collection = null, catalogId = "") {
  private fun forceGc() {
    val sentinel = WeakReference(Any())
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
