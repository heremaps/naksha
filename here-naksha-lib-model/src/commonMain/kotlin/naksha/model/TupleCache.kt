@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.base.AtomicRef
import naksha.base.Int64
import naksha.jbon.IDictReader
import naksha.model.request.FeatureTuple
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.jvm.JvmOverloads
import kotlin.math.min

/**
 * The tuple cache, which helps applications or libraries to implement own caches.
 *
 * It performs all the default chaining behavior in a correct way, and then just calls internal methods to do cache-local actions:
 * - [doInit] - invoked ones, when the cache is initialized, short before it is added into the [Naksha.cache].
 * - [doLoad] - when [tuples][Tuple] should be read from this cache.
 * - [doStore] - when a [Tuple] should be added to this cache.
 * - [doesContain] - to test if a [Tuple] is contained in this cache.
 * - [doLoadAllDictionaries] - load all dictionaries from persistent cache.
 * - [doStoreDictionary] - store the given dictionary in the persistent cache.
 * - [doEvictDictionary] - remove the given dictionary from the persistent cache.
 *
 * The abstract class coordinates the calls with the [next cache][nextCache] in line, so that all caches are queried, and have a chance to store tuples. It as well coordinates that needed dictionaries are stored locally, so before calling [doStore], it will find out which dictionaries are needed, query them from the source storage, and invoke [doStoreDictionary] to ensure the cache has the dictionary available.
 *
 * @since 3.0
 */
@JsExport
class TupleCache internal constructor() {

    companion object TupleCache_C {
        private val INT64_0 = Int64(0)
    }

    private val first = AtomicRef(CacheWrapper(TupleHeapCache.getInstance()))

    inner class CacheWrapper(val cache: ITupleCache) {
        val next = AtomicRef<CacheWrapper>(null)

        fun add() {
            while (true) {
                var current = first.get()
                if (current == null) {
                    if (first.compareAndSet(null, this)) return
                    // Concurrent chain modification, restart
                    continue
                }
                var next = current.next.get()
                while (next != null && next.cache.latencyInMicros < this.cache.latencyInMicros) {
                    current = next
                    next = current.next.get()
                }
                check(current != null)
                this.next.set(next)
                if (current.next.compareAndSet(next, this)) return
                // Concurrent chain modification, restart
                this.next.set(null)
            }
        }
    }

    /**
     * Read a single tuple from cache with latency zero.
     *
     * ### Note
     * This method is not recommended, because higher level caches will ignore it, it does not make sense to send a request to a remote cache for a single feature, the latency is too high, therefore it will only be answered by the in-memory cache. It is recommended to load all needed tuples at ones via [getAll].
     * @param tupleNumber the [TupleNumber] of the [Tuple] to read.
     * @return the [Tuple], if it is in the cache.
     * @see [load]
     */
    operator fun get(tupleNumber: TupleNumber): Tuple? {
        var current = first.get()
        while (current != null) {
            val cache = current.cache
            if (cache.latencyInMicros == INT64_0) {
                val tuple = cache[tupleNumber]
                if (tuple != null) return tuple
            }
            current = current.next.get()
        }
        return null
    }

    @JvmOverloads
    fun getAll(tupleNumbers: TupleNumberBinaryArray, from:Int = 0, to:Int = tupleNumbers.size): List<Tuple>
        = load(tupleNumbers.toFeatureTupleList(from, to)).toTupleList()

    /**
     * Read multiple [tuples][Tuple] from the cache; if available.
     *
     * @param featureTuples the [feature-tuple][FeatureTuple] to fill from the cache.
     * @param from the index of the first [FeatureTuple] to load from cache, defaults to `0`.
     * @param to the index of the first [FeatureTuple] **not** to load from cache, defaults to `rs.size`.
     * @param maxMicros if given, the maximum latency in microseconds; `null` when no limit applies.
     * @return the given `featureTuples`, so that the methods can be used as wrapper.
     * @since 3.0.0
     * @see [naksha.model.request.FeatureTupleList.fromByteArray]
     * @see [get]
     */
    @JvmOverloads
    fun <LIST : List<FeatureTuple?>> load(featureTuples: LIST, from:Int = 0, to:Int = featureTuples.size, maxMicros: Int64? = null): LIST {
        val end = min(featureTuples.size, to)
        if (from < 0 || from >= end) return featureTuples
        // TODO: Ask all caches
        return featureTuples
    }

