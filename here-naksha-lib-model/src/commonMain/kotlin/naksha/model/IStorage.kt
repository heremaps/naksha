@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.base.Int64
import naksha.jbon.IDictManager
import naksha.model.Naksha.NakshaCompanion.FETCH_ALL
import naksha.model.Naksha.NakshaCompanion.FETCH_ID
import naksha.model.Naksha.NakshaCompanion.FETCH_META
import naksha.model.Naksha.NakshaCompanion.FETCH_CACHE
import naksha.model.objects.NakshaFeature
import naksha.model.request.ResultTuple
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
    val id: String

    /**
     * The admin options to use for internal processing.
     *
     * They are needed for administrative work, reading dictionaries, collection information, create administrative structures. The application can override the defaults to have more control over the `appId` and/or `author` being written, when internal data is processed, and how internal connections authenticate (`appName`).
     *
     * If not set, some defaults are read from [NakshaContext] application level defaults, others are internally generated (like `appName`).
     * @since 3.0.0
     */
    val adminOptions: SessionOptions

    /**
     * Tests if this storage is initialized, so [initStorage] has been called.
     * @return _true_ if this storage is initialized; _false_ otherwise.
     */
    fun isInitialized(): Boolean

    /**
     * Initializes the storage for the default map. The function will try to read the storage identifier from the storage. If necessary, creating the transaction table, installs needed scripts, and extensions. If the storage is already initialized, and a storage identifier is provided in the params, then the method ensures that the actual storage-id matches the requested one. This operation requires that the current [context][NakshaContext] has the [superuser][NakshaContext.su] rights.
     *
     * - Throws [NakshaError.FORBIDDEN], if not called as super-user, and the storage is a new one.
     * @param params optional special parameters that are storage dependent to influence how a storage is initialized.
     * @since 2.0.8
     */
    fun initStorage(params: Map<String, *>? = null)

    /**
     * The default map.
     *
     * - Throws [NakshaError.UNINITIALIZED], if [initStorage] has not been called before.
     * @since 3.0.0
     */
    val defaultMap: IMap

    /**
     * Returns the map admin object.
     *
     * - Throws [NakshaError.UNINITIALIZED], if [initStorage] has not been called before.
     * @param mapId the map-id.
     * @return the map admin object.
     */
    operator fun get(mapId: String): IMap

    /**
     * Tests if this storage contains the given map.
     *
     * - Throws [NakshaError.UNINITIALIZED], if [initStorage] has not been called before.
     * @param mapId the map-id of the map to test for.
     * @return _true_ if such a map exists; _false_ otherwise.
     */
    operator fun contains(mapId: String): Boolean

    /**
     * Returns the map-identifier for the given map-number.
     *
     * - Throws [NakshaError.UNINITIALIZED], if [initStorage] has not been called before.
     * @param mapNumber the map-number.
     * @return the map-identifier or _null_, if no such map exists.
     */
    fun getMapId(mapNumber: Int): String?

    /**
     * Convert the given [Tuple] into a [NakshaFeature].
     *
     * - Throws [NakshaError.UNINITIALIZED], if [initStorage] has not been called before.
     * @param tuple the row to convert.
     * @return the feature generated from the row.
     * @since 3.0.0
     */
    fun rowToFeature(tuple: Tuple): NakshaFeature

    /**
     * Convert the given feature into a [Tuple].
     *
     * - Throws [NakshaError.UNINITIALIZED], if [initStorage] has not been called before.
     * @param feature the feature to convert.
     * @return the [Tuple] generated from the given feature.
     * @since 3.0.0
     */
    fun featureToRow(feature: NakshaFeature): Tuple

    /**
     * Returns the dictionary manager of the storage.
     *
     * - Throws [NakshaError.UNINITIALIZED], if [initStorage] has not been called before.
     * @param map the map for which to return the dictionary manager.
     * @return The dictionary manager of the storage.
     * @since 3.0.0
     */
    fun dictManager(map: String = NakshaContext.currentContext().mapId): IDictManager

    // TODO: We should move this into IWriteSession so that we can implement it using an advisory lock!
    //       We have all kind of security measurements already in PgSession, for example we manage a
    //       shared background connection, and to keep an advisory lock alive, we need this, because it
    //       is bound to the session (aka connection).
    //       Additionally, we can continue to use this connection, that has the lock, for the whole session
    //       that might be useful for all handlers too.
    //       Another thing to take into account, when some fatal error happens, we should terminate the connection
    //       to ensure that the lock is released!
    @Deprecated(
        "This is not yet implemented and need further review, we should move it into IWriteSession",
        level = DeprecationLevel.ERROR
    )
    fun enterLock(id: String, waitMillis: Int64): ILock {
        throw NakshaException(NakshaError.NOT_IMPLEMENTED, "enterLock")
    }

    /**
     * Open a new write session.
     *
     * - Throws [NakshaError.UNINITIALIZED], if [initStorage] has not been called before.
     * @param options additional options.
     * @return the write session.
     * @since 2.0.7
     */
    fun newWriteSession(options: SessionOptions? = null): IWriteSession

    /**
     * Open a new read-only session. The [SessionOptions] can be used to guarantee, that the session relates to the master-node, if replication lags are not acceptable.
     *
     * - Throws [NakshaError.UNINITIALIZED], if [initStorage] has not been called before.
     * @param options additional options.
     * @return the read-only session.
     * @since 2.0.7
     */
    fun newReadSession(options: SessionOptions? = null): IReadSession

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
     * Load the latest [tuples][Tuple] of the features with the given identifiers, from the given collection/map.
     *
     * The fetch modes are:
     * - [all][FETCH_ALL] (_**default**_) - all columns
     * - [all-no-cache][FETCH_ALL] - all columns, but do not access cache (but cache is updated)
     * - [id][FETCH_ID] - id and row-id, rest from cache, if available
     * - [meta][FETCH_META] - metadata and row-id, rest from cache, if available
     * - [cached-only][FETCH_CACHE] - only what is available in cache
     *
     * @param mapId the map from which to load.
     * @param collectionId the collection from to load.
     * @param featureIds a list of feature identifiers to load.
     * @param mode the fetch mode.
     * @return the list of the latest [tuples][Tuple], _null_, if no [tuple][Tuple] was not found.
     * @since 3.0.0
     */
    fun getLatestTuples(mapId: String, collectionId: String, featureIds: Array<String>, mode: String = FETCH_ALL): List<Tuple?>

    /**
     * Load specific [tuples][naksha.model.Tuple].
     *
     * The fetch modes are:
     * - [all][FETCH_ALL] (_**default**_) - all columns
     * - [all-no-cache][FETCH_ALL] - all columns, but do not access cache (but cache is updated)
     * - [id][FETCH_ID] - id and row-id, rest from cache, if available
     * - [meta][FETCH_META] - metadata and row-id, rest from cache, if available
     * - [cached-only][FETCH_CACHE] - only what is available in cache
     *
     * @param tupleNumbers a list of [tuple-numbers][TupleNumber] of the rows to load.
     * @param mode the fetch mode.
     * @return the list of the loaded [tuples][Tuple], _null_, if the tuple was not found.
     * @since 3.0.0
     */
    fun getTuples(tupleNumbers: Array<TupleNumber>, mode: String = FETCH_ALL): List<Tuple?>

    /**
     * Fetches a single result-tuple.
     *
     * The fetch modes are:
     * - [all][FETCH_ALL] (_**default**_) - all columns
     * - [all-no-cache][FETCH_ALL] - all columns, but do not access cache (but cache is updated)
     * - [id][FETCH_ID] - id and row-id, rest from cache, if available
     * - [meta][FETCH_META] - metadata and row-id, rest from cache, if available
     * - [cached-only][FETCH_CACHE] - only what is available in cache
     *
     * @param resultTuple the result-tuple into which to load the tuple.
     * @param mode the fetch mode.
     * @since 3.0.0
     */
    fun fetchTuple(resultTuple: ResultTuple, mode: String = FETCH_ALL)

    /**
     * Fetches all tuples in the given result-tuples.
     *
     * The fetch modes are:
     * - [all][FETCH_ALL] (_**default**_) - all columns
     * - [all-no-cache][FETCH_ALL] - all columns, but do not access cache (but cache is updated)
     * - [id][FETCH_ID] - id and row-id, rest from cache, if available
     * - [meta][FETCH_META] - metadata and row-id, rest from cache, if available
     * - [cached-only][FETCH_CACHE] - only what is available in cache
     *
     * @param resultTuples a list of result-tuples to fetch.
     * @param from the index of the first result-tuples to fetch.
     * @param to the index of the first result-tuples to ignore.
     * @param mode the fetch mode.
     * @since 3.0.0
     */
    fun fetchTuples(resultTuples: List<ResultTuple?>, from:Int = 0, to:Int = resultTuples.size, mode: String = FETCH_ALL)

    /**
     * Shutdown the storage instance, blocks until the storage is down (all sessions are closed).
     *
     * @since 2.0.7
     */
    override fun close()
}