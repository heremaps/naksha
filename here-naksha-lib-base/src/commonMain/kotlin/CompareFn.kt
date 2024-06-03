@file:Suppress("OPT_IN_USAGE")

package com.here.naksha.lib.base

import kotlin.js.JsExport

/**
 * The compare function.
 */
@JsExport
interface CompareFn<T: Any> {
    /**
     * A function that determines the order of the elements. The function is called with the following arguments:
     * - a: The first element for comparison. Will never be _undefined_.
     * - b: The second element for comparison. Will never be _undefined_.
     *
     * It should return a number where:
     *
     * - A negative value indicates that a should come before b.
     * - A positive value indicates that a should come after b.
     * - Zero or NaN indicates that a and b are considered equal.
     *
     * To memorize this, remember that (a, b) => a - b sorts numbers in ascending order.
     */
    fun compare(a: T?, b: T?): Int
}