    /**
     * Store a single [Tuple] in the caches. The cache eventually can decide if it really likes to store the provided tuples.
     *
     * @param tuple the [Tuple] to store in the cache.
     * @since 3.0
     */
    fun store(tuple: Tuple) {}

    /**
     * Store a couple of [Tuple] in the caches. The cache eventually can decide if it really likes to store the provided tuples.
     *
     * @param tuple the [Tuple] to store in the cache.
     * @since 3.0
     */
    fun storeAll(vararg tuple: Tuple) {}

    /**
     * Store all given [Tuple] in this tuple-storage. The storage eventually can decide if it really likes to store all or some of the provided tuples. The method should ensure that the dictionaries of the tuples it stores are as well stored in the cache dictionary for the storage.
     *
     * @param tuples the [tuple's][Tuple] to store in the cache.
     * @since 3.0
     */
    fun storeTuples(tuples: List<Tuple?>) {}

    /**
     * Store all given [Tuple] in this tuple-storage. The storage eventually can decide if it really likes to store all or some of the provided tuples. The method should ensure that the dictionaries of the tuples it stores are as well stored in the cache dictionary for the storage.
     *
     * @param tuples the [tuple's][Tuple] to store in the cache.
     * @since 3.0
     */
    fun storeFeatureTuples(tuples: List<FeatureTuple?>) {}

    /**
     * A method being invoked when a new storage was added to the [Naksha registry][Naksha].
     * @param storage the storage that was added.
     * @since 3.0.0
     */
    internal fun addStorage(storage: IStorage) {
    }

    /**
     * A method being invoked when a previously registered storage was removed from the [Naksha registry][Naksha].
     * @param storage the storage that was removed.
     * @since 3.0.0
     */
    internal fun removedStorage(storage: IStorage) {
    }

    /**
     * A method to query for a dictionary reader.
     * @param storageNumber the storage-number to query the reader for.
     * @return the [dictionary reader][IDictReader], if any is available.
     */
    @JsName("getDictReaderForStorageId")
    fun getDictReader(storageNumber: Int64): IDictReader? = null

    /**
     * A method to query for a dictionary reader.
     * @param tuple the tuple that should be decoded.
     * @return the [dictionary reader][IDictReader], if any is available.
     */
    @JsName("getDictReaderForTuple")
    fun getDictReader(tuple: Tuple): IDictReader? = null

    /**
     * A method to query for a dictionary reader.
     * @param featureTuple the tuple that should be decoded.
     * @return the [dictionary reader][IDictReader], if any is available.
     */
    @JsName("getDictReaderForFeatureTuple")
    fun getDictReader(featureTuple: FeatureTuple): IDictReader? = null

    /**
     * Removes all cache entries (clear the cache).
     * @since 3.0.0
     */
    fun clear() {}

