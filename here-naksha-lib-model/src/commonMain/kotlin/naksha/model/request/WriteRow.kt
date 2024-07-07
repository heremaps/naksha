package naksha.model.request

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
    val atomic: Boolean = false
) : RowOp(XYZ_OP_UPSERT, collectionId, row)