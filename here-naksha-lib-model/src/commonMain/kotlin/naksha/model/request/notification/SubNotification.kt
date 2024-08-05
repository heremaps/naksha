package naksha.model.request.notification

import naksha.base.NotNullProperty
import naksha.model.IStorage
import naksha.model.objects.SubscriptionState
import naksha.model.objects.TransactionList
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * A subscription notification that one or more transaction have been sequenced.
 */
@OptIn(ExperimentalJsExport::class)
@JsExport
open class SubNotification(): Notification() {
    @JsName("of")
    constructor(storage: IStorage, subscriptionId: String, state: SubscriptionState): this() {
        setRaw("storage", storage)
        setRaw("subscriptionId", subscriptionId)
        setRaw("subscriptionState", state)
    }

    companion object SubNotification_C {
        private val STRING = NotNullProperty<SubNotification, String>(String::class) { _,_ -> "" }
        private val STATE = NotNullProperty<SubNotification, SubscriptionState>(SubscriptionState::class)
        private val TX_LIST = NotNullProperty<SubNotification, TransactionList>(TransactionList::class)
    }

    /**
     * The unique identifier of the subscription.
     */
    val subscriptionId by STRING

    /**
     * The subscription state.
     */
    val subscriptionState by STATE

    /**
     * The transactions.
     */
    var transactions by TX_LIST
}
