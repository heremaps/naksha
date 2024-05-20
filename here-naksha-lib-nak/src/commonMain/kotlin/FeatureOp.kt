package com.here.naksha.lib.base

import com.here.naksha.lib.nak.Flags
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
abstract class FeatureOp(
        op: Int,
        collectionId: String,
        private val flags: Flags,
        // FIXME NakFeature -> Imap
        val feature: NakFeature,
        private val grid: Int? = null,
) : WriteOp(op = op, collectionId = collectionId), UploadOp {
    override fun getId(): String = feature.getId()!!
    override fun getFlags(): Flags = flags
    override fun getGrid(): Int? = grid
}