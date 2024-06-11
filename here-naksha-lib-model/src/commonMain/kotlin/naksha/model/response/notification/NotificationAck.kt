package naksha.model.response.notification

import naksha.model.response.Response
import naksha.base.Int64
import naksha.base.P_Map
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
class NotificationAck(
    val seqNumber: Int64,
    val uid: Int,
    val handlerStates: P_Map<String, Any>
) : Response(SUCCESS_TYPE) {
    override fun size(): Int = 0
}