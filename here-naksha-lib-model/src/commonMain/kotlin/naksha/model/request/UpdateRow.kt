package naksha.model.request

import naksha.model.response.Row
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * Update operation, if you picked this operation but feature doesn't exist in DB, the error will be returned - for such cases use WriteFeature.
 * @see UpdateFeature if you need more convenient mode with default conversions.
 */
@OptIn(ExperimentalJsExport::class)
@JsExport
class UpdateRow(
    collectionId: String,
    row: Row,
    /**
     * Indicates if operation should verify `uuid` of the feature stored in DB.
     * true - will perform Update only if `uuid` of the feature in request matches `uuid` of the feature in DB. Response with error if not.
     * false - operation will be performed for feature with same `id` without other verification
     *
     * Default: false
     */
    val atomic: Boolean = false,
) : RowOp(XYZ_OP_UPDATE, collectionId, row)