package naksha.model.request

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * P_Collection based operation.
 */
@OptIn(ExperimentalJsExport::class)
@JsExport
abstract class CollectionOp(
    /**
     * @see Write.op
     */
    op: Int,

    /**
     * @see Write.collectionId
     */
    collectionId: String,
) : Write(op = op, collectionId = collectionId) {

    override fun getId(): String = collectionId
}