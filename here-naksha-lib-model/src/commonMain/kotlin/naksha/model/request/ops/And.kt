@file:Suppress("OPT_IN_USAGE")

package naksha.model.request.ops

import naksha.base.NotNullProperty
import kotlin.js.JsExport

/**
 * Logical AND.
 * @since 3.0
 */
@JsExport
class And() : Op() {
    companion object And_C {
        private val VALUES = NotNullProperty<And, OpList>(OpList::class) { _, _ -> OpList() }
    }

    constructor(vararg children: Op) : this() {
        this.op = AND
        val _children = this.children
        for (arg in children) _children.add(arg)
    }

    /**
     * The operation arguments, so the sub-operations to logically AND combine.
     * @since 3.0
     */
    var children: OpList by VALUES
}
