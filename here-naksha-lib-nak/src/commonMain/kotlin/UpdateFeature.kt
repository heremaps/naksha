package com.here.naksha.lib.base

import com.here.naksha.lib.nak.Flags
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.js.JsName

@OptIn(ExperimentalJsExport::class)
@JsExport
class UpdateFeature(
        collectionId: String,
        flags: Flags,
        feature: NakFeature,
        val atomic: Boolean = false,
        grid: Int? = null
) : FeatureOp(XYZ_OP_UPDATE, collectionId, flags, feature, grid) {
    @JsName("UpdateFeatureDefault")
    constructor(collectionId: String, flags: Flags, feature: NakFeature) : this(collectionId = collectionId, flags = flags, feature = feature, grid = null, atomic = false)
}