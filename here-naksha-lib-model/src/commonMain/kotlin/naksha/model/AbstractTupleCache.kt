@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.base.AtomicRef
import naksha.base.Int64
import naksha.jbon.IDict
import naksha.jbon.JbDictManager
import naksha.jbon.JbDictionary
import naksha.model.NakshaError.NakshaErrorCompanion.UNINITIALIZED
import naksha.model.objects.NakshaFeature
import naksha.model.request.FeatureTuple
import kotlin.js.JsExport

/**
 * A base implementation of a [tuple-cache][ITupleCache], which helps applications or libraries to implement own caches.
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
 * The abstract class coordinates the calls with the [next cache][nextCache] in line, so that all caches are queried, and have a chance to store tuples. It as well coordinates that needed dictionaries are stored locally, so before calling [doStore], it will find out which dictionaries are needed, query them from the source storage, and invoke [putDictionary] to ensure the cache has the dictionary available.
 *
 * @since 3.0.0
 */
@JsExport
abstract class AbstractTupleCache(override val cacheLatencyInMicros: Int64) : ITupleCache {
    override var nextCache: ITupleCache? = null

    private val isInitialized = AtomicRef(false)

    /**
     * A helper to automatically add this cache thread-safe into the [Naksha.cache] subsystem, and to initialize it. It will order this cache automatically according to its latency.
     * @since 3.0.0
     */
    fun start() {
        if (!isInitialized.compareAndSet(expectedValue = false, newValue = true)) {
            throw NakshaException(NakshaError.INITIALIZATION_FAILED, "The cache is already initialized")
        }
        doInit()
        val all = doLoadAllDictionaries()
        val dictManager = JbDictManager()
        for (dict in all) dictManager.putDictionary(dict)
        dictCache.set(dictManager)
        registerCache()
    }

    private tailrec fun registerCache() {
        Naksha.lock.acquire().use {
            val existing = Naksha.cacheRef.get()
            var first: ITupleCache = this
            var addTo: ITupleCache? = null
            var next = existing
            if (existing != null && existing.cacheLatencyInMicros <= this.cacheLatencyInMicros) {
                first = existing
                addTo = existing
                next = existing.nextCache
                while (next != null && next.cacheLatencyInMicros <= this.cacheLatencyInMicros) {
                    addTo = next
                    next = next.nextCache
                }
            }
            this.nextCache = next
            if (Naksha.cacheRef.compareAndSet(existing, first)) {
                addTo?.nextCache = this
                return
            }
        }
        // Concurrency issue, another thread was faster, repeat this.
        registerCache()
    }

    /**
     * The in-memory dictionary cache.
     * @since 3.0.0
     */
    private val dictCache = AtomicRef<JbDictManager>(null)

    private fun storeMissing(rs: List<FeatureTuple?>, start:Int, end:Int) {
        var i = start
        while (i < end) {
            val rt = rs[i++] ?: continue
            val tuple = rt.tuple ?: continue
            if (rt.source === this) continue
            if (allowIncompleteTuple || tuple.isComplete()) performStore(tuple)
        }
    }

    final override fun load(rs: List<FeatureTuple?>, start:Int, end:Int): List<FeatureTuple?> {
        // Store all tuples coming from a cache before.
        storeMissing(rs, start, end)
        doLoad(rs, start, end)
        // If there is another cache, ask it to load more.
        val nextCache = this.nextCache
        if (nextCache != null) {
            nextCache.load(rs, start, end)
            // Ensure that whatever it returned is added into our cache as well.
            storeMissing(rs, start, end)
        }
        return rs
    }

    final override fun set(tupleNumber: TupleNumber, tuple: Tuple) {
        store(tuple)
    }

    final override fun store(tuple: Tuple): Tuple {
        if (tuple.isComplete() || allowIncompleteTuple) performStore(tuple)
        return nextCache?.store(tuple) ?: tuple
    }

    final override operator fun contains(tupleNumber: TupleNumber): Boolean {
        var found = doesContain(tupleNumber)
        if (!found) found = nextCache?.contains(tupleNumber) ?: false
        return found
    }

    private fun dictCache(): JbDictManager
        = dictCache.get() ?: throw NakshaException(UNINITIALIZED, "The cache is not initialized yet")

    final override fun getDictionary(id: String): JbDictionary? {
        val dict = dictCache().getDictionary(id)
        return dict ?: nextCache?.getDictionary(id)
    }

    final override fun deleteDictionary(dict: JbDictionary): Boolean {
        val deletedFormOther = nextCache?.deleteDictionary(dict) ?: false
        val deletedFromThis = dictCache().deleteDictionary(dict)
        return deletedFromThis || deletedFormOther
    }

