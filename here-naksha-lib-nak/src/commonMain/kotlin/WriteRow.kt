package com.here.naksha.lib.base

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
class WriteRow(
        collectionId: String,
        row: Row,
        val atomic: Boolean = false,
        grid: Int? = null
) : RowOp(XYZ_OP_UPSERT, collectionId, row, grid)