package com.here.naksha.lib.naksha.request.notification

import naksha.base.P_List
import naksha.base.P_Map
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
    transactions: P_List<NakshaTransactionProxy>,
    // The features is a map, where the key is the transaction number and the value is a list of features that are part of this transaction. The features are ordered by their uid.
    val features: P_Map<String, P_List<NakshaFeatureProxy>>
) : SubscriptionNotification(storageId, subscriptionId, subscriptionState, transactions) {
}