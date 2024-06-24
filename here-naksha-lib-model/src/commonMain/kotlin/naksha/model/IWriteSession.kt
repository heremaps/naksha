package naksha.model

import naksha.model.response.Response
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
interface IWriteSession: IReadSession {

    fun writeFeature(feature: NakshaFeatureProxy): Response

    fun commit()

    fun rollback()
}