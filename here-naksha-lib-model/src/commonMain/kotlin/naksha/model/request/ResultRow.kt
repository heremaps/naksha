package naksha.model.request

import naksha.model.Row
import naksha.model.NakshaFeatureProxy
import naksha.model.response.ExecutedOp
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
class ResultRow(
    val op: ExecutedOp,
    val row: Row?, // optional - for retained purged rows
    private var feature: NakshaFeatureProxy? = null
) {

    fun getFeature(): NakshaFeatureProxy? {
        if (feature == null) {
            feature = row?.toMemoryModel()
        }
        return feature
    }
}

