@file:Suppress("OPT_IN_USAGE")

package naksha.base

import kotlin.js.JsExport

/**
 * Standard declaration of a list of doubles.
 */
@JsExport
open class DoubleList : PTypedArray<Double>(Double::class) {
    /**
     * Adds the specified element to the end of this list.
     * @param element the element to add.
     * @return this.
     * @since 3.0
     */
    fun append(element: Double?): DoubleList {
        super.add(element)
        return this
    }

}

