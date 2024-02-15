@file:OptIn(ExperimentalJsExport::class)

package com.here.naksha.lib.jbon

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * An API that grants access to native maps.
 */
@JsExport
interface IMapApi {

    /**
     * Tests whether the given object is a string-map that can be cast into a [IMap] using [asMap].
     * @param any The object to test.
     * @return true if the given object can be cast using [asMap]; false otherwise.
     */
    fun isMap(any: Any?): Boolean

    /**
     * Cast the given object to a string-map.
     * @param any The object to cast.
     * @return The cast object.
     * @throws IllegalArgumentException If the given object can't be cast to a string-map.
     */
    fun asMap(any: Any?): IMap

    /**
     * Creates a new native map.
     * @return An empty native map.
     */
    fun newMap(): IMap

    /**
     * Returns the amount of key-value pairs being in the given native map.
     * @param map The native map.
     * @throws IllegalArgumentException If the given map is no native map.
     */
    fun size(map: IMap): Int

    /**
     * Tests if the given native map contains the given key.
     * @param map The native map.
     * @param key The key to test.
     * @return true if the map contains the key; false otherwise.
     * @throws IllegalArgumentException If the given map is no native map.
     */
    fun containsKey(map: IMap, key: String): Boolean

    /**
     * Returns the value assigned to the given key.
     * @param map The native map.
     * @param key The key to query.
     * @return The value or _null_, if no such key exists.
     * @throws IllegalArgumentException If the given map is no native map.
     */
    fun get(map: IMap, key: String): Any?

    /**
     * Assign the given value with the given key.
     * @param map The native map.
     * @param key The key to assign.
     * @param value The value to assign.
     * @return The value that was previously assigned or _null_, if no such key existed.
     * @throws IllegalArgumentException If the given map is no native map.
     */
    fun put(map: IMap, key: String, value: Any?): Any?

    /**
     * Removes the given key assignment from the map.
     * @param map The native map.
     * @param key The key to remove.
     * @return The value that was assigned or _null_, if no such key existed.
     * @throws IllegalArgumentException If the given map is no native map.
     */
    fun remove(map: IMap, key: String): Any?

    /**
     * Removes all key-value assignments from the map.
     * @param map The native map.
     * @throws IllegalArgumentException If the given map is no native map.
     */
    fun clear(map: IMap)

    /**
     * Collects all keys that have assignments in the map and return them.
     * @param map The native map.
     * @return All keys that have assignments in the map.
     * @throws IllegalArgumentException If the given map is no native map.
     */
    fun keys(map: IMap): Array<String>

    /**
     * Creates a new map iterator.
     * @param map The native map to iterate.
     * @return The map iterator.
     * @throws IllegalArgumentException If the given map is no native map.
     */
    fun iterator(map: IMap): IMapIterator
}