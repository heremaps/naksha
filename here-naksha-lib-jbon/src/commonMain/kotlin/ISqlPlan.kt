@file:OptIn(ExperimentalJsExport::class)

package com.here.naksha.lib.jbon

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * The prepared plan.
 */
@JsExport
interface ISqlPlan {
    /**
     * Execute the prepared plan with the given arguments. The types must match to the prepared statement.
     * @param args The arguments to be set at $n position, where $1 is the first array element.
     * @return The result-set.
     */
    fun execute(args: Array<Any?>): ISqlResultSet

    /**
     * Execute the prepared plan with the given arguments. The types must match to the prepared statement.
     * @param args The arguments to be set at $n position, where $1 is the first array element.
     * @return A cursor into the result-set.
     */
    fun cursor(args: Array<Any?>): ISqlCursor

    /**
     * Closes (free) the plan.
     */
    fun close()
}