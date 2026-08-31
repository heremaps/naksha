package naksha.psql

import naksha.base.AnyList
import naksha.base.Int64
import naksha.base.Platform.PlatformCompanion.toJSON
import naksha.model.objects.MemberType

/**
 * Rows as selected in [PgRows].
 * @since 3.0
 */
@Suppress("UNCHECKED_CAST")
internal data class PgColumnWithValues(
    /**
     * The database column.
     * @since 3.0
     */
    val pgColumn: PgColumn,

    /**
     * An optional alias, if the column is mapped to a different name in the result-set.
     * @since 3.0
     */
    val alias: String = pgColumn.name,

    /**
     * The values of the column for each row.
     * @since 3.0
     */
    val values: AnyList = AnyList()
) {
    fun withSize(size: Int): PgColumnWithValues {
        values.size = size
        return this
    }
    /**
     * Returns all values of this column as array that can be feed into PostgresQL [UNNEST](https://www.postgresql.org/docs/18/functions-array.html) function. Requires a minor hack for [naksha.model.TagList].
     * @see PgRows.setRow
     * @see PgRows.values
     */
    fun toArray(): Array<Any?> = Array(values.size) {
        val value = values[it]
        when (pgColumn.memberType) {
            // We keep it internally as text[], but UNNEST requires only one value.
            // Therefore, we work around by serializing the array into a JSON array, then deserializing after UNNEST.
            // See: PgRows.decodedColumns
            MemberType.TAG_LIST -> if (value is Array<*>) toJSON(value) else null
            else -> value
        }
    }

    override fun toString(): String = alias
}