@file:Suppress("OPT_IN_USAGE")

package naksha.base

import kotlin.js.JsExport

/**
 * Standard definition of a list that can hold any value as it generally appears in raw `JSON`.
 * @see PAnyMap
 */
@JsExport
open class PAnyArray : PTypedArray<Any>(Any::class) {
    /**
     * Adds the specified element to the end of this list.
     * @param element the element to add.
     * @return this.
     * @since 3.0
     */
    fun append(element: Any?): PAnyArray {
        super.add(element)
        return this
    }

}
