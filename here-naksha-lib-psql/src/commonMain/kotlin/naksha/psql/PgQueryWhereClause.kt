package naksha.psql

/**
 * A SQL `WHERE` query.
 * @param collection the collection for which this `WHERE` query applies.
 * @since 3.0
 */
internal data class PgQueryWhereClause(val collection: PgCollection) {
    /**
     * The `WHERE` query, without the keyword `WHERE` or an empty string, if an empty query (query without conditions).
     * @since 3.0
     */
    val where = StringBuilder()

    /**
     * The arguments to used with the WHERE in order.
     * @since 3.0
     */
    val argValues: MutableList<Any?> = mutableListOf()

    /**
     * The types of the arguments.
     * @since 3.0
     */
    val argTypes: MutableList<PgType> = mutableListOf()

    /**
     * Returns the [argTypes] as typed-array _(`Array<String>`)_.
     * @since 3.0
     */
    val argTypeNames: Array<String>
        get() = argTypes.map(PgType::toString).toTypedArray()

    override fun toString(): String = where.toString()
}