@file:OptIn(ExperimentalJsExport::class)

package com.here.naksha.lib.jbon

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * An API that grants access to native maps.
 */
@JsExport
interface INativeMap {

    /**
     * Tests whether the given object is a native map.
     * @param any The object to test.
     * @return true if the given map is a native map; false otherwise.
     */
    fun isMap(any: Any?): Boolean

    /**
     * Creates a new native map.
     * @return An empty native map.
     */
    fun newMap() : Any

    /**
     * Returns the amount of key-value pairs being in the given native map.
     * @param map The native map.
     * @throws IllegalArgumentException If the given map is no native map.
     */
    fun size(map: Any): Int

    /**
     * Tests if the given native map contains the given key.
     * @param map The native map.
     * @param key The key to test.
     * @return true if the map contains the key; false otherwise.
     * @throws IllegalArgumentException If the given map is no native map.
     */
    fun containsKey(map: Any, key:String): Boolean

    /**
     * Returns the value assigned to the given key.
     * @param map The native map.
     * @param key The key to query.
     * @return The value or _null_, if no such key exists.
     * @throws IllegalArgumentException If the given map is no native map.
     */
    fun get(map: Any, key: String): Any?

    /**
     * Assign the given value with the given key.
     * @param map The native map.
     * @param key The key to assign.
     * @param value The value to assign.
     * @return The value that was previously assigned or _null_, if no such key existed.
     * @throws IllegalArgumentException If the given map is no native map.
     */
    fun put(map: Any, key: String, value: Any?): Any?

    /**
     * Removes the given key assignment from the map.
     * @param map The native map.
     * @param key The key to remove.
     * @return The value that was assigned or _null_, if no such key existed.
     * @throws IllegalArgumentException If the given map is no native map.
     */
    fun remove(map: Any, key: String): Any?

    /**
     * Removes all key-value assignments from the map.
     * @param map The native map.
     * @throws IllegalArgumentException If the given map is no native map.
     */
    fun clear(map: Any)

    /**
     * Collects all keys that have assignments in the map and return them.
     * @param map The native map.
     * @return All keys that have assignments in the map.
     * @throws IllegalArgumentException If the given map is no native map.
     */
    fun keys(map:Any) : Array<String>
}