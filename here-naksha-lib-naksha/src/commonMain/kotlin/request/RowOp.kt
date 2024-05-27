package com.here.naksha.lib.naksha.request

import com.here.naksha.lib.base.response.Row
import com.here.naksha.lib.nak.Flags
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * Operation on the Row object. Row has to be prepared in pure form, You have full power of how it should be defined and set.
 * @see FeatureOp if you need more convenient mode with default conversions.
 */
@OptIn(ExperimentalJsExport::class)
@JsExport
abstract class RowOp(
    op: Int,
    collectionId: String,
    val row: Row,
) : Write(op = op, collectionId = collectionId) {
    override fun getId(): String = row.id
    fun getFlags(): Flags = Flags(row.flags)
}