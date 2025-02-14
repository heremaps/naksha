package naksha.psql

/**
 * A SQL `WHERE` query.
 * @since 3.0
 */
internal data class PgQueryWhereClause(
    /**
     * The `WHERE` query, without the keyword `WHERE` or an empty string, if an empty query (query without conditions).
     * @since 3.0
     */
    val where: String,

    /**
     * The arguments to used with the WHERE in order.
     * @since 3.0
     */
    val argValues: List<Any?>,

    /**
     * The types of the arguments.
     * @since 3.0
     */
    val argTypes: List<PgType>,
) {
    /**
     * Returns the [argTypes] as string-array.
     */
    val argTypeNames: Array<String>
        get() = argTypes.map(PgType::toString).toTypedArray()
}