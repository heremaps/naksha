package naksha.model.request

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * Insert operation, if you picked this operation but collection already exists in DB, the error will be returned - for such cases use WriteCollection.
 */
@OptIn(ExperimentalJsExport::class)
@JsExport
class InsertCollection(
    collectionId: String,
) : CollectionOp(XYZ_OP_CREATE, collectionId)