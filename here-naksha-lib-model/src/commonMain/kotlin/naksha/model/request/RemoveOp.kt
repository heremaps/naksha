package naksha.model.request

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
abstract class RemoveOp(
        op: Int,
        collectionId: String,
        private val id: String,
        val uuid: String?
): Write(op, collectionId) {
    override fun getId(): String = id
}