    /**
     * Removes all cache entries for the given storage.
     * @param storage the storage for which to clear cache entries.
     * @since 3.0.0
     */
    @JsName("clearForStorage")
    fun clear(storage: IStorage) {}

//    /**
//     * The in-memory dictionary cache.
//     * @since 3.0.0
//     */
//    private val dictCache = AtomicRef<JbDictManager>(null)
//
//    private fun storeMissing(featureTuples: List<FeatureTuple?>, start:Int, end:Int) {
//        var i = start
//        while (i < end) {
//            val ft = featureTuples[i++] ?: continue
//            val tuple = ft.tuple ?: continue
//            if (ft.source === this) continue
//            if (tuple.isComplete()) performStore(tuple)
//        }
//    }
//
//    // By default, only the TupleHeadCache does support reading of single tuples!
//    operator fun get(tupleNumber: TupleNumber): Tuple? = this.nextCache?.get(tupleNumber)
//
//    final override fun getAll(featureTuples: List<FeatureTuple?>, start:Int, end:Int): List<FeatureTuple?> {
//        // Store all tuples coming from a cache before.
//        storeMissing(featureTuples, start, end)
//        doLoad(featureTuples, start, end)
//        // If there is another cache, ask it to load more.
//        val nextCache = this.nextCache
//        if (nextCache != null) {
//            nextCache.getAll(featureTuples, start, end)
//            // Ensure that whatever it returned is added into our cache as well.
//            storeMissing(featureTuples, start, end)
//        }
//        return featureTuples
//    }
//
//    final override fun set(tupleNumber: TupleNumber, tuple: Tuple) {
//        if (tuple.isComplete()) performStore(tuple)
//        nextCache?.set(tupleNumber, tuple)
//    }
//
//    final override fun store(tuple: Tuple) {
//        if (tuple.isComplete()) performStore(tuple)
//        nextCache?.store(tuple)
//    }
//
//    final override fun store(featureTuple: FeatureTuple) {
//        val tuple = featureTuple.tuple
//        if (tuple != null && tuple.isComplete()) performStore(tuple)
//        nextCache?.store(featureTuple)
//    }
//
//    final override fun storeAll(tuples: List<Tuple?>) {
//        for (tuple in tuples) {
//            if (tuple != null && tuple.isComplete()) performStore(tuple)
//        }
//        nextCache?.storeAll(tuples)
//    }
//
//    final override fun storeAll(featureTuples: List<FeatureTuple>) {
//        for (featureTuple in featureTuples) {
//            val tuple = featureTuple.tuple ?: continue
//            if (tuple.isComplete()) performStore(tuple)
//        }
//        nextCache?.storeAll(featureTuples)
//    }
//
//    override fun addedStorage(storage: IStorage) {
//        nextCache?.addedStorage(storage)
//    }
//
//    override fun removedStorage(storage: IStorage) {
//        nextCache?.removedStorage(storage)
//    }
//
//    final override operator fun contains(tupleNumber: TupleNumber): Boolean {
//        var found = doesContain(tupleNumber)
//        if (!found) found = nextCache?.contains(tupleNumber) ?: false
//        return found
//    }
//
//    private fun dictCache(): JbDictManager
//        = dictCache.get() ?: throw NakshaException(UNINITIALIZED, "The cache is not initialized yet")
//
//    final override fun getEncodingDictionary(feature: Any?, context: Any?): JbDictionary? {
//        if (context is IStorage) return context.getEncodingDictionary(feature)
//        if (feature is NakshaFeature) {
//            val xyz = feature.properties.xyz
//            val guid = xyz.guid ?: return null
//            val storage = Naksha.getStorageByNumber(guid.tupleNumber.storageNumber)
//            return storage?.getEncodingDictionary(feature, context)
//        }
//        if (feature is FeatureTuple) {
//            val storage = Naksha.getStorageByNumber(feature.tupleNumber.storageNumber)
//            return storage?.getEncodingDictionary(feature, context)
//        }
//        if (feature is Tuple) {
//            val storage = Naksha.getStorageByNumber(feature.tupleNumber.storageNumber)
//            return storage?.getEncodingDictionary(feature, context)
//        }
//        return null
//    }
//
//    /**
//     * Helper to be called to ensure that this cache is initialized.
//     * - Throws [UNINITIALIZED], if the [start] method has not yet been called.
//     * @since 3.0.0
//     */
//    protected fun ensureInitialized() {
//        if (isInitialized.get() != true) throw NakshaException(UNINITIALIZED, "Cache is not initialized yet")
//    }
//
//    /**
//     * A method internally called to store a tuple, it will find the dictionary needed by a [Tuple], and then invoke [doStore], so that the storage can track dictionary use.
//     * @param tuple the [Tuple] to store.
//     * @return the stored [Tuple], what does not need to be the same as given, if the cache merges [Tuple].
//     * @since 3.0.0
//     */
//    private fun performStore(tuple: Tuple): Tuple {
//        // TODO: We need to find out which dictionary is used, then to add the dictionary to the cache
//        //      using doStoreDictionary, and then put it into dictCache!
//        //      Note, we only need to persist dictionaries for tuples we store!
//        return doStore(tuple, null)
//    }
//
//    /**
//     * Called by [start] to bootstrap the cache, short before it is added into the [Naksha.cache]. This method can contact servers, and do all kind of preparation.
//     * - Throws [NakshaError.INITIALIZATION_FAILED], if initialization failed.
//     * @since 3.0.0
//     */
//    protected abstract fun doInit()
//
//    /**
//     * Load [tuples][Tuple] from this cache; what is available.
//     *
//     * When a tuple was loaded from this cache, this method should set the [FeatureTuple.source] to `this`.
//     * @param featureTuples the result-set.
//     * @param start the index of the first [FeatureTuple] to load from cache.
//     * @param end the index of the first [FeatureTuple] **not** to load from cache.
//     * @since 3.0.0
//     */
//    protected abstract fun doLoad(featureTuples: List<FeatureTuple?>, start:Int, end:Int)
//
//    /**
//     * Invoked to ask this cache to store this tuple.
//     *
//     * This method is only called for tuples that are [complete][Tuple.complete]. The method can perform the store asynchronously, it even should do this, if the latency is bigger than a few nanoseconds (so all, but the [TupleHeapCache] should asynchronize persistence). It is recommended to group tuples, when storing them, for example put all tuples from the same [storage][IStorage] in the same [tile][Metadata.hereTile], and order them by their [version][Tuple.version].
//     * @param tuple the [Tuple] to store in this cache.
//     * @param dictionary the [dictionary][IDict] that is needed to decode this tuple; must be stored and linked to the [tuple][Tuple].
//     * @return the stored [Tuple], normally the given parameter, but can be a merged tuple, when the cache has more data.
//     * @since 3.0.0
//     */
//    protected abstract fun doStore(tuple: Tuple, dictionary: IDict?): Tuple
//
//    /**
//     * Tests if the cache may contain a [Tuple] with the given id; this is a probabilistic gues.
//     *
//     * The method should guess, as good as possible, if the tuple with the given [TupleNumber] is in the cache. It is recommended to implement this method using some form of a [bloom filters](https://en.wikipedia.org/wiki/Bloom_filter) to make that guess.
//     *
//     * @param tupleNumber the [TupleNumber] to check for.
//     * @return _true_ if the [Tuple] is very likely contained in cache; _false_ if it is likely not in the cache.
//     * @since 3.0.0
//     */
//    protected abstract fun doesContain(tupleNumber: TupleNumber): Boolean
//
//    /**
//     * Ask the cache to persist the given dictionary.
//     * @param dict the dictionary to persist.
//     * @since 3.0.0
//     */
//    protected abstract fun doStoreDictionary(dict: JbDictionary)
//
//    /**
//     * Ask the cache to remove the given dictionary from persistent storage.
//     * @param dict the dictionary to evict.
//     * @since 3.0.0
//     */
//    protected abstract fun doEvictDictionary(dict: JbDictionary)
//
//    /**
//     * Ask the cache to load all existing persisted dictionaries.
//     * @return all persisted dictionaries.
//     */
//    protected abstract fun doLoadAllDictionaries(): List<JbDictionary>
//
//
//    override fun doInit() {}
//
//    override fun doLoad(featureTuples: List<FeatureTuple?>, start: Int, end: Int) {
//    }
//
//    override fun doStore(tuple: Tuple, dictionary: IDict?): Tuple {
//        // Do not cache incomplete or undefined tuples, they are created in the client and not yet stored.
//        if (!tuple.isComplete()) return tuple
//        val tuple_number = tuple.meta.tupleNumber
//        if (TupleNumber.HEAD == tuple_number) return tuple
//
//        var cacheLine = tuplesByStorage[tuple_number.storageNumber]
//        if (cacheLine == null) {
//            cacheLine = AtomicMap()
//            val existing = tuplesByStorage.putIfAbsent(tuple_number.storageNumber, cacheLine)
//            if (existing != null) cacheLine = existing
//        }
//        val existingRef = cacheLine[tuple_number]
//        if (existingRef != null) {
//            val existing = existingRef.deref()
//            if (existing != null) return tuple
//        }
//        cacheLine[tuple_number] = WeakRef(tuple)
//        return tuple
//    }
//
//    override fun doesContain(tupleNumber: TupleNumber): Boolean
//            = tuplesByStorage[tupleNumber.storageNumber]?.containsKey(tupleNumber) ?: false
//
//    override fun doStoreDictionary(dict: JbDictionary) {}
//
//    override fun doEvictDictionary(dict: JbDictionary) {}
//
//    override fun doLoadAllDictionaries(): List<JbDictionary> = emptyList()
//
//    override fun get(tupleNumber: TupleNumber): Tuple?
//            = tuplesByStorage[tupleNumber.storageNumber]?.get(tupleNumber)?.deref() ?: nextCache?.get(tupleNumber)
//
//    override fun addedStorage(storage: IStorage) {
//        tuplesByStorage.putIfAbsent(storage.number, AtomicMap())
//        nextCache?.removedStorage(storage)
//    }
//
//    override fun removedStorage(storage: IStorage) {
//        tuplesByStorage.remove(storage.number)
//        nextCache?.removedStorage(storage)
//    }
//
//    override fun clear() {
//        tuplesByStorage = AtomicMap()
//    }
//
//    override fun clear(storage: IStorage) {
//        tuplesByStorage.remove(storage.number)
//    }
//
//    override fun gc() {
//        for (cacheLine in tuplesByStorage.values) {
//            for (e in cacheLine) {
//                if (e.value.deref() == null) cacheLine.remove(e.key, e.value)
//            }
//        }
//    }
}



