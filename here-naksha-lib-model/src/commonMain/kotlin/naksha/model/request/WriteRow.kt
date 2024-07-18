package naksha.model.request

import naksha.model.Guid
import naksha.model.Row
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * Write operation (PUT/Upsert).
 * @see WriteFeature if you need more convenient mode with default conversions.
 */
@OptIn(ExperimentalJsExport::class)
@JsExport
class WriteRow(
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
    val guid: Guid? = null
) : RowOp(XYZ_OP_UPSERT, collectionId, row)