@file:Suppress("OPT_IN_USAGE")

package naksha.base

import kotlin.js.JsExport

/**
 * Standard declaration of a list of integers.
 */
@JsExport
open class IntList : ListProxy<Int>(Int::class) {
    /**
     * Adds the specified element to the end of this list.
     * @param element the element to add.
     * @return this.
     * @since 3.0
     */
    fun append(element: Int?): IntList {
        super.add(element)
        return this
    }

}

