@file:Suppress("OPT_IN_USAGE")

package naksha.model.request.ops

import naksha.base.NotNullProperty
import naksha.base.NullableProperty
import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * Logical NOT.
 * @since 3.0
 */
@JsExport
class Not() : Op() {
    companion object Not_C {
        private val CHILD = NotNullProperty<Not, Op>(Op::class)
    }

    @JsName("of")
    constructor(child: Op) : this() {
        this.op = NOT
        this.child = child
    }

    /**
     * The operation argument, so the sub-operation to logically NOT.
     * @since 3.0
     */
    var child: Op by CHILD
}
