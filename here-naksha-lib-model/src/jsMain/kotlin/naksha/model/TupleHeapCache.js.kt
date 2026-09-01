@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package naksha.model

import naksha.base.Int64
import naksha.base.TupleNumber
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

    actual override fun get(tupleNumber: TupleNumber): Tuple? {
        TODO("Not yet implemented")
    }

    actual override fun load(featureTuples: List<FeatureTuple?>, from: Int, to: Int, acceptFeature: Boolean): Int {
        TODO("Not yet implemented")
    }

    actual override fun put(tuple: Tuple) {
        TODO("Not yet implemented")
    }

    actual override fun store(tuples: List<Tuple>) {
        TODO("Not yet implemented")
    }

    actual override fun onStorageAdd(storage: IStorage) {
        TODO("Not yet implemented")
    }

    actual override fun onStorageRemove(storage: IStorage) {
        TODO("Not yet implemented")
    }

    actual override fun getDictReader(storageNumber: Int64): IDictReader? {
        TODO("Not yet implemented")
    }

    actual override fun clear() {
        TODO("Not yet implemented")
    }

    actual override fun clear(storage: IStorage) {
        TODO("Not yet implemented")
    }

    actual override fun gc() {
        TODO("Not yet implemented")
    }

    actual companion object TupleHeapCache_C {
        /**
         * Returns the head-cache implementation.
         * @return the head-cache implementation.
         * @since 3.0
         */
        actual fun getInstance(): TupleHeapCache {
            TODO("Not yet implemented")
        }
    }
}