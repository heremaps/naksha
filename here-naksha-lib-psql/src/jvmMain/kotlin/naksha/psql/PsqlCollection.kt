package naksha.psql

import naksha.base.AtomicRef

/**
 * A cache entry for a [PgCollection].
 * @since 3.0
 */
data class PsqlCollection(val psqlCatalog: PsqlCatalog, val id: String, val number: Number) {
    /**
     * Tests if the underlying [PgCollection] exist.
     * @return `true` if the collection exists; `false` if this is a tombstone cache entry.
     * @since 3.0
     */
    fun exists(): Boolean = head.get() != null

    /**
     * The current HEAD state, _null_ if the collection does not exist _(after being deleted)_.
     * @since 3.0
     */
    val head = AtomicRef<PgCollection>(null)

    /**
     * Helper method to create a pre-filled cache entry.
     * @param collection the collection to add.
     * @return this.
     * @since 3.0
     */
    fun withCollection(collection: PgCollection): PsqlCollection {
        head.set(collection)
        return this
    }
}