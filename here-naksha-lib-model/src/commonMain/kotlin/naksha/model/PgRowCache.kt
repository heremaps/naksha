@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.base.AtomicMap
import naksha.base.WeakRef
import kotlin.js.JsExport
import kotlin.jvm.JvmField

// TODO: Document me!

/**
 * A global cache for rows.
 */
@JsExport
class PgRowCache internal constructor(
    /**
     * The storage identifier for which this cache holds rows.
     */
    @JvmField val storageId: String
) {
    private val rows = AtomicMap<RowId, WeakRef<Row>>()

    fun add(row: Row): PgRowCache {
        this[row.id] = row
        return this
    }
    operator fun get(id: RowId): Row? = rows[id]?.deref()
    operator fun set(id: RowId, value: Row) {
        val existingRef = rows[id]
        if (existingRef != null) {
            val existing = existingRef.deref()
            if (existing != null && existing.id == id) return
        }
        rows[id] = WeakRef(value)
    }
    operator fun contains(id: RowId): Boolean = rows.containsKey(id)
    fun remove(id: RowId): Row? {
        val rowRef = rows.remove(id)
        return rowRef?.deref()
    }

    fun gc() {
        for (e in rows) {
            if (e.value.deref() == null) rows.remove(e.key, e.value)
        }
    }
}