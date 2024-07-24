@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.base.Int64
import naksha.base.PlatformMap
import naksha.jbon.IDictManager
import kotlin.js.JsExport

/**
 * Any entity implementing the [IStorage] interface represents some data-sink, and comes with an implementation that grants access to the data. The storage normally is a singleton that opens many sessions in parallel.
 *
 * Storages operate on maps. A map is an isolated data sink within the same storage (like an own database schema, an own S3 bucket, an own SQLite database, an own file, aso.). Some implementations only support one map, but if multiple maps are supported, a map is a fully separated storage entity. Each map has its own collections, its own transaction log, and all other entities. Some storages allow to access multiple maps from one session, others may limit a session to a single map. The capabilities can be queried at the session.
 *
 * The storage may or may not support dictionaries, but in any case it needs to return a dictionary manager (even, if this is only an immutable one with no content).
 */
@JsExport
interface IStorage : AutoCloseable {

    /**
     * The storage-id.
     *
     * - Throws [NakshaError.UNINITIALIZED], if [initStorage] has not been called before.
     * @since 2.0.8
     */
    fun id(): String

    /**
     * Initializes the storage for the default map. The function will try to read the storage identifier from the storage. If necessary, creating the transaction table, installs needed scripts, and extensions. If the storage is already initialized, and a storage identifier is provided in the params, then the method ensures that the actual storage-id matches the requested one. This operation requires that the current [context][NakshaContext] has the [superuser][NakshaContext.su] rights.
     *
     * - Throws [NakshaError.FORBIDDEN], if not called as super-user, and the storage is a new one.
     * @param params optional special parameters that are storage dependent to influence how a storage is initialized.
     * @since 2.0.8
     */
    fun initStorage(params: Map<String, *>? = null)

    /**
     * Initializes the given map in the storage. If the given map is already initialized, the method does nothing. This operation requires that the current [context][NakshaContext] has the [superuser][NakshaContext.su] rights.
     *
     * - Throws [NakshaError.UNINITIALIZED], if [initStorage] has not been called before.
     * - Throws [NakshaError.FORBIDDEN], if not called as super-user, and the storage is a new one.
     * @param map the realm to initialize.
     * @throws NakshaException if the initialization failed (e.g. the storage does not support multi-realms).
     * @since 3.0.0
     */
    fun initMap(map: String)

    /**
     * Deletes the given map with all data in it. This operation requires that the current [context][NakshaContext] has the [superuser][NakshaContext.su] rights.
     *
     * - Throws [NakshaError.UNINITIALIZED], if [initStorage] has not been called before.
     * - Throws [NakshaError.FORBIDDEN], if the user has no super-user rights.
     * @since 3.0.0
     */
    fun dropMap(map: String)

    /**
     * Convert the given [Row] into a [NakshaFeatureProxy].
     * @param row The row to convert.
     * @return The feature generated from the row.
     * @since 3.0.0
     */
    fun rowToFeature(row: Row): NakshaFeatureProxy

    /**
     * Convert the given feature into a [Row].
     * @param feature the feature to convert.
     * @return the [Row] generated from the given feature.
     * @since 3.0.0
     */
    fun featureToRow(feature: PlatformMap): Row

    /**
     * Returns the dictionary manager of the storage.
     *
     * - Throws [NakshaError.UNINITIALIZED], if [initStorage] has not been called before.
     * @return The dictionary manager of the storage.
     * @since 3.0.0
     */
    fun dictManager(nakshaContext: NakshaContext): IDictManager

    @Deprecated(
        "This is not yet implemented and need further review",
        level = DeprecationLevel.ERROR
    )
    fun enterLock(id: String, waitMillis: Int64): ILock

    /**
     * Open a new write session.
     *
     * - Throws [NakshaError.UNINITIALIZED], if [initStorage] has not been called before.
     * @param context the naksha context to use in the session.
     * @param options additional optional options.
     * @return the write session.
     * @since 2.0.7
     */
    fun newWriteSession(context: NakshaContext = NakshaContext.currentContext(), options: NakshaSessionOptions? = null): IWriteSession

    /**
     * Open a new read-only session. The [NakshaSessionOptions] can be used to guarantee, that the session relates to the master-node, if replication lags are not acceptable.
     *
     * - Throws [NakshaError.UNINITIALIZED], if [initStorage] has not been called before.
     * @param context The naksha context to use in the session.
     * @param options additional optional options.
     * @return the read-only session.
     * @since 2.0.7
     */
    fun newReadSession(context: NakshaContext = NakshaContext.currentContext(), options: NakshaSessionOptions? = null): IReadSession

    /**
     * Shutdown the storage instance, blocks until the storage is down (all sessions are closed).
     *
     * @since 2.0.7
     */
    override fun close()
}