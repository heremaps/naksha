@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.base.Int64
import naksha.jbon.IDictManager
import naksha.model.request.FeatureTuple
import kotlin.js.JsExport

/**
 * The interface to a [Tuple] cache.
 *
 * Every cache needs to keep track of needed global dictionaries.
 * @since 3.0.0
 */
@JsExport
interface ITupleCache : IDictManager {

    /**
     * The latency of the cache in microseconds (1/1,000,000'th of a second). This can be used to automatically optimise cache-ordering. There are default values, that can be used as an orientation:
     *
     * - [LATENCY_STORAGE] - default latency of a storage, 200 milliseconds
     * - [LATENCY_S3] - default latency of S3 buckets, 100 milliseconds
     * - [LATENCY_REDIS_REMOTE] - default Redis latency, considering some network latency, 10 milliseconds
     * - [LATENCY_REDIS_LOCAL] - default Redis latency when ran locally or with ultra-fast networking, 1 millisecond
     * - [LATENCY_MEMORY] - default in-memory cache latency, being 1 microsecond.
     *
     * @since 3.0.0
     */
    val cacheLatencyInMicros: Int64

    /**
     * The next cache in the cache-list; if there is any.
     *
     * If a new cache is added, it may change this value to add itself behind this cache.
     * @since 3.0.0
     */
    var nextCache: ITupleCache?

    /**
     * Load [tuples][Tuple] from the cache; if available.
     *
     * The cache will only load what it has, then it should forward the request to the [next cache][nextCache], except all [Tuple] were loaded.
     *
     * @param rs the result-set.
     * @param start the index of the first [FeatureTuple] to load from cache, defaults to `0`.
     * @param end the index of the first [FeatureTuple] **not** to load from cache, defaults to `rs.size`.
     * @return the given [result-set] rs, so that the methods can be used as wrapper.
     * @since 3.0.0
     * @see [naksha.model.request.ResultTupleList.fromByteArray]
     */
    fun load(rs: List<FeatureTuple?>, start:Int = 0, end:Int = rs.size): List<FeatureTuple?>

    /**
     * Store the given [Tuple].
     *
     * This method automatically merges any [Tuple] being already in the cache with the given [Tuple]. This is necessary, because the [Tuple] being in the cache may be more complete than the new one given.
     *
     * It is recommended for custom cache implementations to not store [tuples][Tuple] that are incomplete, so that return _false_ for [Tuple.isComplete] calls. Furthermore, the cache should keep new tuples in-memory for a while, before flushing them asynchronously to the storage, so that the amount of storage requests can be minimized. It may even be good to organize [Tuple] using the [HERE tile-id][Metadata.calculateGeoGrid], and optionally sort them by their [version][Tuple.version] within the tile.
     *
     * @param tuple the [Tuple] to store in the cache.
     * @return either the existing [Tuple], the given one, or a merge [Tuple].
     * @since 3.0.0
     */
    fun store(tuple: Tuple): Tuple

    /**
     * Tests if the cache may contain a [Tuple] with the given id; this is a probabilistic gues.
     *
     * The method should guess, as good as possible, if the tuple with the given [TupleNumber] is in the cache. It is recommended to implement this method using some form of a [bloom filters](https://en.wikipedia.org/wiki/Bloom_filter) to make that guess.
     *
     * @param tupleNumber the [TupleNumber] to check for.
     * @return _true_ if the [Tuple] is very likely contained in cache; _false_ if it is likely not in the cache.
     * @since 3.0.0
     */
    operator fun contains(tupleNumber: TupleNumber): Boolean

    /**
     * A method being invoked when a new storage was added to the [Naksha registry][Naksha].
     * @param storage the storage that was added.
     * @since 3.0.0
     */
    fun addedStorage(storage: IStorage) {
        nextCache?.addedStorage(storage)
    }

    /**
     * A method being invoked when a previously registered storage was removed from the [Naksha registry][Naksha].
     * @param storage the storage that was removed.
     * @since 3.0.0
     */
    fun removedStorage(storage: IStorage) {
        nextCache?.removedStorage(storage)
    }

    /**
     * Removes all cache entries (clear the cache).
     * @since 3.0.0
     */
    fun clear()

    /**
     * Performs a garbage collection, remove all expired [Tuple] from the cache.
     *
     * The implementation decides what it exactly will do, when this method is invoked. The method should not block the current thread for too long, if the cleanup takes a long time, a dedicated background job should be started.
     *
     * @since 3.0.0
     */
    fun gc()
}