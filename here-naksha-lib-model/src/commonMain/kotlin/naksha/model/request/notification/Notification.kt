@file:Suppress("OPT_IN_USAGE")

package naksha.model.request.notification

import naksha.base.NotNullProperty
import naksha.model.IStorage
import naksha.base.NakshaError.NakshaError_C.ILLEGAL_STATE
import naksha.base.NakshaException
import naksha.base.Platform.Platform_C.forKClass
import naksha.base.PlatformType
import naksha.base.String_TYPE
import naksha.model.IStorage_TYPE
import naksha.model.request.Request
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * A storage notification sent to notification targets.
 *
 * The notification initially does not contain any features, but can be filled using a feature loader, which fetches the states that were part of the transactions. Once a transaction is processed, the seqNumber of the subscription-state should be set to the seqNumber of the successfully processed transaction and then save() of the subscription state should be called.
 */
@JsExport
open class Notification(): Request() {

    /**
     * Create a new storage notification.
     * @param storage the storage that causes the notification.
     */
    @JsName("NotificationOf")
    constructor(storage: IStorage): this() {
        set("storage", storage)
    }

    companion object Notification_C {
        /**
         * The [PlatformType] of [Notification].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(Notification::class).withPackageName(PACKAGE_NAME)

        private val STORAGE = NotNullProperty<Notification, IStorage>(IStorage_TYPE){ _,_ ->
            throw NakshaException(ILLEGAL_STATE, "The notification has no storage")
        }
        private val STRING = NotNullProperty<Notification, String>(String_TYPE) { self, _ -> self.storage.id }
    }

    /**
     * The storage that raised the notification.
     */
    val storage by STORAGE

    /**
     * The storage-identifier of the storage that generated the notification.
     */
    var storageId by STRING
}
