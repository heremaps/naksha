@file:OptIn(ExperimentalJsExport::class)

package com.here.naksha.lib.jbon

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * API to grant access to native lists. All methods, except for [isList], will throw an [IllegalArgumentException],
 * if tried to operate on an object, that is no native list.
 */
@JsExport
interface INativeList {
    /**
     * Tests whether the given object is a list.
     * @param any The object to test.
     * @return true if the given object is a native list; false otherwise.
     */
    fun isList(any: Any?): Boolean

    /**
     * Creates a native empty list.
     * @return The native empty list.
     */
    fun newList(): Any

    /**
     * Returns the size of the given native list.
     * @param list The native list.
     * @return The size of the list.
     * @throws IllegalArgumentException If the given list is no native list.
     */
    fun size(list: Any): Int

    /**
     * Change the size of the native list. If expanded, _null_ is added, if reduced in size, elements are
     * removed from the end of the list.
     * @param list The native list.
     * @param size The new size, must be greater or equal to zero.
     * @throws IllegalArgumentException If the given list is no native list or the size is less than zero or too large.
     */
    fun setSize(list: Any, size: Int)

    /**
     * Returns the element from the given index.
     * @param list The list to query.
     * @param index The index to query (0 to size - 1).
     * @return The value.
     * @throws IllegalArgumentException If the given list is no native list or the given index is out of bounds.
     */
    fun get(list: Any, index: Int): Any?

    /**
     * Sets the element at the given index to the given value.
     * @param list The list to query.
     * @param index The index to query (0 to size - 1).
     * @param value The value to set.
     * @return The value that was stored there before.
     * @throws IllegalArgumentException If the given list is no native list or the given index is out of bounds.
     */
    fun set(list: Any, index: Int, value: Any?): Any?

    /**
     * Adds the given values to the end of the list.
     * @param list The list to query.
     * @param values The values to add.
     * @throws IllegalArgumentException If the given list is no native list.
     */
    fun add(list: Any, vararg values: Any?)

    fun splice(list: Any, start: Int, delete: Int, vararg add: Any?): Any?

    /**
     * Clears the list, which should be the as setting the size to zero.
     * @param list The list to query.
     * @throws IllegalArgumentException If the given list is no native list.
     */
    fun clear(list: Any)
}