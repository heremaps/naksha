package naksha.psql

import naksha.base.Id

/**
 * An SQL query to be executed against a Naksha table.
 * @since 3.0
 */
data class PgQuery(
    /**
     * The SQL string.
     * @since 3.0
     */
    val sql: String,

    /**
     * The arguments for &#36;1 to &#36;n.
     * @since 3.0
     */
    val argValues: Array<Any?>,

    /**
     * The argument types as specified in [PgType], so like:
     * ```kotlin
     * arrayOf(PgType.INT.toString())
     * ```
     * @since 3.0
     */
    val argTypes: Array<String>,

    /**
     * The `id` of the database from which the results are.
     * @since 3.0
     */
    val databaseId: Id,

    /**
     * The catalog from which the results are.
     * @since 3.0
     */
    val catalog: PgCatalog,

    /**
     * The collection from which the results are.
     * @since 3.0
     */
    val collection: PgCollection,
) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as PgQuery

        if (sql != other.sql) return false
        if (databaseId != other.databaseId) return false
        if (catalog.id != other.catalog.id) return false
        if (collection.id != other.collection.id) return false
        if (!argValues.contentEquals(other.argValues)) return false
        if (!argTypes.contentEquals(other.argTypes)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = sql.hashCode()
        result = 31 * result + databaseId.hashCode()
        result = 31 * result + catalog.id.hashCode()
        result = 31 * result + collection.id.hashCode()
        result = 31 * result + argValues.contentHashCode()
        result = 31 * result + argTypes.contentHashCode()
        return result
    }
}