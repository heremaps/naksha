@file:Suppress("OPT_IN_USAGE")
package com.here.naksha.lib.base

import kotlin.js.JsExport
import kotlin.jvm.JvmStatic

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
class P_NakshaLock : GeoFeature() {

    companion object {
        @JvmStatic
        val klass = object : BaseObjectKlass<P_NakshaLock>() {
            override fun isInstance(o: Any?): Boolean = o is P_NakshaLock

            override fun newInstance(vararg args: Any?): P_NakshaLock = P_NakshaLock()
        }

        @JvmStatic
        val TYPE = Base.intern("type")

        @JvmStatic
        val MAX_OWNERS = Base.intern("maxOwners")

        @JvmStatic
        val OWNER = Base.intern("owner")

        @JvmStatic
        val EXPIRES = Base.intern("expires")

        @JvmStatic
        val OWNERS = Base.intern("owners")
    }

    fun getType(): String = "naksha.Lock"

    fun getMaxOwners(): Int = getOr(MAX_OWNERS, Klass.intKlass, 1)

    fun setMaxOwners(maxOwners: Int) = set(MAX_OWNERS, maxOwners)

    fun getOwner(): String? = getOrNull(OWNER, Klass.stringKlass)

    fun setOwner(value: String?) = set(OWNER, value)

    fun setExpires(value: Int64?) = set(EXPIRES, value)

    fun getExpires(): Int64? = getOrNull(EXPIRES, Klass.int64Klass)

    fun setOwners(value: BaseMap<Int64?>?) = set(OWNERS, value)

    fun getOwners(): BaseMap<Int64?>? = getOrNull(OWNERS, BaseMap.klass) as BaseMap<Int64?>?
}
