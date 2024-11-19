@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.base.Int64
import naksha.jbon.IDictReader
import kotlin.js.JsExport

/**
 * Any entity implementing the [IStorage] interface represents some data-sink, and comes with an implementation that grants access to the data. The storage normally is a singleton that opens many sessions in parallel.
 *
 * Storages operate on maps. All storages do have an administrative map ([Naksha.VIRT_ADMIN_MAP]), which can be virtual or real, implementation dependent. In this virtual admin-map the storage exposes and manages the custom maps it stores, the transaction-logs of the storage, and the global dictionaries needed for the [JBON](https://github.com/heremaps/naksha/blob/v3/docs/JBON.md).
 *
 * All other maps are custom maps, which are isolated data sinks within the same storage (like an own database schema, an own S3 bucket, an own SQLite database, an own directory or file, aso.). Each custom map is a fully separated storage entity. Some storages allow to access multiple maps from one session, others may limit a session to a single map, and will reject cross map operations with [NakshaError.UNSUPPORTED_OPERATION].
 *
 * The storage will cache the dictionaries to avoid that just for decoding a new session need to be opened, which would require object creation for every single feature decoding, therefore every storage implements the [IDictReader] interface, which internally should be attached to a storage local cache, that is automatically kept up-to-date.
 *
 * @since 2.0.7
 */
@JsExport
interface IStorage : IDictReader, AutoCloseable {

    /**
     * The storage-id, optionally stored in the storage, must always be the same for the same physical storage.
     *
     * - Throws [NakshaError.UNINITIALIZED], if [initStorage] has not been called before.
     * @since 2.0.8
     */
    val id: String

    /**
     * The storage-number, managed by environment, optionally stored in the storage, must always be the same for the same physical storage.
     *
     * - Throws [NakshaError.UNINITIALIZED], if [initStorage] has not been called before.
     * @since 3.0.0
     */
    val number: Int64

    /**
     * The admin options to use for internal processing.
     *
     * They are needed for administrative work, reading dictionaries, collection information, create administrative structures. The application can override the defaults to have more control over the `appId` and/or `author` being written, when internal data is processed, and how internal connections authenticate (`appName`). The default is, when creating an admin-context, to use the values from the current thread-local [NakshaContext].
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
     * This method will register the storage with the [Naksha].
     *
     * - Throws [NakshaError.FORBIDDEN], if not called as super-user.
     * - Throws [NakshaError.INITIALIZATION_FAILED], if the initialization failed.
     * - Throws [NakshaError.STORAGE_ID_MISMATCH], if the existing _storage-id_ and/or _storage-number_ does not match the given one.
     * @param id the identifier of the storage (_added in v3.0.0_).
     * @param number the number of the storage (_added in v3.0.0_).
     * @param setup if the storage is not setup, do a setup; if _false_, the method will throw an [NakshaError.INITIALIZATION_FAILED] exception (_added in v3.0.0_).
     * @param params optional special parameters that are storage dependent to influence how a storage is initialized.
     * @since 2.0.8
     */
    fun initStorage(id: String, number: Int64, setup: Boolean = true, params: Map<String, *>? = null)

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
     * This method will remove the instance from the [Naksha].
     *
     * @since 2.0.7
     */
    override fun close()

    // fun getDictionary(id: String): JbDictionary? = storage.getDictionary(id)

    /**
     * The best flags to encode the given feature.
     *
     * @param feature the feature to encode; _null_ if no specific one is available.
     * @param context the context in which the encoding happens (for example the [map][IMap] or [collection][ICollection]); _null_ if none is available.
     * @return best flags to use for encoding.
     * @since 3.0.0
     */
    fun getEncodingFlags(feature: Any?, context: Any? = null): Flags = DEFAULT_FLAGS
}