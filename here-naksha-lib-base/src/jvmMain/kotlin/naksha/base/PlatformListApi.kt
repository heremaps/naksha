package naksha.base

import kotlin.math.max

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual class PlatformListApi {
    // https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Array
    actual companion object PlatformListApiCompanion {
        @JvmStatic
        actual fun array_get_length(array: PlatformList?): Int {
            return (array as JvmList?)?.size ?: 0
        }

        @JvmStatic
        actual fun array_set_length(array: PlatformList?, length: Int) {
            require(array is JvmList)
            array.size = length
        }

        @JvmStatic
        actual fun array_clear(array: PlatformList?) {
            (array as JvmList?)?.clear()
        }

        @JvmStatic
        actual fun array_get(array: PlatformList?, i: Int): Any? {
            if (array == null) return null
            require(array is JvmList)
            if (i < 0) throw IndexOutOfBoundsException(i)
            return if (i < array.size) array[i] else null
        }

        @JvmStatic
        actual fun array_set(array: PlatformList?, i: Int, value: Any?): Any? {
            require(array is JvmList)
            if (i < 0) throw IndexOutOfBoundsException(i)
            array.ensureSize(i + 1)
            val old = array[i]
            array[i] = value
            return old
        }

        @JvmStatic
        actual fun array_delete(array: PlatformList?, i: Int): Any? {
            if (array == null) return null
            require(array is JvmList)
            if (i < 0) throw IndexOutOfBoundsException(i)
            return if (i < array.size) array.removeAt(i) else null
        }

        @JvmStatic
        actual fun array_splice(
            array: PlatformList?,
            start: Int,
            deleteCount: Int,
            vararg add: Any?
        ): PlatformList {
            require(array is JvmList)
            val result = JvmList(deleteCount)
            var i = if (start < 0) max(0, array.size + start) else start
            var delete = deleteCount
            while (delete-- > 0) result.add(array.removeAt(i))
            if (add.isNotEmpty()) {
                array.ensureSize(i + 1)
                var a = 0
                while (a < add.size) array.add(i++, add[a++])
            }
            return result
        }

        /**
         * Compares [searchElement] to elements of the array using strict equality (the same algorithm used by the === operator).
         * NaN values are never compared as equal, so [array_index_of] always returns -1 when [searchElement] is _NaN_.
         *
         * @param searchElement element to locate in the array.
         * @param fromIndex Optional, zero-based index at which to start searching, converted to an integer.
         * - Negative index counts back from the end of the array — if -length <= fromIndex < 0, fromIndex + length is used. Note, the array is still searched from front to back in this case.
         * - If fromIndex < -length or fromIndex is omitted, 0 is used, causing the entire array to be searched.
         * - If fromIndex >= length, the array is not searched and -1 is returned.
         * @return The first index of searchElement in the array; -1 if not found.
         */
        @JvmStatic
        actual fun array_index_of(
            array: PlatformList?,
            searchElement: Any?,
            fromIndex: Int
        ): Int = (array as JvmList).indexOfRange(searchElement, fromIndex)

        /**
         * Compares [searchElement] to elements of the array using strict equality (the same algorithm used by the === operator).
         * _NaN_ values are never compared as equal, so [array_last_index_of] always returns `-1` when [searchElement] is _NaN_.
         *
         * @param searchElement element to locate in the array.
         * @param fromIndex Optional, zero-based index at which to start searching backwards, converted to an integer.
         * - Negative index counts back from the end of the array — if -length <= fromIndex < 0, fromIndex + length is used.
         * - If fromIndex < -length, the array is not searched and -1 is returned. You can think of it conceptually as starting at a nonexistent position before the beginning of the array and going backwards from there. There are no array elements on the way, so searchElement is never found.
         * - If fromIndex >= length or fromIndex is omitted, length - 1 is used, causing the entire array to be searched. You can think of it conceptually as starting at a nonexistent position beyond the end of the array and going backwards from there. It eventually reaches the real end position of the array, at which point it starts searching backwards through the actual array elements.
         * @return The last index of searchElement in the array; -1 if not found.
         */
        @JvmStatic
        actual fun array_last_index_of(
            array: PlatformList?,
            searchElement: Any?,
            fromIndex: Int
        ): Int = (array as JvmList).lastIndexOfRange(searchElement, fromIndex)

        private val EMPTY_LIST = JvmList()

        /**
         * Returns an iterator above the values of the array.
         * @param array Base array to operate on.
         * @return The iterator above the values of the array.
         */
        @JvmStatic
        actual fun array_entries(array: PlatformList?): PlatformIterator<Any?> {
            return JvmListIterator(array ?: EMPTY_LIST)
        }

        /**
         * Appends values to the start of the array.
         * @param array Base array to operate on.
         * @param elements The elements to append.
         * @return The new length of the array.
         */
        @JvmStatic
        actual fun array_unshift(array: PlatformList?, vararg elements: Any?): Int {
            require(array is JvmList)
            var i = elements.size - 1
            while (i >= 0) array.add(0, elements[i--])
            return array.size
        }

        /**
         * Appends values to the end of the array.
         * @param array Base array to operate on.
         * @param elements The elements to append.
         * @return The new length of the array.
         */
        @JvmStatic
        actual fun array_push(array: PlatformList?, vararg elements: Any?): Int {
            require(array is JvmList)
            array.addAll(elements)
            return array.size
        }

        /**
         * Removes the element at the zeroth index and shifts the values at consecutive indexes down, then returns the removed
         * value. If the length is 0, _undefined_ is returned.
         * @param array Base array to operate on.
         */
        @JvmStatic
        actual fun array_shift(array: PlatformList?): Any? {
            TODO("Not yet implemented")
        }

        /**
         * Removes the last element from the array and returns that value. Calling [array_pop] on an empty array, returns _undefined_.
         * @param array Base array to operate on.
         */
        @JvmStatic
        actual fun array_pop(array: PlatformList?): Any? {
            TODO("Not yet implemented")
        }

        /**
         * Sort the elements of this array in place and return the reference to this array, sorted. The default sort order is
         * ascending, built upon converting the elements into strings, then comparing their sequences of UTF-16 code units values.
         *
         * The time and space complexity of the sort cannot be guaranteed as it depends on the implementation.
         *
         * To sort the elements in an array without mutating the original array, use [array_to_sorted].
         * @param array Base array to operate on.
         * @param compareFn The (optional) function to compare; if _null_ sorting will be ascending by [toString] UTF-16 code units.
         * @return _this_.
         */
        @JvmStatic
        actual fun array_sort(array: PlatformList?, compareFn: ((Any?, Any?) -> Int)?): PlatformList {
            TODO("Not yet implemented")
        }

        /**
         * This is the copying version of the [array_sort] method. It returns a new array with the elements sorted in ascending order
         * or sorting using the given compare-function.
         *
         * @param array Base array to operate on.
         * @param compareFn The (optional) function to compare; if _null_ sorting will be ascending by [toString] UTF-16 code units.
         * @return A copy of this array, but sorted.
         */
        @JvmStatic
        actual fun array_to_sorted(array: PlatformList?, compareFn: ((Any?, Any?) -> Int)?): PlatformList {
            TODO("Not yet implemented")
        }

        @JvmStatic
        actual fun array_retain_all(array: PlatformList?, vararg keep: Any?): Boolean {
            if (array == null) return false
            return (array as JvmList).retainAll(keep)
        }
    }
}