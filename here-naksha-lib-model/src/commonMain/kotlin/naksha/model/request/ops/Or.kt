@file:Suppress("OPT_IN_USAGE")

package naksha.model.request.ops

import naksha.base.NotNullProperty
import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * Logical OR.
 * @since 3.0
 */
@JsExport
class Or() : Op() {
    companion object Or_C {
        private val VALUES = NotNullProperty<Or, OpList>(OpList::class) { _, _ -> OpList() }
    }

    @JsName("of")
    constructor(vararg children: Op) : this() {
        this.op = OR
        val _children = this.children
        for (arg in children) _children.add(arg)
    }

    /**
     * The operation arguments, so the sub-operations to logically OR combine.
     * @since 3.0
     */
    var children: OpList by VALUES
}