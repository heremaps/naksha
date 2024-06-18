package naksha.model.request.condition

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.jvm.JvmStatic

@OptIn(ExperimentalJsExport::class)
@JsExport
sealed class SOpType: OpType {

    data object INTERSECTS : SOpType()
}