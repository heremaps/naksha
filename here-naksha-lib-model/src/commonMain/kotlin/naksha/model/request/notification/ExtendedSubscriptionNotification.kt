package naksha.model.request.notification

import naksha.base.ListProxy
import naksha.base.MapProxy
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
    transactions: ListProxy<NakshaTransactionProxy>,
    /** The features is a map, where the key is the transaction number and the value is a list of features that are part of this transaction. The features are ordered by their uid.
     */
    val features: MapProxy<String, ListProxy<NakshaFeatureProxy>>
) : SubscriptionNotification(storageId, subscriptionId, subscriptionState, transactions)