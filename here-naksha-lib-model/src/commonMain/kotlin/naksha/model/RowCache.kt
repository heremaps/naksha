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
class RowCache internal constructor(
    /**
     * The storage identifier for which this cache holds rows.
     */
    @JvmField val storageId: String
) {
    private val rows = AtomicMap<RowNumber, WeakRef<Row>>()

    /**
     * Adds the given row into the cache.
     * @param row the row to add.
     * @return this
     */
    fun add(row: Row): RowCache {
        store(row)
        return this
    }

    /**
     * Returns the existing row from the cache; if any.
     * @param id the row identifier.
     * @return the row from the cache.
     */
    operator fun get(id: RowNumber): Row? = rows[id]?.deref()

    /**
     * Store the given row.
     *
     * This method automatically merges any row being already in the cache with the given row. This is necessary, because the row being in the cache may be more complete than the new one given.
     * @param row the row to store in the cache.
     * @return either the existing row, the given one or a merge row.
     */
    fun store(row: Row): Row {
        val row_number = row.rowNumber
        val existingRef = rows[row_number]
        if (existingRef != null) {
            val existing = existingRef.deref()
            if (existing != null && existing.rowNumber == row_number) {
                val merged = existing.merge(row)
                if (existing !== merged) {
                    rows[row_number] = WeakRef(merged)
                    return merged
                }
                return existing
            }
        }
        rows[row_number] = WeakRef(row)
        return row
    }

    /**
     * Store the given row.
     * @param rowNumber the row-number, must match [Row.rowNumber], otherwise an [ILLEGAL_ARGUMENT] is raised.
     * @param row the row to store.
     */
    operator fun set(rowNumber: RowNumber, row: Row) {
        if (rowNumber != row.rowNumber) {
            throw NakshaException(ILLEGAL_ARGUMENT, "The given row-id ($rowNumber) does not match the one of the given row (${row.rowNumber}")
        }
        store(row)
    }

    /**
     * Tests if the cache contains a row with the given id.
     * @param id the [row id][RowNumber].
     */
    operator fun contains(id: RowNumber): Boolean = rows.containsKey(id)

    /**
     * Remove (evict) the cached row.
     * @param id the [RowNumber] of the row to remove.
     * @return the removed [row][Row]; if any.
     */
    fun remove(id: RowNumber): Row? {
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