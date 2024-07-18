package naksha.model.request

import naksha.model.NakshaCollectionProxy
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
     * In memory implementation of the collection
     */
    collection: NakshaCollectionProxy,
) : Write(op = op, collectionId = collection.id) {

    override fun getId(): String = collectionId
}