@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.base.Int64
import naksha.jbon.IDictReader
import naksha.model.request.FeatureTuple
import kotlin.js.JsExport
import kotlin.js.JsName

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
     * @param tupleNumber the [tuple-number][TupleNumber] of the [Tuple] to fetch.
     * @return the fetched [Tuple]; _null_ if either the method is not supported or the requested [Tuple] is not in the cache.
     * @since 3.0
     */
    fun get(tupleNumber: TupleNumber): Tuple?

    /**
     * Ask the cache to load [tuples][Tuple], if available _(the storage will only load what it has)_.
     *
     * The most simple implementation is:
     * ```kotlin
     * fun load(tupleNumbers: TupleNumberBinaryArray,
     *          start:Int, end:Int): List<Tuple>? {
     *   val ftl = tupleNumbers.toFeatureTupleList(from, to)
     *   loadFeatureTuple(ftl)
     *   return ftl.toTupleList()
     * }
     * ```
     * Note, that the disadvantage is that this may allocate a lot of memory, which might not be necessary if implemented more efficiently.
     *
     * ### Note
     * This method is expected to not take much longer than [latencyInMicros].
     * @param tupleNumbers the [tuple-number's][TupleNumber] to load.
     * @param from the index of the first [Tuple] to load, defaults to `0`.
     * @param to the index of the first [Tuple] **not** to load, defaults to `featureTuples.size`.
     * @return a list with all loaded tuple; an empty list or _null_ if no tuple was loaded.
     * @since 3.0
     */
    fun load(tupleNumbers: TupleNumberBinaryArray, from:Int = 0, to:Int = tupleNumbers.size): List<Tuple>?

    /**
     * Ask the cache to load [tuples][Tuple], if available _(the storage will only load what it has)_.
     *
     * ### Note
     * This method is expected to not take much longer than [latencyInMicros].
     * @param featureTuples the [feature-tuple][FeatureTuple] to fill.
     * @param from the index of the first [FeatureTuple] to load into, defaults to `0`.
     * @param to the index of the first [FeatureTuple] **not** to load into, defaults to `featureTuples.size`.
     * @return the number of tuples that have been loaded.
     * @since 3.0
     */
    fun loadFeatureTuple(featureTuples: List<FeatureTuple?>, from:Int = 0, to:Int = featureTuples.size): Int

    /**
     * Store a single [Tuple] in this tuple-storage. The storage eventually can decide if it really likes to store the provided tuples.
     *
     * ### Note
     * This method is only invoked, if the [latency][latencyInMicros] is zero.
     * @param tuple the [Tuple] to store in the cache.
     * @since 3.0
     */
    fun put(tuple: Tuple)

    /**
     * Store all given [Tuple] in this tuple-storage. The storage eventually can decide if it really likes to store all or some of the provided tuples. The method should ensure that the dictionaries of the tuples it stores are as well stored in the cache dictionary for the storage.
     *
     * ### Note
     * This method can block the callee for a longer time, it only called as part of a background job.
     * @param tuples the [tuple's][Tuple] to store in the cache.
     * @since 3.0
     */
    fun store(tuples: List<Tuple>)

    /**
     * A method being invoked before a new storage is going to be added to the [Naksha registry][Naksha].
     *
     * ### Note
     * This method is expected to not take much longer than [latencyInMicros].
     * @param storage the storage that will be added, when this method returns.
     * @since 3.0
     */
    fun onStorageAdd(storage: IStorage)

    /**
     * A method being invoked short before a previously registered storage is going to be removed from the [Naksha registry][Naksha].
     *
     * ### Note
     * This method is expected to not take much longer than [latencyInMicros].
     * @param storage the storage that is going to be removed, when this method returns.
     * @since 3.0
     */
    fun onStorageRemove(storage: IStorage)

    /**
     * A method to query for a dictionary reader for all cache entries of a certain storage.
     *
     * ### Note
     * This method is expected to not take much longer than [latencyInMicros].
     * @param storageNumber the storage-number of the storage from which to query dictionaries.
     * @return the [dictionary reader][IDictReader] for the requested storage, if any is available.
     */
    fun getDictReader(storageNumber: Int64): IDictReader?

    /**
     * Removes all cache entries (clear the cache).
     *
     * ### Note
     * This method can block the callee for a longer time, it only called as part of a background job.
     * @since 3.0
     */
    fun clear()

    /**
     * Removes all cache entries for the given storage.
     *
     * ### Note
     * This method can block the callee for a longer time, it only called as part of a background job.
     * @param storage the storage for which to clear cache entries.
     * @since 3.0
     */
    @JsName("clearForStorage")
    fun clear(storage: IStorage)

    /**
     * Performs a garbage collection, remove all expired [Tuple] from the cache.
     *
     * ### Note
     * This method can block the callee for a longer time, it only called as part of a background job.
     * @since 3.0
     */
    fun gc()
}