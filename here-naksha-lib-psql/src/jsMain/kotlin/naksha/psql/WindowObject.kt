package naksha.psql

/**
 * You can define user-defined window functions with [Plv8]. It wraps the C-level window function API to support full functionality. To create one, first obtain a window object by calling [Plv8.get_window_object], which provides this interface.
 *
 * - [PostgresQL Window Tutorial](https://www.postgresql.org/docs/current/tutorial-window.html).
 * - [PostgresQL Window Funtions](https://www.postgresql.org/docs/current/functions-window.html)
 * @since 3.0
 */
external interface WindowObject {
    val SEEK_HEAD: SeekType
    val SEEK_CURRENT: SeekType
    val SEEK_TAIL: SeekType

    /**
     * Returns the current position in the partition, starting from 0.
     * @return the current position in the partition, starting from 0.
     * @since 3.0
     */
    fun get_current_position(): Int

    /**
     * Returns the number of rows in the partition.
     * @return the number of rows in the partition.
     * @since 3.0
     */
    fun get_partition_row_count(): Int

    /**
     * Sets the mark at the specified row. Rows above this position will be gone and no longer accessible later.
     * @param pos the position.
     * @since 3.0
     */
    fun set_mark_position(pos: Int)

    /**
     * Returns true if the rows at pos1 and pos2 are peers.
     * @param pos1 position 1.
     * @param pos2 position 2.
     * @return _true_ if the rows at `pos1` and `pos2` are peers.
     * @since 3.0
     */
    fun rows_are_peers(pos1: Int, pos2: Int): Boolean

    /**
     * Returns the value of the argument in [argno] to this function at the [relpos] row from [seektype] in the current partition.
     * @param argno the argument number of the window; starting from `0`.
     * @param relpos the row position.
     * @param seektype can be [SEEK_HEAD], [SEEK_CURRENT], or [SEEK_TAIL].
     * @param mark_pos if _true_, the row the argument is fetched from is marked.
     * @return If the specified row is out of the partition/frame, the returned value will be undefined.
     * @since 3.0
     */
    fun get_func_arg_in_partition(argno: Int, relpos: Int, seektype: SeekType, mark_pos: Boolean): dynamic

    /**
     * Returns the value of the argument in [argno] to this function at the [relpos] row from [seektype] in the current frame.
     * @param argno the argument number of the window; starting from `0`.
     * @param relpos the row position.
     * @param seektype can be [SEEK_HEAD], [SEEK_CURRENT], or [SEEK_TAIL].
     * @param mark_pos if _true_, the row the argument is fetched from is marked.
     * @return If the specified row is out of the partition/frame, the returned value will be undefined.
     * @since 3.0
     */
    fun get_func_arg_in_frame(argno: Int, relpos: Int, seektype: SeekType, mark_pos: Boolean): dynamic

    /**
     * Returns the value of the argument in [argno] to this function at the current row. Note that the returned value will be the same as the argument variable of the function.
     * @param argno the argument number of the window; starting from `0`.
     * @return the `argno` argument number of the window.
     * @since 3.0
     */
    fun get_func_arg_in_current(argno: Int): dynamic

    /**
     * Returns partition-local value.
     *
     * The same as with a `get_partition_local(1000)`.
     * @return the partition-local value, which is released at the end of the current partition. If nothing is stored, [undefined] is returned.
     * @since 3.0
     */
    fun get_partition_local(): dynamic

    /**
     * Returns partition-local value.
     * @param size the byte size of the allocated memory in the first call. Once the memory is allocated, the size will not change.
     * @return the partition-local value, which is released at the end of the current partition. If nothing is stored, [undefined] is returned.
     * @since 3.0
     */
    fun get_partition_local(size: Int): dynamic

    /**
     * Stores the partition-local value, which you can retrieve later with [get_partition_local].
     *
     * This function internally uses `JSON.stringify()` to serialize the object, so if you pass a value that is not able to be serialized it may end up being an unexpected value. If the size of a serialized value is more than the allocated memory, it will throw an exception.
     * @param obj the object to store.
     * @since 3.0
     */
    fun set_partition_local(obj: dynamic)
}