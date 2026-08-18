package naksha.model

import naksha.base.Int64
import naksha.base.TupleNumber
import naksha.base.Version
import naksha.jbon.BookType
import naksha.jbon.HeapBook
import naksha.model.objects.StandardMembers
import org.junit.jupiter.api.Test
import java.lang.ref.WeakReference
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Regression test 
 */
class TupleHeapCacheTest {

    /** A distinct [TupleNumber]; only [featureNumber] varies between tuples in these tests. */
    private fun tupleNumber(featureNumber: Long): TupleNumber =
        TupleNumber(
            Int64(1),               // stoarge
            0,                      // catalogNumber
            0,                      // collectionNumber
            Int64(featureNumber),
            Version(1).number
        )

    /**
     * Builds a minimal [Tuple], stores it in [cache], and returns only its [TupleNumber].
     */
    private fun storeFreshTuple(cache: TupleHeapCache, featureNumber: Long): TupleNumber {
        val tn = tupleNumber(featureNumber)
        val members = HeapBook(BookType.MEMBER_BOOK)
        members.put(StandardMembers.TN, tn) // Tuple.tupleNumber reads membersBook[TN]
        val tuple = Tuple(featureBytes = ByteArray(256), membersBook = members)
        cache.put(tuple)
        return tn
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

        assertEquals(
            tuple,
            cache.get(tn),
            "cache did not return a stored tuple that is still strongly reachable — store/lookup is miswired"
        )
    }

    @Test
    fun heapCacheRetainsStoredTupleAfterGc() {
        val cache = TupleHeapCache.getInstance()
        cache.clear()

        val tn = storeFreshTuple(cache, featureNumber = 42)
        forceGc()

        val recovered = cache.get(tn)
        assertNotNull(
            recovered,
            "TupleHeapCache dropped a stored tuple after GC: held only as WeakRef."
        )
        assertEquals(tn, recovered.tupleNumber)
    }
}
