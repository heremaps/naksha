@file:OptIn(ExperimentalJsExport::class)

package naksha.plv8

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * A cursor as it is returned by the PLV8 engine.
 */
@JsExport
interface IPlv8Cursor {

    /**
     * Fetches the current row returns it as native map.
     * @return The fetched row or _null_, if there are no more rows.
     */
    fun fetch(): Any?

    /**
     * Moves the cursor by the given amount of rows.
     * @param nrows The number of rows by which to move the cursor (negative moves backwards).
     */
    fun move(nrows: Int)

    /**
     * Closes the cursor.
     */
    fun close()
}