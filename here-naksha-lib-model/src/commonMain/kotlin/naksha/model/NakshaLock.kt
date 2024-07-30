@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.base.Int64
import naksha.base.NotNullProperty
import naksha.base.NullableProperty
import naksha.model.objects.NakshaFeature
import kotlin.js.JsExport

/**
 * A Naksha lock is a special feature that can be managed by the application and allows to create and manage locks cross multiple service instances.
 * The lock it-self is a normal feature, but the storage will provide mechanisms to keep the lock alive and to automatically drop the lock (invalidate it),when the owning application terminates.
 *
 * Locks can either be single locks (only one owner allows), which makes the save for modifications, or they can be shared locks with multiple owners, limited to a certain amount of concurrent owners.
 *
 * Shared locks are managed by a cron job (pg_cron) that will ensure that expired owners get removed from the lock feature automatically. This causes possible conflicts, which are always resolved optimistically by all participants.
 * Locks that allow only one owner will be ignored by the cron-job, they are application level managed only.
 *
 * Locks can be linked to a geometry, but the meaning of the geometry is not known to the locking algorithm, it must be managed by the application.
 * Technically, locks can be searched for in the virtual lock collection, ones found, it can be acquired.
 * Another approach would be to use HERE tile-ids as lock-ids.
 */
@JsExport
class NakshaLock : NakshaFeature() {

    companion object {
        private val MAX_OWNERS = NotNullProperty<NakshaLock, Int>(Int::class, init = { _, _ -> 1 })
        private val OWNER = NullableProperty<NakshaLock, String>(String::class)
        private val EXPIRES = NotNullProperty<NakshaLock, Int64>(Int64::class)
        private val OWNERS = NullableProperty<NakshaLock, NakshaLockOwnersProxy>(
            NakshaLockOwnersProxy::class)
    }

    override fun typeDefaultValue(): String = "naksha.Lock"
    var maxOwners: Int by MAX_OWNERS
    var owner: String? by OWNER
    var expires: Int64 by EXPIRES
    var owners: NakshaLockOwnersProxy? by OWNERS

}
