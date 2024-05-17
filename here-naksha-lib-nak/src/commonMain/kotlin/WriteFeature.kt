package com.here.naksha.lib.base

import com.here.naksha.lib.nak.Flags
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.js.JsName

@OptIn(ExperimentalJsExport::class)
@JsExport
class WriteFeature(
        op: Int,
        id: String? = null,
        uuid: String? = null,
        flags: Flags = Flags(),
        grid: Int? = null,
        val feature: NakFeature? = null
) : AbstractWrite(op, id, uuid, flags, grid) {
    @JsName("WriteFeatureAction")
    constructor(op: Int, feature: NakFeature) : this(op = op, id = null, feature = feature)
}