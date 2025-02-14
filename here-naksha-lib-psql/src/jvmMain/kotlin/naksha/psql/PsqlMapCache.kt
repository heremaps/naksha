package naksha.psql

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.CacheLoader
import com.github.benmanes.caffeine.cache.LoadingCache
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.Expiry
import com.github.benmanes.caffeine.cache.RemovalCause
import naksha.base.AtomicMap
import java.util.concurrent.TimeUnit

/**
 * [Coffeine](https://github.com/ben-manes/caffeine) cache loader to load a [PgMap] wrapper, being [PsqlMap]. This will as well then offer a child cache, that is used for the [collection's][PgCollection].
 * @since 3.0
 */
class PsqlMapCache(@JvmField val adminMap: PsqlAdminMap): CacheLoader<Int, PsqlMap?>, Expiry<Int, PsqlMap> {

    /**
     * Returns the [PsqlMap] by map-id, if not being in cache, loads it into the cache.
     * @param id the map-id.
     * @return the [PsqlMap].
     */
    operator fun get(id: String?): PsqlMap? {
        if (id == null) return null
        val number = numberById[id] ?: return loadById(id)
        return cache.get(number, this::load)
    }

    operator fun get(number: Int): PsqlMap? = cache.get(number, this::load)

    /**
     * All maps by their number (**primary**).
     * @since 3.0
     */
    private val cache: Cache<Int, PsqlMap?> = Caffeine.newBuilder()
        // TODO: Optimize settings!
        .removalListener(this::onEviction)
        .expireAfter(this)
        .build(this)

    /**
     * Translates the map-id into a number for items being in cache.
     * @since 3.0.0
     */
    private val numberById = AtomicMap<String, Int>()

    /**
     * Called by Caffeine, when a cached map is removed.
     * @param mapNumber the map-number _(aka key)_
     * @param psqlMap the value that is removed
     * @param cause the reason for the removal
     * @since 3.0
     * @see [Caffeine.removalListener]
     */
    private fun onEviction(mapNumber: Int?, psqlMap: PsqlMap?, cause: RemovalCause) {
        if (psqlMap != null) {
            numberById.remove(psqlMap.id, psqlMap.number)
        }
    }

    /**
     * Load a cache entry using the `id`.
     * @param id the map-id.
     * @return the loaded [PsqlMap]; null if the map does not exist.
     */
    private fun loadById(id: String): PsqlMap? {
        // TODO: Implement me
        // TODO: Add entry into numberById
        // TODO: Add into cache!
        return null
    }

    /**
     * Computes or retrieves the value corresponding to `key`.
     *
     * **Warning:** loading **must not** attempt to update any mappings of this cache directly.
     *
     * @param key the non-null key whose value should be loaded
     * @return the value associated with `key` or `null` if not found
     * @throws Exception or Error, in which case the mapping is unchanged
     * @throws InterruptedException if this method is interrupted. [InterruptedException] is treated like any other [Exception] in all respects except that, when it is caught, the thread's interrupt status is set
     */
    override fun load(key: Int): PsqlMap? {
        // TODO: Implement me
        // TODO: Add entry into numberById
        return null
    }

    /**
     * Computes or retrieves the values corresponding to `keys`. This method is called by [LoadingCache.getAll].
     *
     * If the returned map doesn't contain all requested `keys`, then the entries it does contain will be cached, and `getAll` will return the partial results. If the returned map contains extra keys not present in `keys` then all returned entries will be cached, but only the entries for `keys`, will be returned from `getAll`.
     *
     * This method should be overridden when bulk retrieval is significantly more efficient than many individual lookups. Note that [LoadingCache.getAll] will defer to individual calls to [LoadingCache.get] if this method is not overridden.
     *
     * **Warning**: loading **must not** attempt to update any mappings of this cache directly.
     *
     * @param keys the unique, non-null keys whose values should be loaded
     * @return a map from each key in `keys` to the value associated with that key
     * @throws Exception or Error, in which case the mappings are unchanged
     * @throws InterruptedException if this method is interrupted. [InterruptedException] is treated like any other [Exception] in all respects except that, when it is caught, the thread's interrupt status is set
     */
    override fun loadAll(keys: Set<Int>): Map<Int, PsqlMap> {
        // TODO: Implement me
        return mapOf()
    }

