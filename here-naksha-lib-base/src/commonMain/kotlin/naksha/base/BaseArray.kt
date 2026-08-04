@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING", "OPT_IN_USAGE")

package naksha.base

import kotlin.reflect.KClass

/**
 * A JavaScript-compatible ordered list.
 * @since 3.0
 */
expect class BaseArray() : BaseObject {

    /**
     * Create a new array with initial value.
     * @param elements the elements to initialize the array with.
     * @since 3.0
     */
    constructor(vararg elements: Any?)

    /**
     * Creates an array with pre-defined capacity, helpful for bulk operations where the size of the array is knows upfront, prevents resizing.
     * @param capacity the amount of elements expected to be added. Implementations should pre-allocate enough space for at least the given amount of elements.
     * @since 3.0
     */
    constructor(capacity: Int)

    /**
     * The backing array.
     *
     * Beware that only values not being [Base.UNDEFINED] are valid entries. The size of the array is the amount of elements not being [Base.UNDEFINED]. If the data is read in [atomic] mode, it is strongly recommended to make a copy of the data array to the stack before reading from it, this will ensure a snapshot.
     *
     * **The content of the array must not be modified from external!**
     *
     * @since 3.0
     */
    val array: Array<Any?>

    /**
     * If the array is [atomic], then this is the recommended way to manually perform transactional state changes.
     * @param expectedData the `data` that was read.
     * @param newData the new data array to set.
     * @return `true` when [array] is now `newData`; `false` if another thread concurrently modified [array].
     * @since 3.0
     */
    fun copyAndSetData(expectedData: Array<Any?>, newData: Array<Any?>): Boolean

    /**
     * Create a base-array backed by the given array.
     *
     * Beware calling this method hands over ownership of the given array to this [BaseArray] instance. The caller must no longer hold a reference to the array nor use it. Doing otherwise can cause undefined behavior.
     *
     * This is a zero-copy method.
     * @param data the array with the elements.
     * @return this with [data] being the given `data` array.
     * @since 3.0
     */
    fun mapArray(data: Array<Any?>): BaseArray

    /**
     * Set [data] to a range copy of the given array.
     *
     * @param data the array with the elements.
     * @param from the index of the first element to copy; defaults to `0`.
     * @param to the index of the first element **NOT** to copy; defaults to `data.length`.
     * @param capacity the total amount of space that should be reserved for the new [BaseArray], defaults to `to - from + 4`
     * @return this with updated [data].
     * @since 3.0
     */
    fun withData(data: Array<Any?>, from: Int = 0, to: Int = data.size, capacity: Int = to - from + 4): BaseArray

    /**
     * Returns a copy of the backing [array] array.
     * @param from the index of the first element to copy; defaults to `0`.
     * @param to the index of the first element **NOT** to copy; defaults to `data.length`.
     * @return the array with the copy.
     * @since 3.0
     */
    fun copy(from: Int = 0, to: Int = array.size): Array<Any?>

    /**
     * Returns a copy of the backing [array] array.
     * @param elementsType the type of the elements to return.
     * @param from the index of the first element to copy; defaults to `0`.
     * @param to the index of the first element **NOT** to copy; defaults to `data.length`.
     * @return the array with the copy.
     * @since 3.0
     */
    fun <T: Any> copyAs(elementsType: KClass<T>, from: Int = 0, to: Int = array.size): Array<T?>

    /**
     * The number of elements in the array.
     *
     * Setting this to a smaller value truncates the array; setting it to a larger value pads with `null`.
     * @since 3.0
     */
    var length: Int

    /**
     * The current allocated capacity of the underlying array storage.
     * Setting this to a value greater than the current capacity pre-allocates storage without changing [length].
     * Setting it to a value less than or equal to the current capacity is a no-op (capacity never shrinks).
     * @since 3.0
     */
    var capacity: Int

    /**
     * Returns the element at `position`, or [Base.UNDEFINED] if the index is out of bounds.
     * @param position zero-based index.
     * @return the element or [Base.UNDEFINED].
     * @since 3.0
     */
    fun get(position: Int): Any?

    /**
     * Sets the element at `index` to `value`. If `index` is beyond the current [length] the array is automatically padded with `null` values up to `index`.
     * @param index zero-based index.
     * @param value the value to store.
     * @return the previous value at `index`, or [Base.UNDEFINED], if no value is available at this position.
     * @since 3.0
     */
    fun set(index: Int, value: Any?): Any?

    /**
     * Appends `value` to the end of the array.
     * @param value the value to append.
     * @return the new [length] of the array.
     * @since 3.0
     */
    fun push(value: Any?): Int

    /**
     * Removes and returns the last element of the array, or [Base.UNDEFINED] if empty.
     * @return the removed element, or [Base.UNDEFINED].
     * @since 3.0
     */
    fun pop(): Any?

    /**
     * Inserts `value` at the beginning of the array, shifting existing elements right.
     * @param value the value to insert.
     * @return the new [length] of the array.
     * @since 3.0
     */
    fun unshift(value: Any?): Int

    /**
     * Removes and returns the first element of the array, shifting remaining elements left.
     * @return the removed element, or [Base.UNDEFINED] if the array was empty.
     * @since 3.0
     */
    fun shift(): Any?

    /**
     * Removes `deleteCount` elements starting at `start`, optionally inserting `items` in their place.
     * @param start zero-based index at which to start changing the array.
     * @param deleteCount number of elements to remove.
     * @param items elements to insert at [start].
     * @return an array containing the removed elements.
     * @since 3.0
     */
    fun splice(start: Int, deleteCount: Int, vararg items: Any?): Array<Any?>

    /**
     * Returns the first index of [element] in the array, or `-1` if not found.
     * @param element the element to search for.
     * @param fromIndex the index to start searching from (default `0`).
     * @return the index, or `-1`.
     * @since 3.0
     */
    fun indexOf(element: Any?, fromIndex: Int = 0): Int

    /**
     * Returns the last index of [element] in the array, or `-1` if not found.
     * @param element the element to search for.
     * @param fromIndex the index to start searching backwards from (default: last index).
     * @return the index, or `-1`.
     * @since 3.0
     */
    fun lastIndexOf(element: Any?, fromIndex: Int = Int.MAX_VALUE): Int

    /**
     * Removes all elements from the array.
     * @since 3.0
     */
    fun clear()
}
