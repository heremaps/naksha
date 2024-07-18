package naksha.model.request

import naksha.model.Guid
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
abstract class RemoveOp(
        op: Int,
        collectionId: String,
        private val id: String,
        val guid: Guid? = null
): Write(op, collectionId) {
    override fun getId(): String = id
}