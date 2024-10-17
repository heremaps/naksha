@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.base.Int64
import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * Any entity implementing the [IStorage] interface represents some data-sink, and comes with an implementation that grants access to the data. The storage normally is a singleton that opens many sessions in parallel.
 *
 * Storages operate on maps. A map is an isolated data sink within the same storage (like an own database schema, an own S3 bucket, an own SQLite database, an own directory or file, aso.). Some implementations only support one map, but if multiple maps are supported, a map is a fully separated storage entity. Each map has its own collections, while the transaction log, dictionaries, and other administrative information are stored in an own virtual map named `naksha~admin`. Some storages allow to access multiple maps from one session, others may limit a session to a single map.
 *
 * The [ITupleCodec] implementation of a storage normally accepts as context [IMap] or [ICollection]. Providing a map-number, map-id, collection-number or collection-id would be ambiguous, because it would not be clear if a map is referred to, or a collection. The implementation may automatically resolve such ambiguity in any way or simply ignore such a context. Therefore, it is recommended to provide the instances, rather than the number or id.
 *
 * @since 2.0.7
 */
@JsExport
interface IStorage : ITupleCodec, AutoCloseable {

    /**
     * The storage-number, managed by environment, optionally stored in the storage, must always be the same for the same physical storage.
     *
     * - Throws [NakshaError.UNINITIALIZED], if [initStorage] has not been called before.
     * @since 3.0.0
     */
    val number: Int64

    /**
     * The storage-id, optionally stored in the storage, must always be the same for the same physical storage.
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
     * The hard-cap (limit) of the storage. No result-set every should become bigger than this amount of features.
     *
     * Setting the value is optionally support, storages may throw an [NakshaError.UNSUPPORTED_OPERATION] exception, when trying to modify the hard-cap, or they may only allow certain values and throw an [NakshaError.ILLEGAL_ARGUMENT] exception, if the value too big. A negative value is changed into [Int.MAX_VALUE], which means no hard-cap (if supported by the storage).
     * @since 3.0.0
     */
    var hardCap: Int

    /**
     * Tests if this storage is initialized, so [initStorage] has been called.
     * @return _true_ if this storage is initialized; _false_ otherwise.
     * @since 3.0.0
     */
    fun isInitialized(): Boolean

    /**
     * Initializes the storage.
     *
     * If necessary, this method will create the storage structures to store transactions, install needed scripts, extensions, and do all other initialization works. If the storage is already initialized, the given storage-identifier, and storage-number, must match the existing ones, otherwise a new storage is initialized, adding the storage-id and storage-number.
     *
     * This operation requires that the current [context][NakshaContext] has the [superuser][NakshaContext.su] rights.
     *
     * This method will register the storage with the [NakshaCache].
     *
     * - Throws [NakshaError.FORBIDDEN], if not called as super-user.
     * @param id the identifier of the storage (_added in v3.0.0_).
     * @param number the number of the storage (_added in v3.0.0_).
     * @param params optional special parameters that are storage dependent to influence how a storage is initialized.
     * @since 2.0.8
     */
    fun initStorage(id: String, number: Int64, params: Map<String, *>? = null)

    /**
     * Re-Read all map information from the storage to update the map cache.
     *
     * The method will avoid parallel invocation in multiple threads using a lock, internally some very short minimum cache time will be applied, for example one second, to avoid that heavy usage of this method causes too many database requests.
     *
     * The method will not update the collection cache of the maps, but if a map was removed, it will remove the map including all collections cached.
     * @return this
     * @since 3.0.0
     */
    @v30_experimental
    fun refreshMaps(): IStorage

    /**
     * Returns the map admin object.
     *
     * Does not perform network operations. If the map details are not yet loaded from storage, creates a virtual admin object, that allows the management of the map, like to query if such a map exists already, or to create the map. Creating a map will keep it in the cache, and grant it a unique map-number, provided by the storage.
     *
     * - Throws [NakshaError.UNINITIALIZED], if [initStorage] has not been called before.
     * @param mapId the map-id.
     * @return the map admin object.
     * @since 3.0.0
     */
    @JsName("getMapById")
    @v30_experimental
    operator fun get(mapId: String): IMap

