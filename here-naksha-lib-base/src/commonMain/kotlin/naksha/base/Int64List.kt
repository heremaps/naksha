@file:Suppress("OPT_IN_USAGE")

package naksha.base

import kotlin.js.JsExport

/**
 * Standard declaration of a list of 64-bit integers.
 */
@JsExport
open class Int64List : ListProxy<Long>(Long::class) {
    /**
     * Adds the specified element to the end of this list.
     * @param element the element to add.
     * @return this.
     * @since 3.0
     */
    fun append(element: Long?): Int64List {
        super.add(element)
        return this
    }

}
