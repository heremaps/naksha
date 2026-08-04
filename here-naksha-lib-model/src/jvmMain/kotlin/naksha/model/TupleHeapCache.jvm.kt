@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package naksha.model

import naksha.base.AtomicMap
import naksha.base.Int64
import naksha.base.TupleNumber
import naksha.base.WeakRef

actual class TupleHeapCache : ITupleCache {
    actual override val latencyInMicros: Int64
        get() = LATENCY_MEMORY

    // TODO: Review Caffeine, we should use it!
    //       https://github.com/ben-manes/caffeine
    // TODO: !!! We should not store data only using weak-references. !!!
    //       This is many bad side effects, one very bad is that when an eviction happens, everything is evicted at ones!
    //       We should define a minimum cache size in bytes, and keep GZIP compressed full tuples, in binary encoding, in it.
    //       We should define a maximum cache size in bytes, and we use soft-references for this one (binary encoding).
    //       Eventually we should use all other available additional memory via weak-references for caching.
    //       All heap references should always be weak-referred to be collectable under memory pressure.
    //       The partial tuples should use only weak-references, they are very unhandy anyway and should be avoided.
    private var tuplesCache = AtomicMap<TupleNumber, WeakRef<Tuple>>()

    actual override fun get(tupleNumber: TupleNumber): Tuple? {
        val weakRef = tuplesCache[tupleNumber] ?: return null
        val tuple = weakRef.deref()
        if (tuple == null) tuplesCache.remove(tupleNumber, weakRef)
        return tuple
    }

    actual override fun load(tuples: Array<Tuple?>, tupleNumbers: Array<TupleNumber>, from:Int, to:Int, maxMicros: Int64?): Int {
        var loaded = 0
        for (i in from ..< to) {
            if (tuples[i] != null) continue
            val tn = tupleNumbers[i]
            val cached = get(tn) ?: continue
            loaded++
            tuples[i] = cached
        }
        return loaded
    }

    actual override fun put(tuple: Tuple) {
        tuplesCache.putIfAbsent(tuple.tupleNumber, tuple.weakRef)
    }

    actual override fun store(vararg tuples: Tuple?) {
        for (tuple in tuples) if (tuple != null) put(tuple)
    }

    actual override fun clear() {
        tuplesCache.clear()
    }

    actual override fun gc() {
        for ((tupleNumber, weakRef) in tuplesCache) {
            val tuple = weakRef.deref()
            if (tuple == null) tuplesCache.remove(tupleNumber, weakRef)
        }
    }
}