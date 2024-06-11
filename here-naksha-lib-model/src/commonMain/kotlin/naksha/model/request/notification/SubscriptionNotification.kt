package naksha.model.request.notification

import naksha.base.P_List
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
    val transactions: P_List<NakshaTransactionProxy>
    ): Notification(storageId){
}