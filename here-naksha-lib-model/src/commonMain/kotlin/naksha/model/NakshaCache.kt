@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.base.AtomicMap
import naksha.base.WeakRef
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmStatic

/**
 * All kind of global caches.
 */
@JsExport
class NakshaCache private constructor() {
    companion object PgCache_C {
        private val tupleCaches = AtomicMap<String, WeakRef<TupleCache>>()

        /**
         * Returns the cache for all rows of a specific storage.
         * @param storageId the identifier of the storage for which to return the [row-cache][TupleCache].
         * @return the [row-cache][TupleCache] for the storage.
         */
        @JvmStatic
        @JsStatic
        tailrec fun rowCache(storageId: String): TupleCache {
            var ref = tupleCaches[storageId]
            var cache = ref?.deref()
            if (cache != null) return cache
            cache = TupleCache(storageId)
            ref = WeakRef(cache)
            if (tupleCaches.putIfAbsent(storageId, ref) == null) return cache
            return rowCache(storageId)
        }
    }
}