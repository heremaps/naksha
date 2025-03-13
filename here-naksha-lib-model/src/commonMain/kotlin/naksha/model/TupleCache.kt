@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.base.AtomicBool
import naksha.base.AtomicInt64
import naksha.base.AtomicRef
import naksha.base.Int64
import naksha.base.fn.Fn1
import naksha.jbon.IDictReader
import naksha.model.request.FeatureTuple
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.jvm.JvmOverloads
import kotlin.math.min

/**
 * The standard tuple cache attached to [Naksha].
 *
 * @since 3.0
 * @see [Naksha.cache]
 */
@JsExport
class TupleCache internal constructor() {

    private val first = AtomicRef(CacheWrapper(TupleHeapCache.getInstance()))

    inner class CacheWrapper(val cache: ITupleCache) {
        val next = AtomicRef<CacheWrapper>(null)
    }

    /**
     * The default maximum amount of microseconds allowed for [load] or [getAll], defaults to `9223372036854775807`.
     *
     * ### Note
     * If set to a negative value, no cache will be queried when [load] or [getAll] are invoked!
     * @since 3.0
     * @see [load]
     * @see [getAll]
     */
    val maxLoadMicros = AtomicInt64(9223372036854775807)

    /**
     * If for cache misses in [load] or [getAll] the storage should be queried to load the [Tuple] into the cache, defaults to `false`.
     * @since 3.0
     * @see [load]
     * @see [getAll]
     */
    var autoLoad = AtomicBool(true)

    /**
     * Adds the given cache.
     * @param cache the cache to add.
     * @return `true` if the cache was added; `false` if it is already added.
     * @since 3.0
     */
    fun addCache(cache: ITupleCache): Boolean {
        val wrapper = CacheWrapper(cache)
        val latency = cache.latencyInMicros
        while (true) {
            var current = first.get()
            if (current == null) {
                if (first.compareAndSet(null, wrapper)) return true
                // Concurrent chain modification, restart
                continue
            }
            if (current.cache === cache) return false
            var next = current.next.get()
            while (next != null) {
                if (next.cache === cache) return false
                if (next.cache.latencyInMicros > latency) break
                current = next
                next = current.next.get()
            }
            check(current != null)
            wrapper.next.set(next)
            if (current.next.compareAndSet(next, wrapper)) return true
            // Concurrent chain modification, restart
            wrapper.next.set(null)
        }
    }

    /**
     * Removes the given cache.
     * @param cache the cache to remove.
     * @return `true` if the cache was removed; `false` if it is not part of the caching.
     * @since 3.0
     */
    fun removeCache(cache: ITupleCache): Boolean {
        TODO("Implement me")
    }

    /**
     * Invoke the given lambda for every cache to perform an action.
     * @param f the function to call for each [cache][ITupleCache].
     * @return the first none `null` value returned by `f`, or `null`, if `f` always returned `null` for all [caches][ITupleCache].
     * @since 3.0
     */
    private fun <V> forEachCache(f: Fn1<V?, ITupleCache>): V? {
        var current = first.get()
        while (current != null) {
            val cache = current.cache
            val v = f.call(cache)
            if (v != null) return v
            current = current.next.get()
        }
        return null
    }

    /**
     * Read a single tuple from cache with zero latency.
     *
     * @param tupleNumber the [TupleNumber] of the [Tuple] to read.
     * @return the [Tuple], if it is in the cache, `null` otherwise.
     * @since 3.0
     * @see [getAll]
     * @see [load]
     */
    operator fun get(tupleNumber: TupleNumber): Tuple? {
        val ZERO = Int64(0)
        return forEachCache { if (it.latencyInMicros == ZERO) it[tupleNumber] else null }
    }

    /**
     * Read multiple [tuples][Tuple] from the cache; if available.
     *
     * @param tupleNumbers the [tuple-numbers][TupleNumberBinaryArray] to load from the cache.
     * @param from the index of the first [TupleNumber] to load from cache, defaults to `0`.
     * @param to the index of the first [TupleNumber] **not** to load from cache, defaults to `tupleNumbers.size`.
     * @param maxMicros if given, the maximum latency in microseconds; defaults to [maxLoadMicros].
     * @param loadFromStorage if explicitly `true`, missing tuples are loaded from the corresponding storage, defaults to [autoLoad].
     * @return the loaded [tuple's][Tuple]
     * @since 3.0
     * @see [load]
     * @see [maxLoadMicros]
     */
    @JvmOverloads
    fun getAll(
        tupleNumbers: TupleNumberBinaryArray,
        from:Int = 0,
        to:Int = tupleNumbers.size,
        maxMicros: Int64? = null,
        loadFromStorage: Boolean? = null
    ): List<Tuple> = load(tupleNumbers.toFeatureTupleList(from, to), maxMicros = maxMicros, loadFromStorage = loadFromStorage).toTupleList()

