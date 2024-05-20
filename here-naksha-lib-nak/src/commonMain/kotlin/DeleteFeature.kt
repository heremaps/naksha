package com.here.naksha.lib.base

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
open class DeleteFeature(
        collectionId: String,
        id: String,
        uuid: String? = null
) : RemoveOp(XYZ_OP_DELETE, collectionId, id, uuid)