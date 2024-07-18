package naksha.model.request

import naksha.model.Guid
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * Delete operation, if uuid is specified, the executor will verify if feature on DB has the same uuid and perform operation only when it is.
 * @see P_NakshaCollection.setAutoPurge to check if this operation will auto-purge.
 */
@OptIn(ExperimentalJsExport::class)
@JsExport
open class DeleteFeature(
        collectionId: String,
        /**
         * ID of the feature to delete.
         */
        id: String,
        guid: Guid?
) : RemoveOp(XYZ_OP_DELETE, collectionId, id, guid)