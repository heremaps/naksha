@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.base.Int64
import naksha.jbon.IDictManager
import naksha.model.Naksha.NakshaCompanion.FETCH_ALL
import naksha.model.Naksha.NakshaCompanion.FETCH_ID
import naksha.model.Naksha.NakshaCompanion.FETCH_META
import naksha.model.Naksha.NakshaCompanion.FETCH_CACHE
import naksha.model.objects.NakshaFeature
import naksha.model.request.ResultRow
import kotlin.js.JsExport

/**
 * Any entity implementing the [IStorage] interface represents some data-sink, and comes with an implementation that grants access to the data. The storage normally is a singleton that opens many sessions in parallel.
 *
 * Storages operate on maps. A map is an isolated data sink within the same storage (like an own database schema, an own S3 bucket, an own SQLite database, an own file, aso.). Some implementations only support one map, but if multiple maps are supported, a map is a fully separated storage entity. Each map has its own collections, its own transaction log, and all other entities. Some storages allow to access multiple maps from one session, others may limit a session to a single map. The capabilities can be queried at the session.
 *
 * The storage may or may not support dictionaries, but in any case it needs to return a dictionary manager (even, if this is only an immutable one with no content).
 * @since 2.0.7
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
     * Convert the given [Row] into a [NakshaFeature].
     *
     * @param row the row to convert.
     * @return the feature generated from the row.
     * @since 3.0.0
     */
    fun rowToFeature(row: Row): NakshaFeature

    /**
     * Convert the given feature into a [Row].
     * @param feature the feature to convert.
     * @return the [Row] generated from the given feature.
     * @since 3.0.0
     */
    fun featureToRow(feature: NakshaFeature): Row

    /**
     * Returns the dictionary manager of the storage.
     *
     * - Throws [NakshaError.UNINITIALIZED], if [initStorage] has not been called before.
     * @param map the map for which to return the dictionary manager.
     * @return The dictionary manager of the storage.
     * @since 3.0.0
     */
    fun dictManager(map: String = NakshaContext.currentContext().map): IDictManager

    @Deprecated(
        "This is not yet implemented and need further review",
        level = DeprecationLevel.ERROR
    )
    fun enterLock(id: String, waitMillis: Int64): ILock // Hint: Implement in PsqlStorage

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
     * Tests if the given handle is valid, and if it is, tries to extend its live-time to the given amount of milliseconds.
     *
     * Some handles may expire after some time. For example, when custom filters were applied, the generated result-set must be stored somewhere to guarantee that it is always the same (we can't store the filter code!), but we do not store this forever, so the handle does have an expiry. Some handles may not have an expiry, for example when the storage can reproduce them at any moment, using just the information from the handle.
     *
     * There is no guarantee that the life-time of the handle can be extended.
     * @param handle the handle to test.
     * @param ttl if not _null_, the time-to-live of the handle should be extended by the given amount of milliseconds, if possible.
     * @return _true_ if the handle is valid, _false_ otherwise.
     * @since 3.0.0
     */
    fun validateHandle(handle: String, ttl: Int? = null): Boolean

    /**
     * Fetches all rows with the given row identifiers.
     * @param map the map from which to fetch.
     * @param collectionId the collection from to fetch.
     * @param rowIds a list of row identifiers of the rows to fetch.
     * @param mode the fetch mode, can be [all][FETCH_ALL], [id][FETCH_ID], [meta][FETCH_META], or [cache][FETCH_CACHE].
     * @return the list of the fetched rows, _null_, if the row was not in cached or not found in the storage.
     * @since 3.0.0
     */
    fun fetchRowsById(map: String, collectionId: String, rowIds: Array<RowId>, mode: String = FETCH_ALL): List<Row?>

    /**
     * Fetches all rows in the given result-rows.
     * @param rows a list of result-rows to fetch.
     * @param from the index of the first result-row to fetch.
     * @param to the index of the first result-row to ignore.
     * @param mode the fetch mode, can be [all][FETCH_ALL], [id][FETCH_ID], [meta][FETCH_META], or [cache][FETCH_CACHE].
     * @since 3.0.0
     */
    fun fetchRows(rows: List<ResultRow?>, from:Int = 0, to:Int = rows.size, mode: String = FETCH_ALL)

    /**
     * Fetches a single result-row.
     * @param row the result-row into which to load the row.
     * @param mode the fetch mode, can be [all][FETCH_ALL], [id][FETCH_ID], [meta][FETCH_META], or [cache][FETCH_CACHE].
     * @since 3.0.0
     */
    fun fetchRow(row: ResultRow, mode: String = FETCH_ALL)

    /**
     * Shutdown the storage instance, blocks until the storage is down (all sessions are closed).
     *
     * @since 2.0.7
     */
    override fun close()
}