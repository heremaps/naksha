package naksha.model.request.condition

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.jvm.JvmStatic

@OptIn(ExperimentalJsExport::class)
@JsExport
class SOpType private constructor(val operation: String): OpType {

    companion object {
        @JvmStatic
        val INTERSECTS = SOpType("intersects")
    }
}