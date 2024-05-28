package com.here.naksha.lib.naksha.request

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * Logical operators.
 */
@OptIn(ExperimentalJsExport::class)
@JsExport
class LOpType private constructor(val operator: String): OpType {

    companion object {
        /**
         * Combine all children via logical AND operator.
         */
        val AND: LOpType = LOpType("AND")

        /**
         * Combine all children via logical OR operator.
         */
        val OR: LOpType = LOpType("OR")

        /**
         * Negate the logical state of the child operation, requires exactly one child.
         */
        val NOT: LOpType = LOpType("NOT")
    }
}