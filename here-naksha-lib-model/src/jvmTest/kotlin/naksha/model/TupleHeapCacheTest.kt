package naksha.model

import naksha.base.Int64
import naksha.base.TupleNumber
import naksha.base.Version
import naksha.jbon.BookType
import naksha.jbon.HeapBook
import naksha.model.objects.StandardMembers
import naksha.model.request.FeatureTuple
import org.junit.jupiter.api.Test
import java.lang.ref.WeakReference
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * Regression test 
 */
class TupleHeapCacheTest {

    /** A distinct [TupleNumber]; [featureNumber] and [storage] vary between tuples in these tests. */
    private fun tupleNumber(featureNumber: Long, storage: Long = 1): TupleNumber =
        TupleNumber(
            Int64(storage),         // storage (databaseNumber)
            0,                      // catalogNumber
            0,                      // collectionNumber
            Int64(featureNumber),
            Version(1).number
        )

    /** Builds a minimal [Tuple] for [tn] and stores it in [cache]. */
    private fun putTuple(cache: TupleHeapCache, tn: TupleNumber): Tuple {
        val members = HeapBook(BookType.MEMBER_BOOK)
        members.put(StandardMembers.TN, tn) // Tuple.tupleNumber reads membersBook[TN]
        val tuple = Tuple(featureBytes = ByteArray(256), membersBook = members)
        cache.put(tuple)
        return tuple
    }

    /**
     * Forces a full GC and waits until it reclaims a reachable object.
     */
    private fun forceGc() {
        val sentinel = WeakReference(Any())
        var spins = 0
        while (sentinel.get() != null && spins < 1_000) {
            System.gc()
            @Suppress("UNUSED_VARIABLE")
            val pressure = ByteArray(2 * 1024 * 1024) // garbage to have something to collect
            Thread.sleep(2)
            spins++
        }
    }

    @Test
    fun heapCacheReturnsStoredTupleWhileStronglyReachable() {
        val cache = TupleHeapCache.getInstance()
        cache.clear()

        val tn = tupleNumber(featureNumber = 7)
        val members = HeapBook(BookType.MEMBER_BOOK)
        members.put(StandardMembers.TN, tn)
        val tuple = Tuple(featureBytes = ByteArray(256), membersBook = members)
        cache.put(tuple)
        forceGc()

        assertSame(
            tuple,
            cache.get(tn),
            "cache did not return the stored tuple instance that is still strongly reachable — store/lookup is miswired"
        )
    }

    @Test
    fun returnsManyStronglyReachableTuplesAcrossStorages() {
        val cache = TupleHeapCache.getInstance()
        cache.clear()

        val held = ArrayList<Tuple>()
        val tns = ArrayList<TupleNumber>()
        for (i in 0 until 50) {
            val storage = if (i % 2 == 0) 1L else 2L
            val tn = tupleNumber(featureNumber = i.toLong(), storage = storage)
            held.add(putTuple(cache, tn))
            tns.add(tn)
        }

        for (tn in tns) {
            assertNotNull(cache.get(tn), "cache must return a stored, strongly-reachable tuple $tn")
        }
        assertEquals(50, held.size)
    }

    @Test
    fun loadFillsFeatureTupleFromCacheAndRecordsSource() {
        val cache = TupleHeapCache.getInstance()
        cache.clear()

        val tn = tupleNumber(featureNumber = 99)
        val featureTuple = FeatureTuple(tn) // cache is empty, so tuple stays null
        assertNull(featureTuple.tuple, "precondition: nothing cached for this tuple yet")

        putTuple(cache, tn)
        val loaded = cache.load(listOf(featureTuple))

        assertEquals(1, loaded, "load must report one filled feature-tuple")
        assertNotNull(featureTuple.tuple, "load must fill the tuple from the cache")
        assertSame(cache, featureTuple.source, "load must record the serving cache as the source")
    }

    @Test
    fun gcKeepsStronglyReachableTuples() {
        val cache = TupleHeapCache.getInstance()
        cache.clear()

        val held = ArrayList<Tuple>()
        val tns = ArrayList<TupleNumber>()
        for (i in 0 until 10) {
            val tn = tupleNumber(featureNumber = 1000 + i.toLong())
            held.add(putTuple(cache, tn)) // keep a strong reference
            tns.add(tn)
        }
        cache.gc()

        for (tn in tns) {
            assertNotNull(cache.get(tn), "gc() must not drop a strongly-reachable tuple $tn")
        }
        assertEquals(10, held.size)
    }
}
