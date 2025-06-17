@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package naksha.model

import naksha.base.Int64
import naksha.jbon.IDictReader
import naksha.model.request.FeatureTuple

/**
 * A cache in the Java heap for [Tuple]'s, used as default cache by the [Naksha.cache]. This cache is a mandatory first level cache, the application can install second or third level caches.
 *
 * **It is possible to replace this as first level cache, but strongly discouraged, without providing a much better alternative first level cache!**
 * @since 3.0
 */
actual class TupleHeapCache : ITupleCache {
    actual override val latencyInMicros: Int64
        get() = LATENCY_MEMORY

    // TODO: Clean the cache, currently its growing endlessly!
    private val cache = HashMap<TupleNumber, Tuple>()

    actual override fun get(tupleNumber: TupleNumber): Tuple? = cache[tupleNumber]

    actual override fun load(featureTuples: List<FeatureTuple?>, from: Int, to: Int, acceptFeature: Boolean): Int {
        var loaded = 0
        for (i in from until to) {
            val featureTuple = featureTuples[i] ?: continue
            if (featureTuple.tuple == null) {
                if (acceptFeature && featureTuple.feature != null) continue
                val tuple = cache[featureTuple.tupleNumber] ?: continue
                loaded++
                featureTuple.tuple = tuple
            }
        }
        return loaded
    }

    actual override fun put(tuple: Tuple) {
        cache[tuple.tupleNumber] = tuple
    }

    actual override fun store(tuples: List<Tuple>) {
        for (tuple in tuples) {
            cache[tuple.tupleNumber] = tuple
        }
    }

    actual override fun onStorageAdd(storage: IStorage) {
    }

    actual override fun onStorageRemove(storage: IStorage) {
    }

    actual override fun getDictReader(storageNumber: Int64): IDictReader? = null

    actual override fun clear() {
        cache.clear()
    }

    actual override fun clear(storage: IStorage) {
        cache.clear()
    }

    actual override fun gc() {
    }

    actual companion object TupleHeapCache_C {
        private val singleton = TupleHeapCache()

        /**
         * Returns the head-cache implementation.
         * @return the head-cache implementation.
         * @since 3.0
         */
        actual fun getInstance(): TupleHeapCache = singleton
    }
}