package naksha.model.request.notification

import naksha.base.AbstractListProxy
import naksha.base.AbstractMapProxy
import naksha.model.NakshaFeatureProxy
import naksha.model.NakshaTransactionProxy
import naksha.model.SubscriptionStateProxy
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
class ExtendedSubscriptionNotification(
    storageId: String,
    subscriptionId: String,
    subscriptionState: SubscriptionStateProxy,
    transactions: AbstractListProxy<NakshaTransactionProxy>,
    // The features is a map, where the key is the transaction number and the value is a list of features that are part of this transaction. The features are ordered by their uid.
    val features: AbstractMapProxy<String, AbstractListProxy<NakshaFeatureProxy>>
) : SubscriptionNotification(storageId, subscriptionId, subscriptionState, transactions) {
}