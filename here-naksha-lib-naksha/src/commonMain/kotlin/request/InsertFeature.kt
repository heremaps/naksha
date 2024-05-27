package com.here.naksha.lib.naksha.request

import com.here.naksha.lib.base.P_NakshaFeature
import com.here.naksha.lib.nak.Flags
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * Insert operation, if you picked this operation but feature already exists in DB, the error will be returned - for such cases use WriteFeature.
 * Use InsertRow if you need more control about how feature is stored in database.
 */
@OptIn(ExperimentalJsExport::class)
@JsExport
class InsertFeature(
    collectionId: String,
    feature: P_NakshaFeature,
) : FeatureOp(XYZ_OP_CREATE, collectionId, feature)