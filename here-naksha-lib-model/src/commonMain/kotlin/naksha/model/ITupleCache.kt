@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.base.Int64
import naksha.jbon.IDictManager
import naksha.model.request.FeatureTuple
import kotlin.js.JsExport

/**
 * The interface to a [Tuple] cache. Caches do only store complete tuple.
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
     * @see [naksha.model.request.FeatureTupleList.fromByteArray]
     * @see [get]
     */
    fun load(rs: List<FeatureTuple?>, start:Int = 0, end:Int = rs.size): List<FeatureTuple?>

    /**
     * Store the given [Tuple].
     * @param tuple the [Tuple] to store in the cache.
     * @return the given [Tuple].
     * @since 3.0.0
     */
    fun store(tuple: Tuple): Tuple {
        set(tuple.tupleNumber, tuple)
        return tuple
    }

    /**
     * Read a single tuple from cache.
     *
     * ### Note
     * This method is not recommended, because higher level caches will ignore it, it does not make sense to send a request to a remote cache for a single feature, the latency is too high, therefore it will only be answered by the in-memory cache.
     * @param tupleNumber the [TupleNumber] of the [Tuple] to read.
     * @return the [Tuple], if it is in the cache.
     * @see [load]
     */
    operator fun get(tupleNumber: TupleNumber): Tuple?

    /**
     * Store or update a cached tuple.
     *
     * ### Note
     * [Tuple] are immutable, except for [nextVersion][Metadata.nextVersion]. This is a mutable property, but with totally no significance in the cache. Still, because this property changes for _HEAD_ [Tuple], an update may be needed. Caches do not have to perform the update, but when they are able in some way to do it, they should do it.
     */
    operator fun set(tupleNumber: TupleNumber, tuple: Tuple)

    /**
     * Tests if the cache may contain the [Tuple] with the given [tuple-number][TupleNumber].
     *
     * This is a probabilistic guess. The method should guess, as good as possible, if the tuple with the given [TupleNumber] is in the cache. It is recommended to implement this method using some form of a [bloom filters](https://en.wikipedia.org/wiki/Bloom_filter) to make that guess.
     *
     * @param tupleNumber the [TupleNumber] to check for.
     * @return _true_ if the [Tuple] is very likely in the cache; _false_ if it is not in the cache.
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