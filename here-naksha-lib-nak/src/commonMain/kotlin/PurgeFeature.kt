package com.here.naksha.lib.base

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
class PurgeFeature(
        collectionId: String,
        id: String,
        uuid: String?
) : RemoveOp(XYZ_OP_PURGE, collectionId, id, uuid)