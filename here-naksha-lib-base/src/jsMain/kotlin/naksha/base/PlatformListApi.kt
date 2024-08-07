@file:Suppress("OPT_IN_USAGE", "EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package naksha.base

@JsExport
actual class PlatformListApi {
    // https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Array
    actual companion object PlatformListApiCompanion {
        @JsStatic
        actual fun array_get_length(array: PlatformList?): Int = array.asDynamic().length.unsafeCast<Int>()

        @JsStatic
        actual fun array_set_length(array: PlatformList?, length: Int) {
            array.asDynamic().length = length
        }

        @JsStatic
        actual fun array_get_capacity(array: PlatformList?): Int = (array.asDynamic()?.length ?: 0).unsafeCast<Int>()

        @JsStatic
        actual fun array_set_capacity(array: PlatformList?, capacity: Int) {}

        @JsStatic
        actual fun array_clear(array: PlatformList?) = array_set_length(array, 0)

        @JsStatic
        actual fun array_get(array: PlatformList?, i: Int): Any? = array.asDynamic()[i].unsafeCast<Any?>()

        @JsStatic
        actual fun array_set(array: PlatformList?, i: Int, value: Any?): Any? {
            require(i >= 0) { "array_set: Invalid index $i, must be >= 0" }
            val arr = array.asDynamic()
            val old = arr[i]
            arr[i] = value
            return old
        }

        @JsStatic
        actual fun array_delete(array: PlatformList?, i: Int): Any? = array.asDynamic().splice(i, 1)[0]

        @JsStatic
        actual fun array_splice(
            array: PlatformList?,
            start: Int,
            deleteCount: Int,
            vararg add: Any?
        ): PlatformList = js("eval('array.splice(start, deleteCount, ...add)')").unsafeCast<PlatformList>()

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
        @JsStatic
        actual fun array_index_of(
            array: PlatformList?,
            searchElement: Any?,
            fromIndex: Int
        ): Int = array.asDynamic().indexOf(searchElement, fromIndex).unsafeCast<Int>()

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
        @JsStatic
        actual fun array_last_index_of(
            array: PlatformList?,
            searchElement: Any?,
            fromIndex: Int
        ): Int = array.asDynamic().lastIndexOf(searchElement, fromIndex).unsafeCast<Int>()

        /**
         * Returns an iterator above the values of the array.
         * @return The iterator above the values of the array.
         */
        @JsStatic
        actual fun array_entries(array: PlatformList?): PlatformIterator<Any?> = js("array[Symbol.iterator]()")
            .unsafeCast<PlatformIterator<Any?>>()

        /**
         * Appends values to the start of the array.
         * @param elements The elements to append.
         * @return The new length of the array.
         */
        @JsStatic
        actual fun array_unshift(array: PlatformList?, vararg elements: Any?): Int = js("eval('array.unshift(...elements)')")
            .unsafeCast<Int>()

        /**
         * Appends values to the end of the array.
         * @param array Array to operate on.
         * @param elements The elements to append.
         * @return The new length of the array.
         */
        @JsStatic
        actual fun array_push(array: PlatformList?, vararg elements: Any?): Int = js("eval('array.push(...elements)')").unsafeCast<Int>()

        /**
         * Removes the element at the zeroth index and shifts the values at consecutive indexes down, then returns the removed
         * value. If the length is 0, _undefined_ is returned.
         */
        @JsStatic
        actual fun array_shift(array: PlatformList?): Any? = js("array.shift()").unsafeCast<Any?>()

        /**
         * Removes the last element from the array and returns that value. Calling [array_pop] on an empty array, returns _undefined_.
         */
        @JsStatic
        actual fun array_pop(array: PlatformList?): Any? = js("array.pop()").unsafeCast<Any?>()

        /**
         * Sort the elements of this array in place and return the reference to this array, sorted. The default sort order is
         * ascending, built upon converting the elements into strings, then comparing their sequences of UTF-16 code units values.
         *
         * The time and space complexity of the sort cannot be guaranteed as it depends on the implementation.
         *
         * To sort the elements in an array without mutating the original array, use [array_to_sorted].
         * @param compareFn The (optional) function to compare; if _null_ sorting will be ascending by [toString] UTF-16 code units.
         * @return _this_.
         */
        @JsStatic
        actual fun array_sort(array: PlatformList?, compareFn: ((Any?, Any?) -> Int)?): PlatformList {
            TODO("Not yet implemented array_sort")
        }

        /**
         * This is the copying version of the [array_sort] method. It returns a new array with the elements sorted in ascending order
         * or sorting using the given compare-function.
         *
         * @param compareFn The (optional) function to compare; if _null_ sorting will be ascending by [toString] UTF-16 code units.
         * @return A copy of this array, but sorted.
         */
        @JsStatic
        actual fun array_to_sorted(array: PlatformList?, compareFn: ((Any?, Any?) -> Int)?): PlatformList {
            TODO("Not yet implemented array_to_sorted")
        }

        @JsStatic
        actual fun array_retain_all(array: PlatformList?, vararg keep: Any?): Boolean {
            val arr = array.asDynamic()
            if (arr === null || arr === undefined || arr.length == 0) return false
            if (keep.isEmpty()) {
                arr.length = 0
                return true
            }
            val copy = js("[]").unsafeCast<dynamic>()
            var i = 0
            while (i < arr.length.unsafeCast<Int>()) {
                val value = arr[i++]
                var keepIt = false
                for (item in keep) {
                    if (item == value) {
                        keepIt = true
                        break
                    }
                }
                if (keepIt) copy.push(value)
            }
            if (arr.length > copy.length) {
                // Note: arr.splice(0, arr.length, ...copy) does work as well, but is slower!
                arr.length = 0
                eval("arr.push(...copy)")
                return true
            }
            return false
        }
    }
}