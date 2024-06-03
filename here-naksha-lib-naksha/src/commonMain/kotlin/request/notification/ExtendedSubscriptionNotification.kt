package com.here.naksha.lib.naksha.request.notification

import com.here.naksha.lib.base.P_List
import com.here.naksha.lib.base.P_Map
import com.here.naksha.lib.base.P_NakshaFeature
import com.here.naksha.lib.base.P_NakshaTransaction
import com.here.naksha.lib.base.P_SubscriptionState
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
class ExtendedSubscriptionNotification(
    storageId: String,
    subscriptionId: String,
    subscriptionState: P_SubscriptionState,
    transactions: P_List<P_NakshaTransaction>,
    // The features is a map, where the key is the transaction number and the value is a list of features that are part of this transaction. The features are ordered by their uid.
    val features: P_Map<String, P_List<P_NakshaFeature>>
) : SubscriptionNotification(storageId, subscriptionId, subscriptionState, transactions) {
}