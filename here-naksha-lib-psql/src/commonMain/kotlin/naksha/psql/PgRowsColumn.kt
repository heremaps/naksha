package naksha.psql

import naksha.base.Platform.PlatformCompanion.toJSON
import naksha.base.illegalArg
import naksha.base.fromOrdinalFeatureNumber
import naksha.base.toOrdinalFeatureNumber
import naksha.model.objects.MemberType

/**
 * Rows as selected in [PgRows].
 * @since 3.0
 */
@Suppress("UNCHECKED_CAST", "ArrayInDataClass")
internal data class PgRowsColumn(
    /**
     * The [PgRows] to which this is bound.
     * @since 3.0
     */
    val pgRows: PgRows,

    /**
     * The index in [PgRows.columns].
     * @since 3.0
     */
    val index: Int,

    /**
     * The column for which row values are kept.
     * @since 3.0
     */
    val pgColumn: PgColumn,

    /**
     * An optional alias, if the column is mapped to a different name in the result-set; otherwise the same as `member.name`.
     * @since 3.0
     */
    val alias: String = pgColumn.name,

    /**
     * The raw SQL values for each row. Feature-number columns contain feature-ordinals; use the get/set operators for logical feature-numbers.
     * @since 3.0
     */
    internal var values: Array<Any?> = arrayOfNulls(pgRows.size),
) {
    /**
     * Physical cursor/SQL label, preserving explicit result aliases.
     * @since 3.0
     */
    val sqlAlias: String
        get() = if (alias == pgColumn.name) pgColumn.columnName else alias

    /**
     * The size of the [values], so amount of row values for this column.
     * @since 3.0
     */
    var size: Int
        get() = pgRows.size
        set(value) {
            pgRows.size = value
        }

    /**
     * Returns the logical value from the given index or `null`. Feature-ordinals are converted back to feature-numbers.
     * @param i the index to query.
     * @return the value at the given index or `null`.
     * @since 3.0
     */
    operator fun get(i: Int): Any? {
        val value = if (i in 0 until size) values[i] else null
        return if ((pgColumn eq PgColumn.FnColumn) && value != null) (value as Long).fromOrdinalFeatureNumber() else value
    }

    /**
     * Sets the logical value at the given index, storing feature-numbers as feature-ordinals. If the current [size] is less than `i`, increases the size, potentially adding `null` values.
     * @param i the index to set.
     * @param value the value to set.
     * @throws naksha.base.NakshaException with error [ILLEGAL_ARGUMENT][naksha.base.NakshaError.ILLEGAL_ARGUMENT] if the given index `i` is less than zero.
     * @since 3.0
     */
    operator fun set(i: Int, value: Any?) {
        if (i < 0) throw illegalArg("The given index $i is less than zero")
        if (i >= size) size = i + 1
        values[i] = if ((pgColumn eq PgColumn.FnColumn) && value != null) (value as Long).toOrdinalFeatureNumber() else value
    }

    /**
     * Returns all values of this column as array that can be feed into PostgresQL [UNNEST](https://www.postgresql.org/docs/18/functions-array.html) function. Feature-number columns are emitted as the feature-ordinal; other types are passed through (with a minor [naksha.model.TagList] hack).
     * @see PgRows.setRow
     * @see PgRows.values
     */
    fun toArray(): Array<Any?> = Array(size) {
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
