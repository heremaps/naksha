@file:OptIn(ExperimentalJsExport::class)

package com.here.naksha.lib.plv8

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * The prepared plan as returned by the PLV8 engine.
 */
@JsExport
interface IPlv8Plan {
    /**
     * Execute the prepared plan with the given arguments. The types must match to the prepared statement.
     * @param args The arguments to be set at $n position, where $1 is the first array element.
     * @return Either the number of affected rows or the rows.
     */
    fun <T> execute(args: Array<Any?>? = null): T

    /**
     * Execute the prepared plan with the given arguments. The types must match to the prepared statement.
     * @param args The arguments to be set at $n position, where $1 is the first array element.
     * @return A cursor into the result-set.
     */
    fun cursor(args: Array<Any?>? = null): IPlv8Cursor

    /**
     * Frees the plan.
     */
    fun free()
}