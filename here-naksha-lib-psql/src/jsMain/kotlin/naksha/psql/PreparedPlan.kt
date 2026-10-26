package naksha.psql

/**
 * A prepared plan as returned by [Plv8.prepare].
 * @since 3.0
 */
external interface PreparedPlan {
    /**
     * Executes the prepared statement.
     *
     * @return the result of this method is the same as [Plv8.execute].
     * @see Plv8.execute
     * @since 3.0
     */
    fun execute(): dynamic

    /**
     * Executes the prepared statement.
     *
     * @param args the same as what would be required for [Plv8.execute].
     * @return the result of this method is the same as [Plv8.execute].
     * @see Plv8.execute
     * @since 3.0
     */
    fun execute(args: Array<dynamic?>): dynamic

    /**
     * Opens a cursor form the prepared statement.
     *
     * @return the cursor, must be closed by [Cursor.close] before leaving the function.
     * @since 3.0
     */
    fun cursor(): Cursor

    /**
     * Opens a cursor form the prepared statement.
     *
     * @param args the same as what would be required for [Plv8.execute] and [execute].
     * @return the cursor, must be closed by [Cursor.close] before leaving the function.
     * @since 3.0
     */
    fun cursor(args: Array<dynamic?>): Cursor

    /**
     * Frees the prepared statement.
     * @since 3.0
     */
    fun free()
}