package com.here.naksha.lib.naksha.request

import com.here.naksha.lib.base.P_NakshaFeature
import com.here.naksha.lib.nak.Flags
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * Update operation, if you picked this operation but feature doesn't exist in DB, the error will be returned - for such cases use WriteFeature.
 * Use UpdateRow if you need more control about how feature is stored in database.
 */
@OptIn(ExperimentalJsExport::class)
@JsExport
class UpdateFeature(
    collectionId: String,
    feature: P_NakshaFeature,
    /**
     * Indicates if operation should verify `uuid` of the feature stored in DB.
     * true - will perform Update only if `uuid` of the feature in request matches `uuid` of the feature in DB. Response with error if not.
     * false - operation will be performed for feature with same `id` without other verification
     *
     * Default: false
     */
    val atomic: Boolean = false,
) : FeatureOp(XYZ_OP_UPDATE, collectionId, feature)