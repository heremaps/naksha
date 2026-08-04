package naksha.base

/**
 * A simple interface to read `JSON` arrays.
 * @since 3.0
 */
interface IArray: IObject {
    /**
     * The number of elements in the array.
     * @since 3.0
     */
    val length: Int

    /**
     * Returns the element at `position`, or `null` if the index is out of bounds.
     * @param position zero-based index.
     * @return the element or [Base.UNDEFINED], if the position is out of bounds.
     * @since 3.0
     */
    operator fun get(position: Int): Any?

    /**
     * Returns the first index of `element` in the array, or `-1` if not found.
     * @param element the element to search for.
     * @param fromIndex the index to start searching from (default `0`).
     * @return the index, or `-1`.
     * @since 3.0
     */
    fun indexOf(element: Any?, fromIndex: Int = 0): Int

    /**
     * Returns the last index of `element` in the array, or `-1` if not found.
     * @param element the element to search for.
     * @param fromIndex the index to start searching backwards from (default: `length - 1`).
     * @return the index, or `-1`.
     * @since 3.0
     */
    fun lastIndexOf(element: Any?, fromIndex: Int = length - 1): Int

}