    /**
     * Returns the map admin object.
     *
     * - Throws [NakshaError.UNINITIALIZED], if [initStorage] has not been called before.
     * @param mapNumber the map-number.
     * @return the map admin object, _null_, if no such map exists.
     * @since 3.0.0
     */
    @v30_experimental
    @JsName("getMapByNumber")
    operator fun get(mapNumber: Int): IMap?

    /**
     * Tests if this storage contains the given map.
     *
     * - Throws [NakshaError.UNINITIALIZED], if [initStorage] has not been called before.
     * @param mapId the id of the map to test for.
     * @return _true_ if such a map exists; _false_ otherwise.
     * @since 3.0.0
     */
    @v30_experimental
    @JsName("containsMapId")
    operator fun contains(mapId: String): Boolean

    /**
     * Tests if this storage contains the given map.
     *
     * - Throws [NakshaError.UNINITIALIZED], if [initStorage] has not been called before.
     * @param mapNumber the number of the map to test for.
     * @return _true_ if such a map exists; _false_ otherwise.
     * @since 3.0.0
     */
    @v30_experimental
    @JsName("containsMapNumber")
    operator fun contains(mapNumber: Int): Boolean

    /**
     * Query the map-identifier for the given map-number.
     *
     * - Throws [NakshaError.UNINITIALIZED], if [initStorage] has not been called before.
     * @param mapNumber the map-number.
     * @return the map-identifier or _null_, if no such map exists.
     * @since 3.0.0
     */
    @v30_experimental
    fun getMapId(mapNumber: Int): String?

    /**
     * Query the map-identifier for the given [tuple-number][TupleNumber].
     *
     * - Throws [NakshaError.UNINITIALIZED], if [initStorage] has not been called before.
     * @param tupleNumber the tuple-number.
     * @return the map-identifier or _null_, if no such map exists, or the tuple is stored in another storage.
     * @since 3.0.0
     */
    @JsName("getMapIdByTupleNumber")
    @v30_experimental
    fun getMapId(tupleNumber: TupleNumber): String? {
        if (tupleNumber.storageNumber != number) return null
        return getMapId(tupleNumber.mapNumber())
    }

    /**
     * Query the collection-id of the given tuple-number.
     * - Throws [NakshaError.UNINITIALIZED], if [initStorage] has not been called before.
     * @return the collection-id or _null_, if no such map and/or collection exists, or the tuple is stored in another storage.
     * @since 3.0.0
     */
    @v30_experimental
    fun getCollectionId(tupleNumber: TupleNumber): String? {
        if (tupleNumber.storageNumber != number) return null
        val map = get(tupleNumber.mapNumber()) ?: return null
        if (!map.exists()) return null
        return map.getCollectionId(tupleNumber.collectionNumber())
    }

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
    @v30_experimental
    fun enterLock(id: String, waitMillis: Int64): ILock {
        throw NakshaException(NakshaError.NOT_IMPLEMENTED, "enterLock")
    }

    /**
     * Open a new write session.
     *
     * - Throws [NakshaError.UNINITIALIZED], if [initStorage] has not been called before.
     * @param options additional options, _null_ automatically creates them from the current [NakshaContext].
     * @return the write session.
     * @since 2.0.7
     */
    fun newWriteSession(options: SessionOptions? = null): IWriteSession

    /**
     * Open a new read-only session. The [SessionOptions] can be used to guarantee, that the session relates to the master-node, if replication lags are not acceptable.
     *
     * - Throws [NakshaError.UNINITIALIZED], if [initStorage] has not been called before.
     * @param options additional options, _null_ automatically creates them from the current [NakshaContext].
     * @return the read-only session.
     * @since 2.0.7
     */
    fun newReadSession(options: SessionOptions? = null): IReadSession

    /**
     * Shutdown the storage instance, blocks until the storage is down (all sessions are closed).
     *
     * This method will remove the instance from the [NakshaCache].
     *
     * @since 2.0.7
     */
    override fun close()
}