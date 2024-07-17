package naksha.model.request.condition

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * Logical operators.
 */
@OptIn(ExperimentalJsExport::class)
@JsExport
class LOpType private constructor(val operator: String): OpType {

    companion object LOpTypeCompanion {
        /**
         * Combine all children via logical AND operator.
         */
        @JvmField
        @JsStatic
        val AND: LOpType = LOpType("AND")

        /**
         * Combine all children via logical OR operator.
         */
        @JvmField
        @JsStatic
        val OR: LOpType = LOpType("OR")

        /**
         * Negate the logical state of the child operation, requires exactly one child.
         */
        @JvmField
        @JsStatic
        val NOT: LOpType = LOpType("NOT")
    }
}