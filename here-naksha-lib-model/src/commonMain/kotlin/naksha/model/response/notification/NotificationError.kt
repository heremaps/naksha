package naksha.model.response.notification

import naksha.model.response.ErrorResponse
import naksha.model.response.NakshaError
import naksha.base.Int64
import naksha.base.MapProxy
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * Reports a processing error, but confirm that the pipeline processed the transaction up until (including) the given sequence number, and within this transaction up until (excluding) the given uid.
 *
 * If the original notification contained further transactions, these will be delivered again in a new subscription notification. If the transaction with the reported sequence number was not fully processed, only the not processed features are send again.
 */
@OptIn(ExperimentalJsExport::class)
@JsExport
class NotificationError(
    reason: NakshaError,
    val seqNumber: Int64,
    val uid: Int,
    val handlerStates: MapProxy<String, Any>
) : ErrorResponse(reason)