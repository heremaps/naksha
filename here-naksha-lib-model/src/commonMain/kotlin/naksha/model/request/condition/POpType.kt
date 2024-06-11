package naksha.model.request

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.jvm.JvmStatic

@OptIn(ExperimentalJsExport::class)
@JsExport
class POpType private constructor(val operation: String): OpType {

    companion object {
        @JvmStatic
        val EXISTS = POpType("exists")
        @JvmStatic
        val STARTS_WITH = POpType("startsWith")
        @JvmStatic
        val EQ = POpType("=")
        @JvmStatic
        val GT = POpType(">")
        @JvmStatic
        val GTE = POpType(">=")
        @JvmStatic
        val LT = POpType("<")
        @JvmStatic
        val LTE = POpType("<=")
        @JvmStatic
        val NULL = POpType("null")
        @JvmStatic
        val NOT_NULL = POpType("not null")
        @JvmStatic
        val CONTAINS = POpType("@>")
    }
}