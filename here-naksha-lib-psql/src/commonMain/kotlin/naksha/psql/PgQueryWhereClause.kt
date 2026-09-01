package naksha.psql

import kotlin.jvm.JvmField

/**
 * A SQL `WHERE` query.
 * @param collection the collection for which this `WHERE` query applies.
 * @property collection the collection for which this `WHERE` query applies.
 * @since 3.0
 */
internal data class PgQueryWhereClause(
    @JvmField
    val collection: PgCollection,

    /**
     * The `WHERE` query, without the keyword `WHERE` or an empty string, if an empty query (query without conditions).
     * @since 3.0
     */
    @JvmField
    val where: String,

    /**
     * The arguments to used with the WHERE in order.
     * @since 3.0
     */
    @JvmField
    val argValues: List<Any?>,

    /**
     * The types of the arguments.
     * @since 3.0
     */
    @JvmField
    val argTypes: List<PgType>
) {
    /**
     * Returns the [argTypes] as typed-array _(`Array<String>`)_.
     * @since 3.0
     */
    val argTypeNames: Array<String>
        get() = argTypes.map(PgType::toString).toTypedArray()

    override fun toString(): String = where
}