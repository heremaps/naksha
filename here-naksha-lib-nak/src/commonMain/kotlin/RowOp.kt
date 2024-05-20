package com.here.naksha.lib.base

import com.here.naksha.lib.nak.Flags
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
abstract class RowOp(
        op: Int,
        collectionId: String,
        val row: Row,
        private val grid: Int? = null
) : WriteOp(op = op, collectionId = collectionId), UploadOp {
    override fun getId(): String = row.id
    override fun getFlags(): Flags = row.flags
    override fun getGrid(): Int? = grid
}