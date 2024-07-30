@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.base.AtomicMap
import naksha.base.WeakRef
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * A row cache across storages.
 */
@JsExport
class PgCache private constructor() {
    private val all = AtomicMap<String, WeakRef<PgRowCache>>()

    companion object PgCache_C {
        /**
         * The static singleton of the cache.
         */
        @JvmField
        @JsStatic
        val instance = PgCache()
    }

    /**
     * Returns the cache for the storage with the given identifier.
     * @param storageId the identifier of the storage for which to return the [row-cache][PgRowCache].
     * @return the [row-cache][PgRowCache].
     */
    tailrec operator fun get(storageId: String): PgRowCache {
        var ref = all[storageId]
        var cache = ref?.deref()
        if (cache != null) return cache
        cache = PgRowCache(storageId)
        ref = WeakRef(cache)
        if (all.putIfAbsent(storageId, ref) == null) return cache
        return get(storageId)
    }
}