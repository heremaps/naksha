@file:Suppress("OPT_IN_USAGE")

package naksha.model.request.notification

import naksha.base.NotNullProperty
import naksha.model.IStorage
import naksha.model.objects.SubscriptionState
import kotlin.js.JsExport
import kotlin.js.JsName

// TODO: Please document me!

@JsExport
open class ExtSubNotification() : SubNotification() {

    @JsName("of")
    constructor(storage: IStorage, subscriptionId: String, state: SubscriptionState): this() {
        setRaw("storage", storage)
        setRaw("subscriptionId", subscriptionId)
        setRaw("subscriptionState", state)
    }

    companion object ExtSubNotification_C {
        private val ROWS_BY_TXN = NotNullProperty<ExtSubNotification, TuplesByTxn>(TuplesByTxn::class)
    }

    /**
     * The result-rows being part of the transactions.
     *
     * **Note**: Not all rows may have been fetched already, invoke [IStorage.fetchRows] to do this.
     */
    var rows by ROWS_BY_TXN
}