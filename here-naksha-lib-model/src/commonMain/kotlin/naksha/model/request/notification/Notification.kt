package naksha.model.request.notification

import naksha.model.request.Request
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * A subscription notification is sent to subscription targets.
 * The notification initially  does not contain any features, but can be filled using a feature loader, which fetches the states that were part of the transactions.
 * Once a transaction is processed, the seqNumber of the subscription-state should be set to the seqNumber of the successfully processed transaction and then save() of the subscription state should be called.
 */
@OptIn(ExperimentalJsExport::class)
@JsExport
abstract class Notification(
    val storageId: String
): Request() {
}