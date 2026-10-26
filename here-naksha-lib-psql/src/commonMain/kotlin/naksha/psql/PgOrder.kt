package naksha.psql

/**
 * The ordering within an [PgIndex].
 * @since 3.0
 */
enum class PgOrder {
    /** Ascending order */
    ASC,
    /** Descending order */
    DESC;

    override fun toString(): String = this.name
}