@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.base.Int64
import naksha.base.NotNullProperty
import naksha.base.NullableProperty
import naksha.base.P_Map
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
class NakshaLockProxy : NakshaFeatureProxy() {

    companion object {
        private val TYPE = NotNullProperty<Any, NakshaLockProxy, String>(String::class, defaultValue = "naksha.Lock")
        private val MAX_OWNERS = NotNullProperty<Any, NakshaLockProxy, Int>(Int::class, defaultValue = 1)
        private val OWNER = NullableProperty<Any, NakshaLockProxy, String>(String::class)
        private val EXPIRES = NotNullProperty<Any, NakshaLockProxy, Int64>(Int64::class)
        private val OWNERS = NullableProperty<Any, NakshaLockProxy, NakshaLockOwnersProxy>(
            NakshaLockOwnersProxy::class)
    }

    var type: String by TYPE
    var maxOwners: Int by MAX_OWNERS
    var owner: String? by OWNER
    var expires: Int64 by EXPIRES
    var owners: NakshaLockOwnersProxy? by OWNERS

}
