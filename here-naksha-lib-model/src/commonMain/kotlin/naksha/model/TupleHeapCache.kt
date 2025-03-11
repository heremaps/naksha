@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package naksha.model

import naksha.base.Int64
import naksha.jbon.IDictReader
import naksha.model.request.FeatureTuple

/**
 * A cache in the Java heap for [Tuple]'s, used as default cache by the [Naksha.cache]. This cache is a mandatory first level cache, the application can install second or third level caches.
 *
 * Technically, no client should load [Tuple] directly from a storage.
 *
 * **It is possible to replace this as first level cache, but strongly discouraged, without providing a much better alternative first level cache!**
 *
 * If the cache should be replaced, remove it via:
 *
 * ```kotlin
 * Naksha.cache.remove(TupleHeapCache.getInstance())
 * ```
 * It can be added again using the same way, so like:
 * ```kotlin
 * Naksha.cache.add(TupleHeapCache.getInstance())
 * ```
 *
 * @since 3.0
 */
expect class TupleHeapCache: ITupleCache {

    // ITupleCache implementation
    override val latencyInMicros: Int64
    override operator fun get(tupleNumber: TupleNumber): Tuple?
    override fun load(featureTuples: List<FeatureTuple?>, from:Int, to:Int): Int
    override fun put(tuple: Tuple)
    override fun store(tuples: List<Tuple>)
    override fun onStorageAdd(storage: IStorage)
    override fun onStorageRemove(storage: IStorage)
    override fun getDictReader(storageNumber: Int64): IDictReader?
    override fun clear()
    override fun clear(storage: IStorage)
    override fun gc()

    companion object TupleHeapCache_C {
        /**
         * Returns the head-cache implementation.
         * @return the head-cache implementation.
         * @since 3.0
         */
        fun getInstance(): TupleHeapCache
    }
}