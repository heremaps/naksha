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
class NakshaRowCache internal constructor(
    /**
     * The storage identifier for which this cache holds rows.
     */
    @JvmField val storageId: String
) {
    private val rows = AtomicMap<RowId, WeakRef<Row>>()

    /**
     * Adds the given row into the cache.
     * @param row the row to add.
     * @return this
     */
    fun add(row: Row): NakshaRowCache {
        store(row)
        return this
    }

    /**
     * Returns the existing row from the cache; if any.
     * @param id the row identifier.
     * @return the row from the cache.
     */
    operator fun get(id: RowId): Row? = rows[id]?.deref()

    /**
     * Store the given row.
     *
     * This method automatically merges any row being already in the cache with the given row. This is necessary, because the row being in the cache may be more complete than the new one given.
     * @param row the row to store in the cache.
     * @return either the existing row, the given one or a merge row.
     */
    fun store(row: Row): Row {
        val id = row.id
        val existingRef = rows[id]
        if (existingRef != null) {
            val existing = existingRef.deref()
            if (existing != null && existing.id == id) {
                val merged = existing.merge(row)
                if (existing !== merged) {
                    rows[id] = WeakRef(merged)
                    return merged
                }
                return existing
            }
        }
        rows[id] = WeakRef(row)
        return row
    }

    /**
     * Store the given row.
     * @param id the row id, must match [Row.id], otherwise an [ILLEGAL_ARGUMENT] is raised.
     * @param row the row to store.
     */
    operator fun set(id: RowId, row: Row) {
        if (id != row.id) {
            throw NakshaException(ILLEGAL_ARGUMENT, "The given row-id ($id) does not match the one of the given row (${row.id}")
        }
        store(row)
    }

    /**
     * Tests if the cache contains a row with the given id.
     * @param id the [row id][RowId].
     */
    operator fun contains(id: RowId): Boolean = rows.containsKey(id)

    /**
     * Remove (evict) the cached row.
     * @param id the [RowId] of the row to remove.
     * @return the removed [row][Row]; if any.
     */
    fun remove(id: RowId): Row? {
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