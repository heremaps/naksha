@file:Suppress("OPT_IN_USAGE")

package naksha.base

import kotlin.js.JsExport

/**
 * Standard definition of a list that can hold any value.
 * - [AnyList]
 * - [AnyMap]
 * - [AnyObject]
 */
@JsExport
open class AnyList : ListProxy<Any>(Any::class) {
    /**
     * Adds the specified element to the end of this list.
     * @param element the element to add.
     * @return this.
     * @since 3.0
     */
    fun append(element: Any?): AnyList {
        super.add(element)
        return this
    }
}
