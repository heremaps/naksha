package com.here.naksha.lib.base

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
class InsertRow(
        collectionId: String,
        row: Row,
        grid: Int? = null
) : RowOp(XYZ_OP_CREATE, collectionId, row, grid)