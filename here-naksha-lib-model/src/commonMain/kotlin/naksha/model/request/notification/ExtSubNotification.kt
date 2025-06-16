@file:Suppress("OPT_IN_USAGE")

package naksha.model.request.notification

import naksha.base.NotNullProperty
import naksha.base.Platform.Platform_C.forKClass
import naksha.base.PlatformType
import naksha.model.IStorage
import naksha.model.objects.NakshaSubscriptionState
import naksha.model.request.FeatureTuple
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

// TODO: Please document me!

@JsExport
open class ExtSubNotification() : SubNotification() {

    @JsName("ExtSubNotificationOf")
    constructor(storage: IStorage, subscriptionId: String, state: NakshaSubscriptionState): this() {
        setRaw("storage", storage)
        setRaw("subscriptionId", subscriptionId)
        setRaw("subscriptionState", state)
    }

    companion object ExtSubNotification_C {
        /**
         * The [PlatformType] of [ExtSubNotification].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(ExtSubNotification::class).withPackageName(PACKAGE_NAME)

        private val ROWS_BY_TXN = NotNullProperty<ExtSubNotification, TuplesByTxn>(TuplesByTxn.TYPE)
    }

    /**
     * The result-rows being part of the transactions.
     *
     * **Note**: Not all rows may have been fetched already, invoke [IStorage.fetchTuples] to do this.
     */
    var rows by ROWS_BY_TXN
}