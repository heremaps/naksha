@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.base.AtomicMap
import naksha.base.Int64
import naksha.base.WeakRef
import naksha.jbon.IDict
import naksha.jbon.JbDictionary
import naksha.model.request.FeatureTuple
import kotlin.js.JsExport

/**
 * A cache in the Java heap for [Tuple]'s, used as default cache by the [Naksha.cache]. This cache is a mandatory first level cache, the application can install second or third level caches.
 *
 * **It is possible to replace this as first level cache, but strongly discouraged, without providing a much better alternative first level cache!**
 * @since 3.0.0
 */
@JsExport
class TupleHeapCache : AbstractTupleCache(LATENCY_MEMORY) {
    // TODO: !!! We should not store data only using weak-references. !!!
    //       This is many bad side effects, one very bad is that when an eviction happens, everything is evicted at ones!
    //       We should define a minimum cache size in bytes, and keep GZIP compressed full tuples, in binary encoding, in it.
    //       We should define a maximum cache size in bytes, and we use soft-references for this one (binary encoding).
    //       Eventually we should use all other available additional memory via weak-references for caching.
    //       All heap references should always be weak-referred to be collectable under memory pressure.
    //       The partial tuples should use only weak-references, they are very unhandy anyway and should be avoided.
    private var tuplesByStorage = AtomicMap<Int64, AtomicMap<TupleNumber, WeakRef<Tuple>>>()

    override val allowIncompleteTuple: Boolean
        get() = true

    override fun doInit() {}

    override fun doLoad(rs: List<FeatureTuple?>, start: Int, end: Int) {
        var i = start
        while (i < end) {
            val featureTuple = rs[i++] ?: continue
            val tupleNumber = featureTuple.tupleNumber
            val tuple = featureTuple.tuple
            if (tuple == null) {
                val cached = tuplesByStorage[tupleNumber.storageNumber]?.get(tupleNumber)?.deref()
                if (cached != null) {
                    featureTuple.tuple = tuple
                    featureTuple.source = this
                }
            }
        }
    }

    override fun doStore(tuple: Tuple, dictionary: IDict?): Tuple {
        // Do not cache incomplete or undefined tuples, they are created in the client and not yet stored.
        if (!tuple.isComplete()) return tuple
        val tuple_number = tuple.meta.tupleNumber
        if (TupleNumber.HEAD == tuple_number) return tuple

        var cacheLine = tuplesByStorage[tuple_number.storageNumber]
        if (cacheLine == null) {
            cacheLine = AtomicMap()
            val existing = tuplesByStorage.putIfAbsent(tuple_number.storageNumber, cacheLine)
            if (existing != null) cacheLine = existing
        }
        val existingRef = cacheLine[tuple_number]
        if (existingRef != null) {
            val existing = existingRef.deref()
            if (existing != null) return tuple
        }
        cacheLine[tuple_number] = WeakRef(tuple)
        return tuple
    }

    override fun doesContain(tupleNumber: TupleNumber): Boolean
        = tuplesByStorage[tupleNumber.storageNumber]?.containsKey(tupleNumber) ?: false

    override fun doStoreDictionary(dict: JbDictionary) {}

    override fun doEvictDictionary(dict: JbDictionary) {}

    override fun doLoadAllDictionaries(): List<JbDictionary> = emptyList()

    override fun addedStorage(storage: IStorage) {
        tuplesByStorage.putIfAbsent(storage.number, AtomicMap())
        nextCache?.removedStorage(storage)
    }

    override fun removedStorage(storage: IStorage) {
        tuplesByStorage.remove(storage.number)
        nextCache?.removedStorage(storage)
    }

    override fun clear() {
        tuplesByStorage = AtomicMap()
    }

    override fun gc() {
        for (cacheLine in tuplesByStorage.values) {
            for (e in cacheLine) {
                if (e.value.deref() == null) cacheLine.remove(e.key, e.value)
            }
        }
    }
}