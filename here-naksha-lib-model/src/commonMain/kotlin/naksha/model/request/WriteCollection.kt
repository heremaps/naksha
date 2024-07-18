package naksha.model.request

import naksha.model.Guid
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * Write operation (PUT/Upsert).
 */
@OptIn(ExperimentalJsExport::class)
@JsExport
open class WriteCollection(
    collectionId: String,
    /**
     * Indicates if operation should verify `uuid` of the collection stored in DB.
     * true - will perform Update only if `uuid` of the collection in request matches `uuid` of the collection in DB. Response with error if not.
     * false - operation will be performed for collection with same `id` without other verification
     *
     * Default: false
     */
    val atomic: Boolean = false,
    val guid: Guid? = null
) : CollectionOp(XYZ_OP_UPSERT, collectionId)