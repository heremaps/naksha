@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.base.AtomicMap
import naksha.base.Int64
import naksha.base.WeakRef
import naksha.model.NakshaError.NakshaErrorCompanion.ILLEGAL_ARGUMENT
import kotlin.js.JsExport
import kotlin.jvm.JvmField

/**
 * A cache for [Tuple]'s.
 * @since 3.0.0
 */
@JsExport
class TupleCache internal constructor(
    /**
     * The storage-number for which this cache holds tuples.
     * @since 3.0.0
     */
    @JvmField val storageNumber: Int64
) {
    private val tuples = AtomicMap<TupleNumber, WeakRef<Tuple>>()

    /**
     * Adds the given [Tuple] into the cache.
     * @param tuple the [Tuple] to add.
     * @return this.
     * @since 3.0.0
     */
    fun add(tuple: Tuple): TupleCache {
        store(tuple)
        return this
    }

    /**
     * Returns the existing row from the cache; if any.
     * @param tupleNumber the [TupleNumber].
     * @return the [Tuple] from the cache.
     * @since 3.0.0
     */
    operator fun get(tupleNumber: TupleNumber): Tuple? = tuples[tupleNumber]?.deref()

    /**
     * Store the given [Tuple].
     *
     * This method automatically merges any [Tuple] being already in the cache with the given [Tuple]. This is necessary, because the [Tuple] being in the cache may be more complete than the new one given.
     * @param tuple the [Tuple] to store in the cache.
     * @return either the existing [Tuple], the given one, or a merge [Tuple].
     * @since 3.0.0
     */
    fun store(tuple: Tuple): Tuple {
        val tuple_number = tuple.tupleNumber
        // Do not cache undefined tuples, they are created in the client and not yet stored.
        if (TupleNumber.UNDEFINED == tuple_number) return tuple
        val existingRef = tuples[tuple_number]
        if (existingRef != null) {
            val existing = existingRef.deref()
            if (existing != null && existing.tupleNumber == tuple_number) {
                val merged = existing.merge(tuple)
                if (existing !== merged) {
                    tuples[tuple_number] = WeakRef(merged)
                    return merged
                }
                return existing
            }
        }
        tuples[tuple_number] = WeakRef(tuple)
        return tuple
    }

    /**
     * Store the given [Tuple].
     * @param tupleNumber the [TupleNumber], must match [Tuple.tupleNumber], otherwise an [ILLEGAL_ARGUMENT] is raised.
     * @param tuple the row to store.
     * @since 3.0.0
     */
    operator fun set(tupleNumber: TupleNumber, tuple: Tuple) {
        if (tupleNumber != tuple.tupleNumber) {
            throw NakshaException(ILLEGAL_ARGUMENT, "The given row-id ($tupleNumber) does not match the one of the given row (${tuple.tupleNumber}")
        }
        store(tuple)
    }

    /**
     * Tests if the cache contains a [Tuple] with the given id.
     * @param tupleNumber the [TupleNumber] to check for.
     * @return _true_ if the [Tuple] is contained in cache; _false_ otherwise.
     * @since 3.0.0
     */
    operator fun contains(tupleNumber: TupleNumber): Boolean = tuples.containsKey(tupleNumber)

    /**
     * Remove (evict) the cached [Tuple].
     * @param tupleNumber the [TupleNumber] of the [Tuple] to remove.
     * @return the removed [Tuple]; if any.
     * @since 3.0.0
     */
    fun remove(tupleNumber: TupleNumber): Tuple? {
        val rowRef = tuples.remove(tupleNumber)
        return rowRef?.deref()
    }

    /**
     * Removes all cache entries.
     * @since 3.0.0
     */
    fun clear() {
        for (e in tuples) tuples.remove(e.key, e.value)
    }

    /**
     * Performs a garbage collection, remove all weak-references to [Tuple]s from the cache, that have been garbage collected.
     * @since 3.0.0
     */
    fun gc() {
        for (e in tuples) {
            if (e.value.deref() == null) tuples.remove(e.key, e.value)
        }
    }
}