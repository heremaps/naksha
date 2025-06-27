@file:Suppress("OPT_IN_USAGE")

package naksha.model.request.notification

import naksha.base.NotNullProperty
import naksha.base.Platform.Platform_C.forKClass
import naksha.base.PlatformType
import naksha.base.String_TYPE
import naksha.model.IStorage
import naksha.model.objects.NakshaSubscriptionState
import naksha.model.objects.NakshaTxList
import naksha.model.objects.PACKAGE_NAME
import naksha.model.objects.StoreMode
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * A subscription notification that one or more transaction have been sequenced.
 * @since 3.0
 */
@JsExport
open class SubNotification(): Notification() {

    @JsName("SubNotificationOf")
    constructor(storage: IStorage, subscriptionId: String, state: NakshaSubscriptionState): this() {
        set("storage", storage)
        set("subscriptionId", subscriptionId)
        set("subscriptionState", state)
    }

    companion object SubNotification_C {
        /**
         * The [PlatformType] of [SubNotification].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(SubNotification::class).withPackageName(PACKAGE_NAME)

        private val STRING = NotNullProperty<SubNotification, String>(String_TYPE) { _, _ -> "" }
        private val STATE = NotNullProperty<SubNotification, NakshaSubscriptionState>(NakshaSubscriptionState.TYPE)
        private val TX_LIST = NotNullProperty<SubNotification, NakshaTxList>(NakshaTxList.TYPE)
    }

    /**
     * The unique identifier of the subscription.
     * @since 3.0
     */
    val subscriptionId by STRING

    /**
     * The subscription state.
     * @since 3.0
     */
    val subscriptionState by STATE

    /**
     * The transactions.
     * @since 3.0
     */
    var transactions by TX_LIST
}
