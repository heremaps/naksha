package com.here.naksha.lib.base

import com.here.naksha.lib.nak.Flags
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.js.JsName

@OptIn(ExperimentalJsExport::class)
@JsExport
class InsertFeature(
        collectionId: String,
        flags: Flags,
        // FIXME NakFeature -> Imap
        feature: NakFeature,
        grid: Int? = null
) : FeatureOp(XYZ_OP_CREATE, collectionId, flags, feature, grid) {
    @JsName("InsertFeatureDefault")
    constructor(collectionId: String, flags: Flags, feature: NakFeature) : this(collectionId = collectionId, flags = flags, feature = feature, grid = null)
}