    /**
     * Returns all [map's][PsqlMap] that are currently available.
     * @return all [map's][PsqlMap] that are currently available.
     */
    fun getAll(): List<PsqlMap> {
        TODO("Implement me")
    }

    /**
     * Computes or retrieves a replacement value corresponding to an already-cached `key`. If the replacement value is not found, then the mapping will be removed if `null` is returned. This method is called when an existing cache entry is refreshed by [Caffeine,refreshAfterWrite], or through a call to [LoadingCache.refresh].
     *
     * **Warning:** loading **must not** attempt to update any mappings of this cache directly or block waiting for other cache operations to complete.
     *
     * **Note:** _all exceptions thrown by this method will be logged and then swallowed_.
     *
     * @param key the non-null key whose value should be loaded
     * @param oldValue the non-null old value corresponding to `key`
     * @return the new value associated with `key`, or `null` if the mapping is to be removed
     * @throws Exception or Error, in which case the mapping is unchanged
     * @throws InterruptedException if this method is interrupted. [InterruptedException] is treated like any other [Exception] in all respects except that, when it is caught, the thread's interrupt status is set
     */
    override fun reload(key: Int, oldValue: PsqlMap): PsqlMap? {
        if (!oldValue.exists()) return null
        return oldValue
    }

    /**
     * Specifies that the entry should be automatically removed from the cache once the duration has elapsed after the entry's creation. To indicate no expiration, an entry may be given an excessively long period, such as [Long.MAX_VALUE].
     *
     * **Note:** The `currentTime` is supplied by the configured [Ticker][org.testcontainers.shaded.com.google.common.base.Ticker] and by default does not relate to system or wall-clock time. When calculating the duration based on a timestamp, the current time should be obtained independently.
     *
     * @param key the key associated with this entry
     * @param value the value associated with this entry
     * @param currentTime the ticker's current time, in nanoseconds
     * @return the length of time before the entry expires, in nanoseconds
     */
    override fun expireAfterCreate(key: Int, value: PsqlMap, currentTime: Long): Long {
        if (!value.exists()) return TimeUnit.SECONDS.toNanos(5)
        return TimeUnit.MINUTES.toNanos(15)
    }

    /**
     * Specifies that the entry should be automatically removed from the cache once the duration has elapsed after the replacement of its value. To indicate no expiration, an entry may be given an excessively long period, such as [Long.MAX_VALUE]. The `currentDuration` may be
     * returned to not modify the expiration time.
     *
     * **Note:** The `currentTime` is supplied by the configured [Ticker][org.testcontainers.shaded.com.google.common.base.Ticker] and by default does not relate to system or wall-clock time. When calculating the duration based on a timestamp, the current time should be obtained independently.
     *
     * @param key the key associated with this entry
     * @param value the new value associated with this entry
     * @param currentTime the ticker's current time, in nanoseconds
     * @param currentDuration the entry's current duration, in nanoseconds
     * @return the length of time before the entry expires, in nanoseconds
     */
    override fun expireAfterUpdate(key: Int, value: PsqlMap, currentTime: Long, currentDuration: Long): Long {
        if (!value.exists()) return currentDuration
        return TimeUnit.MINUTES.toNanos(15)
    }

    /**
     * Specifies that the entry should be automatically removed from the cache once the duration has elapsed after its last read. To indicate no expiration, an entry may be given an excessively long period, such as [Long.MAX_VALUE]. The `currentDuration` may be returned to not
     * modify the expiration time.
     *
     * **Note:** The `currentTime` is supplied by the configured [Ticker][org.testcontainers.shaded.com.google.common.base.Ticker] and by default does not relate to system or wall-clock time. When calculating the duration based on a timestamp, the current time should be obtained independently.
     *
     * @param key the key associated with this entry
     * @param value the value associated with this entry
     * @param currentTime the ticker's current time, in nanoseconds
     * @param currentDuration the entry's current duration, in nanoseconds
     * @return the length of time before the entry expires, in nanoseconds
     */
    override fun expireAfterRead(key: Int, value: PsqlMap, currentTime: Long, currentDuration: Long): Long {
        if (!value.exists()) return currentDuration
        return TimeUnit.MINUTES.toNanos(15)
    }
}