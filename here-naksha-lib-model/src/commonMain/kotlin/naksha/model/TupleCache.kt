@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.base.AtomicMap
import naksha.base.WeakRef
import naksha.model.NakshaError.NakshaErrorCompanion.ILLEGAL_ARGUMENT
import kotlin.js.JsExport
import kotlin.jvm.JvmField

/**
 * A cache for rows.
 */
@JsExport
class TupleCache internal constructor(
    /**
     * The storage identifier for which this cache holds rows.
     */
    @JvmField val storageId: String
) {
    private val rows = AtomicMap<TupleNumber, WeakRef<Tuple>>()

    /**
     * Adds the given row into the cache.
     * @param tuple the row to add.
     * @return this
     */
    fun add(tuple: Tuple): TupleCache {
        store(tuple)
        return this
    }

    /**
     * Returns the existing row from the cache; if any.
     * @param id the row identifier.
     * @return the row from the cache.
     */
    operator fun get(id: TupleNumber): Tuple? = rows[id]?.deref()

    /**
     * Store the given row.
     *
     * This method automatically merges any row being already in the cache with the given row. This is necessary, because the row being in the cache may be more complete than the new one given.
     * @param tuple the row to store in the cache.
     * @return either the existing row, the given one or a merge row.
     */
    fun store(tuple: Tuple): Tuple {
        val row_number = tuple.tupleNumber
        val existingRef = rows[row_number]
        if (existingRef != null) {
            val existing = existingRef.deref()
            if (existing != null && existing.tupleNumber == row_number) {
                val merged = existing.merge(tuple)
                if (existing !== merged) {
                    rows[row_number] = WeakRef(merged)
                    return merged
                }
                return existing
            }
        }
        rows[row_number] = WeakRef(tuple)
        return tuple
    }

    /**
     * Store the given row.
     * @param tupleNumber the row-number, must match [Tuple.tupleNumber], otherwise an [ILLEGAL_ARGUMENT] is raised.
     * @param tuple the row to store.
     */
    operator fun set(tupleNumber: TupleNumber, tuple: Tuple) {
        if (tupleNumber != tuple.tupleNumber) {
            throw NakshaException(ILLEGAL_ARGUMENT, "The given row-id ($tupleNumber) does not match the one of the given row (${tuple.tupleNumber}")
        }
        store(tuple)
    }

    /**
     * Tests if the cache contains a row with the given id.
     * @param id the [row id][TupleNumber].
     */
    operator fun contains(id: TupleNumber): Boolean = rows.containsKey(id)

    /**
     * Remove (evict) the cached row.
     * @param id the [TupleNumber] of the row to remove.
     * @return the removed [row][Tuple]; if any.
     */
    fun remove(id: TupleNumber): Tuple? {
        val rowRef = rows.remove(id)
        return rowRef?.deref()
    }

    /**
     * Performs a garbage collection, remove all rows from the cache, that have been garbage collected.
     */
    fun gc() {
        for (e in rows) {
            if (e.value.deref() == null) rows.remove(e.key, e.value)
        }
    }
}