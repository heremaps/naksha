package naksha.psql

import naksha.base.*

/**
 * A cache for a specific map-id and map-number.
 */
data class PsqlAdminMapCache(val adminMap: PsqlAdminMap, val id: String, val number: Int) {
    /**
     * The current HEAD state, _null_ if the map does not exist.
     */
    val head = AtomicRef<PgMap>(null)

    /**
     * All cached collections by number.
     */
    val collectionByNumber = AtomicMap<Int, PgCollection>()

    /**
     * All cached collections by id.
     */
    val collectionById = AtomicMap<String, PgCollection>()
}