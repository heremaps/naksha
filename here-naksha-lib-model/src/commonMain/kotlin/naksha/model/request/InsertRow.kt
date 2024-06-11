package com.here.naksha.lib.naksha.request

import naksha.model.response.Row
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * Insert operation, if you picked this operation but feature already exists in DB, the error will be returned - for such cases use WriteFeature.
 * @see InsertFeature if you need more convenient mode with default conversions.
 */
@OptIn(ExperimentalJsExport::class)
@JsExport
class InsertRow(
    collectionId: String,
    row: Row,
) : RowOp(XYZ_OP_CREATE, collectionId, row)