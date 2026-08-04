package naksha.base

/**
 * A simple interface to read or mutate an `JSON` array.
 * @since 3.0
 */
interface IMutableArray: IArray {
    /**
     * Change counter.
     * @since 3.0
     */
    val version: Int

    /**
     * The number of elements in the array.
     *
     * Setting this to a smaller value truncates the array; setting it to a larger value pads with `null`.
     * @since 3.0
     */
    override var length: Int

    /**
     * The current allocated capacity of the underlying array storage.
     *
     * Setting this to a value greater than the current capacity pre-allocates storage without changing [length]. Setting it to a value less than or equal to the current [length] will not truncate the array, but may compact the underlying data array. There is no guarantee for capacity control support, it should be treated as a hint to what is needed to allow implementations to optimize for this.
     * @since 3.0
     */
    var capacity: Int

    /**
     * Sets the element at `index` to `value`. If `index` is beyond the current length the array is automatically padded with `null` values up to `index`.
     * @param index zero-based index.
     * @param value the value to store.
     * @since 3.0
     */
    operator fun set(index: Int, value: Any?)

    /**
     * Appends [value] to the end of the array.
     * @param value the value to append.
     * @return the new length of the array.
     * @since 3.0
     */
    fun push(value: Any?): Int

    /**
     * Removes and returns the last element of the array, or _null_ if empty.
     * @return the removed element, or _null_.
     * @since 3.0
     */
    fun pop(): Any?

    /**
     * Inserts [value] at the beginning of the array, shifting existing elements right.
     * @param value the value to insert.
     * @return the new length of the array.
     * @since 3.0
     */
    fun unshift(value: Any?): Int

    /**
     * Removes and returns the first element of the array, shifting remaining elements left.
     * Returns _null_ if the array is empty.
     * @return the removed element, or _null_.
     * @since 3.0
     */
    fun shift(): Any?

    /**
     * Removes [deleteCount] elements starting at [start], optionally inserting [items] in their place.
     * @param start zero-based index at which to start changing the array.
     * @param deleteCount number of elements to remove.
     * @param items elements to insert at [start].
     * @return a [BaseArray] containing the removed elements.
     * @since 3.0
     */
    fun splice(start: Int, deleteCount: Int, vararg items: Any?): BaseArray

    /**
     * Removes all elements from the array.
     * @since 3.0
     */
    fun clear()
}