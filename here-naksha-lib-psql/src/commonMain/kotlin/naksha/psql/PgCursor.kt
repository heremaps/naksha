package naksha.psql

import naksha.base.AnyObject
import kotlin.reflect.KClass

/**
 * A cursor as it is returned by the [PLV8](https://plv8.github.io/) engine. In Java this is a thin wrapper around a
 * [ResultSet](https://docs.oracle.com/javase/8/docs/api/java/sql/ResultSet.html). The cursor is initially positions before the first row.
 * @since 3.0
 */
expect class PgCursor private constructor(): AutoCloseable {

    /**
     * Returns the number of affected row when this is the cursor of an update query.
     * @return the number of affected row; -1 if the query was a SELECT or UPDATE with RETURNS.
     */
    fun affectedRows(): Int

    /**
     * Move the cursor to the next row.
     * @return _true_ if the cursor is positioned at a row; _false_ is the cursor is behind the last row.
     * @see fetch
     */
    fun next(): Boolean

    /**
     * Move the cursor to the next row.
     * @return this.
     * @see next
     */
    fun fetch(): PgCursor

    /**
     * Tests if the cursor is currently positioned at a valid row.
     * @return _true_ if the cursor is positioned at a row; _false_ is the cursor is behind the last row or before the first.
     */
    fun isRow(): Boolean

    /**
     * Returns the current row number. The first row is 1, the row before the first row is 0.
     * @return the current row number (0 to n, where 0 is before the first row).
     */
    fun rowNumber(): Int

    /**
     * Returns the names of the columns of the current row.
     *
     * #### Warning
     * Requires that [isRow] is _true_, so that at least one row has been fetched. Therefore, call [fetch] or [next] before this.
     * @return the names of the selected columns.
     * @throws naksha.base.NakshaException with error [ILLEGAL_STATE][naksha.base.NakshaError.ILLEGAL_STATE], if no row is selected, so [isRow] is _false_.
     */
    fun columnNames(): Array<String>

    /**
     * Tests if the current row has the given column.
     * @param name the name of the column
     * @return _true_ if the current row contains a column with the given name.
     * @throws IllegalStateException if the cursor is not positioned above a valid row, [isRow] returns _false_.
     */
    operator fun contains(name: String): Boolean

    /**
     * Tests if the current row has the given column.
     * @param column the column
     * @return _true_ if the current row contains the column.
     * @throws IllegalStateException if the cursor is not positioned above a valid row, [isRow] returns _false_.
     */
    operator fun contains(column: PgColumn): Boolean

    /**
     * Returns the column value of the current row.
     * @param name the name of the column
     * @return the value of the column; _null_ if the value is _null_ or no such column exists.
     * @throws IllegalStateException if the cursor is not positioned above a valid row, [isRow] returns _false_.
     */
    fun column(name: String): Any?

    /**
     * Returns the column value of the current row.
     * @param column the column
     * @return the value of the column; _null_ if the value is _null_ or no such column exists.
     * @throws IllegalStateException if the cursor is not positioned above a valid row, [isRow] returns _false_.
     */
    fun column(column: PgColumn): Any?

    /**
     * Returns the column value of the current row.
     * @param name the name of the column
     * @param alternative the value to return, if the column does not exist or the value is not of the desired type.
     * @return the value of the column or the [alternative], if the value is _null_ or no such column exists.
     * @throws IllegalStateException if the cursor is not positioned above a valid row, [isRow] returns _false_.
     */
    fun <T : Any> columnOr(name: String, alternative: T): T

    /**
     * Returns the column value of the current row.
     * @param name the name of the column
     * @return the value of the column.
     * @throws IllegalStateException if the cursor is not positioned above a valid row, [isRow] returns _false_.
     * @throws ClassCastException if casting the value failed.
     * @throws NullPointerException if the value is null.
     */
    operator fun <T : Any> get(name: String): T

    /**
     * Returns the column value of the current row.
     * @param column the column
     * @return the value of the column.
     * @throws IllegalStateException if the cursor is not positioned above a valid row, [isRow] returns _false_.
     * @throws ClassCastException if casting the value failed.
     * @throws NullPointerException if the value is null.
     */
    operator fun <T : Any> get(column: PgColumn): T

    /**
     * Convert the current row into a map, and return the corresponding proxy.
     * @param klass the proxy type to map.
     * @return the proxy about the row.
     * @throws IllegalStateException if the cursor is not positioned above a valid row, [isRow] returns _false_.
     */
    fun <T : AnyObject> map(@Suppress("NON_EXPORTABLE_TYPE") klass: KClass<T>): T

    /**
     * Closes the cursor.
     * @since 3.0
     * @see PgCursor
     */
    override fun close()
}