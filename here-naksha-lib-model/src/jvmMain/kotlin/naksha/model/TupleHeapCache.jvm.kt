@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package naksha.model

import naksha.base.AtomicMap
import naksha.base.Int64
import naksha.base.TupleNumber
import naksha.jbon.IDictReader
import naksha.model.request.FeatureTuple
import java.lang.ref.SoftReference

actual class TupleHeapCache : ITupleCache {
    actual override val latencyInMicros: Int64
        get() = LATENCY_MEMORY

    private var tuplesByStorage = AtomicMap<Int64, AtomicMap<TupleNumber, SoftReference<Tuple>>>()

    actual override fun get(tupleNumber: TupleNumber): Tuple?
        = tuplesByStorage[tupleNumber.databaseNumber]?.get(tupleNumber)?.get()

    actual override fun load(featureTuples: List<FeatureTuple?>, from: Int, to: Int, acceptFeature: Boolean): Int {
        var loaded = 0
        for (i in from ..< to) {
            val featureTuple = featureTuples[i] ?: continue
            val tupleNumber = featureTuple.tupleNumber
            val tuple = featureTuple.tuple
            if (tuple != null) continue
            if (acceptFeature) {
                val feature = featureTuple.feature
                if (feature != null) continue
            }
            val cached = get(tupleNumber) ?: continue
            loaded++
            featureTuple.tuple = cached
            featureTuple.source = this
        }
        return loaded
    }

    actual override fun put(tuple: Tuple) {
        val tupleNumber = tuple.tupleNumber
        val storageNumber = tupleNumber.databaseNumber
        var storageTuples = tuplesByStorage[storageNumber]
        if (storageTuples == null) {
            storageTuples = AtomicMap()
            val existing = tuplesByStorage.putIfAbsent(storageNumber, storageTuples)
            if (existing != null) storageTuples = existing
        }
        storageTuples[tupleNumber] = SoftReference(tuple)
    }

    actual override fun store(tuples: List<Tuple>) {
        for (tuple in tuples) put(tuple)
    }

    actual override fun onStorageAdd(storage: IStorage) {
    }

    actual override fun onStorageRemove(storage: IStorage) {
    }

    actual override fun getDictReader(storageNumber: Int64): IDictReader? {
        // TODO: Implement me!
        return null
    }

    actual override fun clear() {
        tuplesByStorage.clear()
    }

    actual override fun clear(storage: IStorage) {
        tuplesByStorage.remove(storage.number)
    }

    actual override fun gc() {
        for (storageTuples in tuplesByStorage.values) {
            val dead = storageTuples.entries.filter { it.value.get() == null }
            for (entry in dead) storageTuples.remove(entry.key, entry.value)
        }
    }

    actual companion object TupleHeapCache_C {
        private val theInstance = TupleHeapCache()

        /**
         * Returns the head-cache implementation.
         * @return the head-cache implementation.
         * @since 3.0
         */
        actual fun getInstance(): TupleHeapCache = theInstance
    }
}