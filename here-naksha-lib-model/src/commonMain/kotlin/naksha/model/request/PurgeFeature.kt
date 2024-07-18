package naksha.model.request

import naksha.model.Guid
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * Purge operation removes from head and $del.
 */
@OptIn(ExperimentalJsExport::class)
@JsExport
class PurgeFeature(
        collectionId: String,
        id: String,
        guid: Guid?
) : RemoveOp(XYZ_OP_PURGE, collectionId, id, guid)
