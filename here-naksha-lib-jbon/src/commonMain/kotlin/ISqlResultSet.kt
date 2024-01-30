@file:OptIn(ExperimentalJsExport::class)

package com.here.naksha.lib.jbon

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * A result-set returned by an SQL [ISql.execute].
 */
@JsExport
interface ISqlResultSet {
    /**
     * Returns the number of affected rows, if this was an update without returning.
     * @return The number of affected rows (0 to n); -1 if this was a select or contained returning.
     */
    fun affectedRows() : Int

    /**
     * Tests whether rows were returned.
     * @return true if rows were returned; false otherwise.
     */
    fun hasRows() : Boolean

    /**
     * Returns the returned rows.
     * @return An array of native maps that represent the returned rows.
     */
    fun rows() : Array<Any>
}