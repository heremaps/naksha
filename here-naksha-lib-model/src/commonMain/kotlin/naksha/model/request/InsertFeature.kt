package naksha.model.request

import naksha.model.NakshaFeatureProxy
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * Insert operation, if you picked this operation but feature already exists in DB, the error will be returned - for such cases use WriteFeature.
 * Use InsertRow if you need more control about how feature is stored in database.
 */
@OptIn(ExperimentalJsExport::class)
@JsExport
open class InsertFeature(
    collectionId: String,
    feature: NakshaFeatureProxy,
) : FeatureOp(XYZ_OP_CREATE, collectionId, feature)