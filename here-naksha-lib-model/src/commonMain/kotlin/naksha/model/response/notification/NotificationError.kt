package naksha.model.response.notification

import naksha.model.response.ErrorResponse
import naksha.model.response.NakshaError
import naksha.base.Int64
import naksha.base.P_Map
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
class NotificationError(
    reason: NakshaError,
    val seqNumber: Int64,
    val uid: Int,
    val handlerStates: P_Map<String, Any>
) : ErrorResponse(reason) {
}