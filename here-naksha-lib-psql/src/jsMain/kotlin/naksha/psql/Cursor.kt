package naksha.psql

/**
 * A cursor as returned by [PreparedPlan.cursor].
 * @since 3.0
 */
external interface Cursor {
    /**
     * Moves the cursor.
     * @param nrows the amount of rows to move, negative values will move backward.
     * @since 3.0
     */
    fun move(nrows: Int)

    /**
     * Fetches a single row as object.
     * @return the next row as object with keys being the names; `null` if there is no more row.
     * @since 3.0
     */
    fun fetch(): dynamic

    /**
     * Fetches row(s) from the cursor.
     *
     * @param nrows fetches as many rows, up to the number of rows available, and returns an array of objects. A negative value will fetch backward.
     * @since 3.0
     */
    fun fetch(nrows: Int): Array<dynamic>

    /**
     * Closes the cursor.
     * @since 3.0
     */
    fun close()
}