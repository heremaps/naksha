@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.base.AtomicNonNullRef
import naksha.base.Int64
import naksha.base.IntMutable
import naksha.base.TupleNumber
import naksha.base.fn.Fn1
import naksha.base.fn.Fn2
import naksha.base.fn.Fx1
import naksha.base.illegalArg
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.math.max
import kotlin.math.min

/**
 * The standard tuple cache manager, allows to chain multiple caches implementing [ITupleCache]. The default constructor adds the [TupleHeapCache].
 *
 * @since 3.0
 */
@JsExport
class TupleCacheManager : ITupleCache {

    private val caches: AtomicNonNullRef<Array<ITupleCache>> = AtomicNonNullRef(arrayOf(TupleHeapCache()))

    /**
     * The default maximum amount of microseconds allowed for [get] and [load], defaults to `9223372036854775807`.
     *
     * ### Note
     * If set to a negative value, no cache will be queried when [get] or [load] are invoked!
     * @since 3.0
     * @see [load]
     * @see [store]
     */
    var maxLoadMicros: Int64 = Int64(9223372036854775807)
        set(value) {
            field = if (value < 0) Int64(0) else value
        }

    /**
     * Adds the given cache.
     * @param cache the cache to add.
     * @return `true` if the cache was added; `false` if it is already added.
     * @since 3.0
     */
    fun addCache(cache: ITupleCache): Boolean {
        do {
            val current = caches.get()
            val new_caches = Array(current.size + 1) { if (it < current.size) current[it] else cache }
            if (caches.compareAndSet(current, new_caches)) return true
            // Concurrent update of caches, retry
        } while (true)
    }

    /**
     * Adds the given cache, if not already added, and returns this.
     * @param cache the cache to add, if not already part of the manager.
     * @return this.
     * @since 3.0
     */
    fun withCache(cache: ITupleCache): TupleCacheManager {
        addCache(cache)
        return this
    }

    /**
     * Removes the given cache.
     * @param cache the cache to remove.
     * @return `true` if the cache was removed; `false` if it is not part of the caching.
     * @since 3.0
     */
    fun removeCache(cache: ITupleCache): Boolean {
        do {
            val current = caches.get()
            val removeIndex: Int = current.indexOf(cache)
            if (removeIndex < 0) return false
            val new_caches = Array(current.size - 1) { i ->
                if (i < removeIndex) current[i] else current[i + 1]
            }
            if (caches.compareAndSet(current, new_caches)) return true
            // Concurrent update of caches, retry
        } while (true)
    }

    /**
     * Removes the cache, if added, and returns this.
     * @param cache the cache to remove.
     * @return this.
     * @since 3.0
     */
    fun withoutCache(cache: ITupleCache): TupleCacheManager {
        removeCache(cache)
        return this
    }

    /**
     * Invoke the given lambda for every cache to find a value, aborts ones the value is found, returning the found value.
     * @param f the function to call for each [cache][ITupleCache].
     * @return the first none `null` value returned by any call to `f`, or `null`, if `f` always returned `null` for all [caches][ITupleCache] _(or no caches are added)_.
     * @since 3.0
     */
    fun <V> find(f: Fn1<V?, ITupleCache>): V? {
        val caches = this.caches.get()
        for (cache in caches) {
            val v = f.call(cache)
            if (v != null) return v
        }
        return null
    }

    /**
     * Invoke the given lambda for every cache to calculate a value.
     * @param startValue the initial value to be provided for the first lambda.
     * @param f the function to call for each [cache][ITupleCache].
     * @return the result returned by the last function or the given `startValue`, when no function has been called, or the last function returned the `startValue`.
     * @since 3.0
     */
    fun <V> calculate(startValue: V?, f: Fn2<V?, ITupleCache, V?>): V? {
        val caches = this.caches.get()
        var value: V? = startValue
        for (cache in caches) {
            value = f.call(cache, value)
        }
        return value
    }

    /**
     * Invoke the given lambda for every cache to calculate a non-null value.
     * @param startValue the initial value to be provided for the first lambda.
     * @param f the function to call for each [cache][ITupleCache].
     * @return the result returned by the last function or the given `startValue`, when no function has been called, or the last function returned the `startValue`.
     * @since 3.0
     */
    fun <V> calculateNotNull(startValue: V, f: Fn2<V, ITupleCache, V>): V {
        val caches = this.caches.get()
        var value: V = startValue
        for (cache in caches) {
            value = f.call(cache, value)
        }
        return value
    }

    /**
     * Invoke the given lambda for every cache to perform an action.
     * @param f the function to call for each [cache][ITupleCache].
     * @since 3.0
     */
    fun forAll(f: Fx1<ITupleCache>) {
        val caches = this.caches.get()
        for (cache in caches) {
            f.call(cache)
        }
    }

    override val latencyInMicros: Int64
        get() = calculateNotNull(Int64(0), { cache, value ->
            if (cache.latencyInMicros < value) cache.latencyInMicros else value
        })

    /**
     * Read a single tuple from cache with zero latency.
     *
     * @param tupleNumber the [naksha.base.TupleNumber] of the [Tuple] to read.
     * @return the [Tuple], if it is in the cache, `null` otherwise.
     * @since 3.0
     */
    override fun get(tupleNumber: TupleNumber): Tuple? {
        val ZERO = Int64(0)
        return find { if (it.latencyInMicros == ZERO) it[tupleNumber] else null }
    }

    override fun put(tuple: Tuple) {
        forAll { it.put(tuple) }
    }


    // JVM Overloads
    @JsName("loadWithFromToMaxDefaults")
    fun load(tuples: Array<Tuple?>, tupleNumbers: Array<TupleNumber>): Int = load(tuples, tupleNumbers, from=0, to=tuples.size, maxMicros = null)
    @JsName("loadWithToMaxDefaults")
    fun load(tuples: Array<Tuple?>, tupleNumbers: Array<TupleNumber>, from:Int): Int = load(tuples, tupleNumbers, from, tuples.size, null)
    @JsName("loadWithMaxDefaults")
    fun load(tuples: Array<Tuple?>, tupleNumbers: Array<TupleNumber>, from:Int, to:Int): Int = load(tuples, tupleNumbers, from, to, null)
    override fun load(tuples: Array<Tuple?>, tupleNumbers: Array<TupleNumber>, from:Int, to:Int, maxMicros: Int64?): Int {
        val _from = max(0, min(from, to))
        val _to = min(tuples.size, max(from, to))
        if (tuples.size < _to) throw illegalArg("The tuple array is too short")
        if (tupleNumbers.size < _to) throw illegalArg("The tuple-number array is too short")
        val MAX = max(0, (maxMicros ?: maxLoadMicros).toLong()).toLong()
        val count = IntMutable(0)
        forAll { cache ->
            if (cache.latencyInMicros <= MAX) {
                count.value += cache.load(tuples, tupleNumbers, _from, _to, MAX)
            }
        }
        return count.value
    }

    override fun store(vararg tuples: Tuple?) {
        find { it.store(*tuples) }
    }

    /**
     * Removes all cache entries (clear the cache).
     * @since 3.0.0
     */
    override fun clear() {
        find { it.clear() }
    }

    override fun gc() {
        find { it.gc() }
    }
}