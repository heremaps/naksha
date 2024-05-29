package com.here.naksha.lib.naksha.request.notification

import com.here.naksha.lib.base.BaseList
import com.here.naksha.lib.base.P_NakshaTransaction
import com.here.naksha.lib.base.P_SubscriptionState
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
    val subscriptionState: P_SubscriptionState,
    val transactions: BaseList<P_NakshaTransaction>
    ): Notification(storageId){
}