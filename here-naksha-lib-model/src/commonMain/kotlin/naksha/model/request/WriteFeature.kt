package naksha.model.request

import naksha.model.Guid
import naksha.model.NakshaFeatureProxy
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * Write operation (PUT/Upsert).
 * Use WriteRow if you need more control about how feature is stored in database.
 */
@OptIn(ExperimentalJsExport::class)
@JsExport
class WriteFeature(
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
) : FeatureOp(XYZ_OP_UPSERT, collectionId, feature)