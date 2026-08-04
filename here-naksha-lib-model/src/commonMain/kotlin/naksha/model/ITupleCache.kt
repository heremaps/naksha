@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.base.Int64
import naksha.base.TupleNumber
import kotlin.js.JsExport

/**
 * The interface to a [Tuple] cache. Note that tuple-cache does only store immutable tuples for faster loading of tuples, so they have not to be all loaded from the storage itself.
 *
 * @since 3.0
 */
@JsExport
interface ITupleCache {

    /**
     * The latency of the cache in microseconds (1/1,000,000'th of a second). This is used to automatically optimise cache-ordering. There are default values, that can be used as an orientation:
     *
     * - [LATENCY_STORAGE] - default latency of a storage, 200 milliseconds
     * - [LATENCY_S3] - default latency of S3 buckets, 100 milliseconds
     * - [LATENCY_REDIS_REMOTE] - default Redis latency, considering some network latency, 10 milliseconds
     * - [LATENCY_REDIS_LOCAL] - default Redis latency when run locally or with ultra-fast networking, 1 millisecond
     * - [LATENCY_MEMORY] - default in-memory cache latency, being 0 microsecond.
     *
     * The implementation can either use any of these defaults or it can just use real numbers by testing the network connection. The value is read every time before a decision is made to read data from the cache.
     *
     * @since 3.0
     */
    val latencyInMicros: Int64

    /**
     * Tries to read a single tuple from the cache.
     *
     * ### Note
     * This method is only invoked, if the [latency][latencyInMicros] is zero.
     * @param tupleNumber the [tuple-number][naksha.base.TupleNumber] of the [Tuple] to fetch.
     * @return the fetched [Tuple]; _null_ if either the method is not supported or the requested [Tuple] is not in the cache.
     * @since 3.0
     */
    operator fun get(tupleNumber: TupleNumber): Tuple?

    /**
     * Ask the cache to load [tuples][Tuple], if available _(the cache will only load what it has)_.
     *
     * ### Note
     * This method is expected to not take much longer than [latencyInMicros].
     * @param tuples the [Tuple] to load, caches should only fill elements in the given range and that are `null`.
     * @param tupleNumbers the [tuple-numbers][TupleNumber] of the [Tuple] to load.
     * @param from the index of the first [Tuple] to load, defaults to `0`.
     * @param to the index of the first [Tuple] **not** to load, defaults to `tuple.size`.
     * @param maxMicros if given, the maximum latency in microseconds the cache is allowed to use, can be `0` which means only in-memory access.
     * @return the number of tuples that have been loaded.
     * @since 3.0
     */
    fun load(tuples: Array<Tuple?>, tupleNumbers: Array<TupleNumber>, from:Int = 0, to:Int = tuples.size, maxMicros: Int64? = null): Int

    /**
     * Store a single [Tuple] in this tuple-storage.
     *
     * The storage eventually can decide if it really likes to store the provided tuples. The caller should ensure that the books of the tuples it stores are as well stored in the cache.
     *
     * ### Note
     * The cache must return instantly to the call, IO must be asynchronized.
     * @param tuple the [Tuple] to store in the cache.
     * @since 3.0
     */
    fun put(tuple: Tuple)

    /**
     * Store all given [Tuple] in this tuple-storage.
     *
     * The storage eventually can decide if it really likes to store all or some of the provided tuples. The caller should ensure that the books of the tuples it stores are as well stored in the cache.
     *
     * ### Note
     * The cache must return instantly to the call, IO must be asynchronized.
     * @param tuples the [tuple's][Tuple] to store in the cache.
     * @since 3.0
     */
    fun store(vararg tuples: Tuple?)

    /**
     * Removes all cache entries (clear the cache).
     *
     * ### Note
     * This method can block the callee for a longer time, it only called as part of a background job.
     * @since 3.0
     */
    fun clear()

    /**
     * Performs a garbage collection, remove all expired [Tuple] from the cache.
     *
     * ### Note
     * This method can block the callee for a longer time, it only called as part of a background job.
     * @since 3.0
     */
    fun gc()
}