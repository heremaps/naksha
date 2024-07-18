package naksha.model.request

import naksha.model.Guid
import naksha.model.NakshaFeatureProxy
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * Update operation, if you picked this operation but feature doesn't exist in DB, the error will be returned - for such cases use WriteFeature.
 * Use UpdateRow if you need more control about how feature is stored in database.
 */
@OptIn(ExperimentalJsExport::class)
@JsExport
class UpdateFeature(
    collectionId: String,
    feature: NakshaFeatureProxy,
    /**
     * Indicates if operation should verify `uuid` of the feature stored in DB.
     * true - will perform Update only if `uuid` of the feature in request matches `uuid` of the feature in DB. Response with error if not.
     * false - operation will be performed for feature with same `id` without other verification
     *
     * Default: false
     */
    val atomic: Boolean = false,
    val guid: Guid? = null
) : FeatureOp(XYZ_OP_UPDATE, collectionId, feature)
