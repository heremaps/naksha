@file:OptIn(ExperimentalJsExport::class)

package com.here.naksha.lib.jbon

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * A cursor.
 */
@JsExport
interface ISqlCursor {

    /**
     * Returns the position of the currently selected row.
     * @return The position of the currently selected row.
     */
    fun pos() : Int

    /**
     * Fetches the current row and moves the cursor forward.
     * @return The current row (as native map) or null, if there is no.
     */
    fun next() : Any?

    /**
     * Fetches the current row plus the given amount of rows (positive forward, negative backward) and returns them.
     * If the [amount] is zero and empty array is returned and the cursor is not moved.
     * @param amount The amount of rows to fetch.
     * @return The fetched rows.
     */
    fun fetch(amount: Int) : Array<Any>

    /**
     * Moves the cursor by the given amount of rows.
     * @param by The number of rows by which to move the cursor.
     */
    fun moveBy(by: Int)

    /**
     * Moves the cursor to the given position.
     * @param pos The position to move the cursor to.
     */
    fun moveTo(pos:Int)

    /**
     * Closes the cursor.
     */
    fun close()
}