    final override fun putDictionary(dict: JbDictionary) {
        nextCache?.putDictionary(dict)
        dictCache().putDictionary(dict)
    }

    final override fun getEncodingDictionary(feature: Any?, context: Any?): JbDictionary? {
        // This need to always use the storage, we rather should not use any dictionary, than the wrong one!
        if (context is IStorage) return context.getEncodingDictionary(feature)
        val f = if (feature is NakshaFeature) feature else return null
        val xyz = f.properties.xyz
        val guid = xyz.guid ?: return null
        val storage = Naksha.getStorageByNumber(guid.tupleNumber.storageNumber)
        return storage?.getEncodingDictionary(feature, context)
    }

    /**
     * If the cache wants to accept [incomplete tuple][Tuple.complete], normally this only makes sense in the [TupleHeapCache].
     * @since 3.0.0
     */
    protected open val allowIncompleteTuple: Boolean
        get() = false

    /**
     * Helper to be called to ensure that this cache is initialized.
     * - Throws [UNINITIALIZED], if the [start] method has not yet been called.
     * @since 3.0.0
     */
    protected fun ensureInitialized() {
        if (isInitialized.get() != true) throw NakshaException(UNINITIALIZED, "Cache is not initialized yet")
    }

    /**
     * A method internally called to store a tuple, it will find the dictionary needed by a [Tuple], and then invoke [doStore], so that the storage can track dictionary use.
     * @param tuple the [Tuple] to store.
     * @return the stored [Tuple], what does not need to be the same as given, if the cache merges [Tuple].
     * @since 3.0.0
     */
    protected open fun performStore(tuple: Tuple): Tuple {
        // TODO: We need to find out which dictionary is used, then to add the dictionary to the cache
        //      using doStoreDictionary, and then put it into dictCache!
        //      Note, we only need to persist dictionaries for tuples we store!
        return doStore(tuple, null)
    }

    /**
     * Called by [start] to bootstrap the cache, short before it is added into the [Naksha.cache]. This method can contact servers, and do all kind of preparation.
     * - Throws [NakshaError.INITIALIZATION_FAILED], if initialization failed.
     * @since 3.0.0
     */
    protected abstract fun doInit()

    /**
     * Load [tuples][Tuple] from this cache; what is available.
     *
     * When a tuple was loaded from this cache, this method should set the [FeatureTuple.source] to `this`.
     * @param rs the result-set.
     * @param start the index of the first [FeatureTuple] to load from cache.
     * @param end the index of the first [FeatureTuple] **not** to load from cache.
     * @since 3.0.0
     */
    protected abstract fun doLoad(rs: List<FeatureTuple?>, start:Int, end:Int)

    /**
     * Invoked to ask this cache to store this tuple.
     *
     * This method is only called for tuples that are [complete][Tuple.complete], except [allowIncompleteTuple] is `true`. The method can perform the store asynchronously, it even should do this, if the latency is bigger than a few nanoseconds (so all, but the [TupleHeapCache] should asynchronize persistence). It is recommended to group tuples, when storing them, for example put all tuples from the same [storage][IStorage] in the same [tile][Metadata.hereTile], and order them by their [version][Tuple.version].
     * @param tuple the [Tuple] to store in this cache.
     * @param dictionary the [dictionary][IDict] that is needed to decode this tuple; must be stored and linked to the [tuple][Tuple].
     * @return the stored [Tuple], normally the given parameter, but can be a merged tuple, when the cache has more data.
     * @since 3.0.0
     */
    protected abstract fun doStore(tuple: Tuple, dictionary: IDict?): Tuple

    /**
     * Tests if the cache may contain a [Tuple] with the given id; this is a probabilistic gues.
     *
     * The method should guess, as good as possible, if the tuple with the given [TupleNumber] is in the cache. It is recommended to implement this method using some form of a [bloom filters](https://en.wikipedia.org/wiki/Bloom_filter) to make that guess.
     *
     * @param tupleNumber the [TupleNumber] to check for.
     * @return _true_ if the [Tuple] is very likely contained in cache; _false_ if it is likely not in the cache.
     * @since 3.0.0
     */
    protected abstract fun doesContain(tupleNumber: TupleNumber): Boolean

    /**
     * Ask the cache to persist the given dictionary.
     * @param dict the dictionary to persist.
     * @since 3.0.0
     */
    protected abstract fun doStoreDictionary(dict: JbDictionary)

    /**
     * Ask the cache to remove the given dictionary from persistent storage.
     * @param dict the dictionary to evict.
     * @since 3.0.0
     */
    protected abstract fun doEvictDictionary(dict: JbDictionary)

    /**
     * Ask the cache to load all existing persisted dictionaries.
     * @return all persisted dictionaries.
     */
    protected abstract fun doLoadAllDictionaries(): List<JbDictionary>
}