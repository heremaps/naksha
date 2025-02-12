package naksha.psql

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
    val argTypes: Array<String>
) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as PgQuery

        if (sql != other.sql) return false
        if (!argValues.contentEquals(other.argValues)) return false
        if (!argTypes.contentEquals(other.argTypes)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = sql.hashCode()
        result = 31 * result + argValues.contentHashCode()
        result = 31 * result + argTypes.contentHashCode()
        return result
    }
}