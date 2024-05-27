package com.here.naksha.lib.base.request

import com.here.naksha.lib.base.P_NakshaFeature
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * P_Feature based operation. All necessary attributes will be calculated automatically, like: geo_grid, flags (and corresponding encodings).
 * If you need more control on how feature should be stored in database please use RowOp.
 */
@OptIn(ExperimentalJsExport::class)
@JsExport
abstract class FeatureOp(
    /**
     * @see Write.op
     */
    op: Int,

    /**
     * @see Write.collectionId
     */
    collectionId: String,

    /**
     *  In memory feature representation.
     */
    val feature: P_NakshaFeature,
) : Write(op = op, collectionId = collectionId) {

    override fun getId(): String = feature.getId()!!
}