@file:OptIn(ExperimentalJsExport::class)

package naksha.base

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * Standard declaration of a list of strings.
 * @since 3.0
 */
@JsExport
open class AnyObjectList() : PTypedArray<PAnyMap>(PAnyMap::class) {

    /**
     * Create an initialized string list.
     * @since 3.0
     */
    @JsName("fromAnyObjects")
    constructor(vararg objects: PAnyMap?) : this() {
        setCapacity(objects.size)
        for (i in 0 until objects.size) add(objects[i])
    }

    /**
     * Create an initialized string list.
     * @since 3.0
     */
    @JsName("fromList")
    constructor(list: List<PAnyMap?>) : this() {
        setCapacity(list.size)
        for (o in list) add(o)
    }

    /**
     * Adds the specified element to the end of this list.
     * @param obj the element to add.
     * @return this.
     * @since 3.0
     */
    fun append(obj: PAnyMap?): AnyObjectList {
        super.add(obj)
        return this
    }
}


