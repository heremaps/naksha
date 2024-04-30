@file:Suppress("OPT_IN_USAGE")

package com.here.naksha.lib.base

import kotlin.js.JsExport

/**
 * A platform independent iterator API.
 */
@JsExport
interface PIterator<K, V> {
    /**
     * Loads the next object element and returns _true_ if the iteration has more elements.
     * @return _true_ if the next element has been loaded.
     */
    fun loadNext(): Boolean

    /**
     * Returns _true_, if a value is loaded; _false_ otherwise.
     * @return _true_, if a value is loaded; _false_ otherwise.
     */
    fun isLoaded(): Boolean

    /**
     * Returns the index of the loaded element in the iteration.
     * @return the index of the loaded element in the iteration.
     * @throws IllegalStateException - if the internal position is out of bounds ([loadNext] not yet invoked or returned _false_).
     */
    fun getKey() : K

    /**
     * Returns the loaded element in the iteration.
     * @return the loaded element in the iteration.
     * @throws IllegalStateException - if the internal position is out of bounds ([loadNext] not yet invoked or returned _false_).
     */
    fun getValue(): V
}