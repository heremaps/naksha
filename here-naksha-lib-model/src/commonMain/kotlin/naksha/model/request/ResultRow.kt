package naksha.model.request

import naksha.model.Row
import naksha.model.NakshaFeatureProxy
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
class ResultRow(
    val op: String,
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