//
///**
// * The [tuple cache entry][TupleCache] to which this cache implementation is linked. This variable is late initialized by the tuple-cache, it is bound _(set)_ before the cache is used.
// * @since 3.0
// */
//var tupleCacheEntry: TupleCache
//
///**
// * The latency of the cache in microseconds (1/1,000,000'th of a second). This can be used to automatically optimise cache-ordering. There are default values, that can be used as an orientation:
// *
// * - [LATENCY_STORAGE] - default latency of a storage, 200 milliseconds
// * - [LATENCY_S3] - default latency of S3 buckets, 100 milliseconds
// * - [LATENCY_REDIS_REMOTE] - default Redis latency, considering some network latency, 10 milliseconds
// * - [LATENCY_REDIS_LOCAL] - default Redis latency when ran locally or with ultra-fast networking, 1 millisecond
// * - [LATENCY_MEMORY] - default in-memory cache latency, being 1 microsecond.
// *
// * @since 3.0.0
// */
//val cacheLatencyInMicros: Int64
//
///**
// * The next cache in the cache-list; if there is any.
// *
// * If a new cache is added, it may change this value to add itself behind this cache.
// * @since 3.0.0
// */
//var nextCache: ITupleCacheOld?
//
///**
// * Read a single tuple from cache.
// *
// * ### Note
// * This method is not recommended, because higher level caches will ignore it, it does not make sense to send a request to a remote cache for a single feature, the latency is too high, therefore it will only be answered by the in-memory cache. It is recommended to load all needed tuples at ones via [getAll].
// * @param tupleNumber the [TupleNumber] of the [Tuple] to read.
// * @return the [Tuple], if it is in the cache.
// * @see [getAll]
// */
//operator fun get(tupleNumber: TupleNumber): Tuple?
//
///**
// * Read multiple [tuples][Tuple] from the cache; if available.
// *
// * The cache will only load what it has, then it should forward the request to the [next cache][nextCache], except all [Tuple] were loaded.
// *
// * @param featureTuples the [feature-tuple][FeatureTuple] to fill from the cache.
// * @param start the index of the first [FeatureTuple] to load from cache, defaults to `0`.
// * @param end the index of the first [FeatureTuple] **not** to load from cache, defaults to `rs.size`.
// * @return the given [result-set] rs, so that the methods can be used as wrapper.
// * @since 3.0.0
// * @see [naksha.model.request.FeatureTupleList.fromByteArray]
// * @see [get]
// */
//fun getAll(featureTuples: List<FeatureTuple?>, start:Int = 0, end:Int = featureTuples.size): List<FeatureTuple?>
//
///**
// * Store or update a cached tuple.
// *
// * ### Note
// * [Tuple] are immutable, except for [nextVersion][Metadata.nextVersion]. This is a mutable property, but with totally no significance in the cache. Still, because this property changes for _HEAD_ [Tuple], an update may be needed. Caches do not have to perform the update, but when they are able in some way to do it, they should do it.
// */
//operator fun set(tupleNumber: TupleNumber, tuple: Tuple)
//
///**
// * Store the given [Tuple].
// * @param tuple the [Tuple] to store in the cache.
// * @since 3.0.0
// */
//fun store(tuple: Tuple)
//
///**
// * Store the given [Tuple].
// * @param featureTuple the [FeatureTuple] to store in the cache.
// * @since 3.0.0
// */
//@JsName("storeFeatureTuple")
//fun store(featureTuple: FeatureTuple)
//
///**
// * Store all given [Tuple].
// * @param tuples the [tuple's][Tuple] to store in the cache.
// * @since 3.0.0
// */
//fun storeAll(tuples: List<Tuple?>)
//
///**
// * Store all given [Tuple].
// * @param featureTuples the [tuple's][Tuple] to store in the cache.
// * @since 3.0.0
// */
//@JsName("storeAllFeatureTuple")
//fun storeAll(featureTuples: List<FeatureTuple>)
//
///**
// * Tests if the cache may contain the [Tuple] with the given [tuple-number][TupleNumber].
// *
// * This is a probabilistic guess. The method should guess, as good as possible, if the tuple with the given [TupleNumber] is in the cache. It is recommended to implement this method using some form of a [bloom filters](https://en.wikipedia.org/wiki/Bloom_filter) to make that guess.
// *
// * @param tupleNumber the [TupleNumber] to check for.
// * @return _true_ if the [Tuple] is very likely in the cache; _false_ if it is not in the cache.
// * @since 3.0.0
// */
//operator fun contains(tupleNumber: TupleNumber): Boolean
//
///**
// * A method being invoked when a new storage was added to the [Naksha registry][Naksha].
// * @param storage the storage that was added.
// * @since 3.0.0
// */
//fun addedStorage(storage: IStorage)
//
///**
// * A method being invoked when a previously registered storage was removed from the [Naksha registry][Naksha].
// * @param storage the storage that was removed.
// * @since 3.0.0
// */
//fun removedStorage(storage: IStorage)
//
///**
// * A method to query for a dictionary reader.
// * @param storageNumber the storage-number to query the reader for.
// * @return the [dictionary reader][IDictReader], if any is available.
// */
//@JsName("getDictReaderForStorageId")
//fun getDictReader(storageNumber: Int64): IDictReader?
//
///**
// * A method to query for a dictionary reader.
// * @param tuple the tuple that should be decoded.
// * @return the [dictionary reader][IDictReader], if any is available.
// */
//@JsName("getDictReaderForTuple")
//fun getDictReader(tuple: Tuple): IDictReader?
//
///**
// * A method to query for a dictionary reader.
// * @param featureTuple the tuple that should be decoded.
// * @return the [dictionary reader][IDictReader], if any is available.
// */
//@JsName("getDictReaderForFeatureTuple")
//fun getDictReader(featureTuple: FeatureTuple): IDictReader?
//
///**
// * Removes all cache entries (clear the cache).
// * @since 3.0.0
// */
//fun clear()
//
///**
// * Removes all cache entries for the given storage.
// * @param storage the storage for which to clear cache entries.
// * @since 3.0.0
// */
//@JsName("clearForStorage")
//fun clear(storage: IStorage)
//
///**
// * Performs a garbage collection, remove all expired [Tuple] from the cache.
// *
// * The implementation decides what it exactly will do, when this method is invoked. The method should not block the current thread for too long, if the cleanup takes a long time, a dedicated background job should be started.
// *
// * @since 3.0.0
// */
//fun gc()