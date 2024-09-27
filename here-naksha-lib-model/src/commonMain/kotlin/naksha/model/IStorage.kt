@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.base.Int64
import naksha.model.objects.NakshaFeature
import kotlin.js.JsExport
import kotlin.js.JsName

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
     * The hard-cap (limit) of the storage. No result-set every should become bigger than this amount of features.
     *
     * Setting the value is optionally support, storages may throw an [NakshaError.UNSUPPORTED_OPERATION] exception, when trying to modify the hard-cap, or they may only allow certain values and throw an [NakshaError.ILLEGAL_ARGUMENT] exception, if the value too big. A negative value is changed into [Int.MAX_VALUE], which means no hard-cap (if supported by the storage).
     */
    var hardCap: Int

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
     * Returns the map admin object.
     *
     * - Throws [NakshaError.UNINITIALIZED], if [initStorage] has not been called before.
     * @param mapNumber the map-number.
     * @return the map admin object, _null_, if no such map exists.
     */
    @JsName("getByNumber")
    operator fun get(mapNumber: Int): IMap?

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
     * @param tuple the tuple to convert.
     * @return the feature generated from the tuple.
     * @since 3.0.0
     */
    fun tupleToFeature(tuple: Tuple): NakshaFeature

    /**
     * Convert the given feature into a [Tuple].
     *
     * - Throws [NakshaError.UNINITIALIZED], if [initStorage] has not been called before.
     * @param feature the feature to convert.
     * @return the [Tuple] generated from the given feature.
     * @since 3.0.0
     */
    fun featureToTuple(feature: NakshaFeature): Tuple

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
     * Shutdown the storage instance, blocks until the storage is down (all sessions are closed).
     *
     * @since 2.0.7
     */
    override fun close()
}