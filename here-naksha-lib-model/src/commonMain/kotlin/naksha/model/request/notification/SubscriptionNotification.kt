package naksha.model.request.notification

import naksha.base.ListProxy
import naksha.model.NakshaTransactionProxy
import naksha.model.SubscriptionStateProxy
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * A notification that one or more transaction have been sequenced.
 */
@OptIn(ExperimentalJsExport::class)
@JsExport
open class SubscriptionNotification(
    storageId: String,
    val subscriptionId: String,
    val subscriptionState: SubscriptionStateProxy,
    var transactions: ListProxy<NakshaTransactionProxy>
): Notification<SubscriptionNotification>(storageId)