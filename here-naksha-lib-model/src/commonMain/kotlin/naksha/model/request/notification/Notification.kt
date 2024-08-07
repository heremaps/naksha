@file:Suppress("OPT_IN_USAGE")

package naksha.model.request.notification

import naksha.base.NotNullProperty
import naksha.model.IStorage
import naksha.model.NakshaError
import naksha.model.NakshaError.NakshaErrorCompanion.ILLEGAL_STATE
import naksha.model.NakshaException
import naksha.model.request.Request
import kotlin.js.JsExport
import kotlin.js.JsName

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
    @JsName("of")
    constructor(storage: IStorage): this() {
        setRaw("storage", storage)
    }

    companion object Notification_C {
        private val STORAGE = NotNullProperty<Notification, IStorage>(IStorage::class){ _,_ ->
            throw NakshaException(ILLEGAL_STATE, "The notification has no storage")
        }
        private val STRING = NotNullProperty<Notification, String>(String::class) { self, _ -> self.storage.id }
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