    /**
     * Read multiple [tuples][Tuple] from the cache; if available.
     *
     * @param featureTuples the [feature-tuple][FeatureTuple] to fill from the cache.
     * @param from the index of the first [FeatureTuple] to load from cache, defaults to `0`.
     * @param to the index of the first [FeatureTuple] **not** to load from cache, defaults to `featureTuples.size`.
     * @param maxMicros if given, the maximum latency in microseconds; defaults to [maxLoadMicros].
     * @param loadFromStorage if explicitly `true`, missing tuples are loaded from the corresponding storage, defaults to [autoLoad].
     * @param acceptFeature if `true`, then the [Tuple] will only be loaded, when [FeatureTuple.feature] is as well `null`.
     * @return the given `featureTuples`, so that the methods can be used as wrapper.
     * @since 3.0
     * @see [getAll]
     * @see [maxLoadMicros]
     */
    @JvmOverloads
    fun <LIST : List<FeatureTuple?>> load(
        featureTuples: LIST,
        from:Int = 0,
        to:Int = featureTuples.size,
        maxMicros: Int64? = null,
        loadFromStorage: Boolean? = null,
        acceptFeature: Boolean = false
    ): LIST {
        val MAX = maxMicros ?: maxLoadMicros.get()
        val end = min(featureTuples.size, to)
        if (from < 0 || from >= end) return featureTuples
        forEachCache { if (it.latencyInMicros <= MAX) it.load(featureTuples, from, end, acceptFeature) else null }
        val AUTO_LOAD = loadFromStorage ?: autoLoad.get()
        if (AUTO_LOAD) {
            val byStorage = featureTuples.filter {
                if (acceptFeature)
                    it != null && it.tuple == null && it.feature == null
                else
                    it != null && it.tuple == null
            }.filterNotNull().groupBy { it.tupleNumber.storageNumber }
            for (entry in byStorage) {
                val storageNumber = entry.key
                val toLoad = entry.value
                val storage = Naksha.getStorageByNumber(storageNumber) ?: continue
                storage.newReadSession().use { session ->
                    session.loadTuples(toLoad)
                }
            }
        }
        return featureTuples
    }

    /**
     * Store a single [Tuple] in the caches. The cache eventually can decide if it really likes to store the provided tuples.
     *
     * @param tuple the [Tuple] to store in the cache.
     * @since 3.0
     */
    fun store(tuple: Tuple) {
        forEachCache { if (it.latencyInMicros eq 0 && tuple.complete) it.put(tuple) }
    }

    /**
     * Store a couple of [Tuple] in the caches. The cache eventually can decide if it really likes to store the provided tuples.
     *
     * @param tuples the [Tuple] to store in the cache.
     * @since 3.0
     */
    @JsName("storeAll")
    fun store(vararg tuples: Tuple) {
        forEachCache {
            if (it.latencyInMicros eq 0) {
                for (tuple in tuples) {
                    if (tuple.complete) it.put(tuple)
                }
            }
        }
        // TODO: For all slower caches, collect tuples and invoke `store` in a background job!
    }

    /**
     * Store all given [Tuple] in the caches. The caches eventually can decide if they really likes to store all or some of the provided tuples.
     *
     * The method will ensure that the dictionaries of the tuples are as well stored in the cache.
     *
     * @param tuples the [tuple's][Tuple] to store in the cache.
     * @since 3.0
     */
    @JsName("storeList")
    fun store(tuples: List<Tuple?>) {
        forEachCache {
            if (it.latencyInMicros eq 0) {
                for (tuple in tuples) {
                    if (tuple != null && tuple.complete) {
                        it.put(tuple)
                    }
                }
            }
        }
        // TODO: For all slower caches, collect tuples and invoke `store` in a background job!
    }

    /**
     * Store all given [Tuple] in this tuple-storage. The storage eventually can decide if it really likes to store all or some of the provided tuples. The method should ensure that the dictionaries of the tuples it stores are as well stored in the cache dictionary for the storage.
     *
     * @param featureTuples the [tuple's][Tuple] to store in the cache.
     * @since 3.0
     */
    fun storeFeatureTuples(featureTuples: List<FeatureTuple?>) {
        forEachCache {
            if (it.latencyInMicros eq 0) {
                for (f in featureTuples) {
                    val tuple = f?.tuple ?: continue
                    if (tuple.complete) it.put(tuple)
                }
            }
        }
        // TODO: For all slower caches, collect tuples and invoke `store` in a background job!
    }

    /**
     * A method being invoked when a new storage was added to the [Naksha registry][Naksha].
     * @param storage the storage that was added.
     * @since 3.0.0
     */
    internal fun addStorage(storage: IStorage) {
        forEachCache {
            it.onStorageAdd(storage)
        }
    }

    /**
     * A method being invoked when a previously registered storage was removed from the [Naksha registry][Naksha].
     * @param storage the storage that was removed.
     * @since 3.0.0
     */
    internal fun removedStorage(storage: IStorage) {
        forEachCache {
            it.onStorageRemove(storage)
        }
    }

    /**
     * A method to query for a dictionary reader.
     * @param storageNumber the storage-number to query the reader for.
     * @return the [dictionary reader][IDictReader], if any is available.
     */
    @JsName("getDictReaderForStorageId")
    fun getDictReader(storageNumber: Int64): IDictReader?
        = forEachCache { it.getDictReader(storageNumber) }

    /**
     * A method to query for a dictionary reader.
     * @param tuple the tuple that should be decoded.
     * @return the [dictionary reader][IDictReader], if any is available.
     */
    @JsName("getDictReaderForTuple")
    fun getDictReader(tuple: Tuple): IDictReader?
        = getDictReader(tuple.tupleNumber.storageNumber)

    /**
     * A method to query for a dictionary reader.
     * @param featureTuple the tuple that should be decoded.
     * @return the [dictionary reader][IDictReader], if any is available.
     */
    @JsName("getDictReaderForFeatureTuple")
    fun getDictReader(featureTuple: FeatureTuple): IDictReader? {
        val tuple = featureTuple.tuple ?: return null
        return getDictReader(tuple.tupleNumber.storageNumber)
    }

    /**
     * Removes all cache entries (clear the cache).
     * @since 3.0.0
     */
    fun clear() {
        forEachCache { it.clear() }
    }

    /**
     * Removes all cache entries for the given storage.
     * @param storage the storage for which to clear cache entries.
     * @since 3.0.0
     */
    @JsName("clearForStorage")
    fun clear(storage: IStorage) {
        forEachCache { it.clear(storage) }
    }
}