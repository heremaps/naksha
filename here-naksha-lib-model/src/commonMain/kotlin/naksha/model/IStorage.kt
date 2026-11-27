@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.base.Id
import naksha.base.PlatformLock
import naksha.base.fn.Fn1
import naksha.base.fn.Fx1
import naksha.jbon.IDictReader
import naksha.model.objects.NakshaDatabase
import naksha.model.objects.NakshaStorage
import kotlin.js.JsExport

/**
 * Any entity implementing the [IStorage] interface represents some data-sink and comes with an implementation that grants access to the data. The storage normally is a singleton that opens many sessions in parallel.
 *
 * Storages operate on [catalogs][naksha.model.objects.NakshaCatalog]. All storages do have an [administrative catalog][naksha.model.objects.NakshaAdmin] which can be virtual or real, implementation dependent. In this [admin-catalog][naksha.model.objects.NakshaAdmin] the storage exposes and manages the custom catalogs it stores, the transaction-logs of the storage, and the global books needed for the [JBON](https://github.com/heremaps/naksha/blob/v3/docs/JBON.md), plus optional implementation specific information.
 *
 * All [catalogs][naksha.model.objects.NakshaCatalog], except for the [admin-catalog][naksha.model.objects.NakshaAdmin], are [custom catalogs][naksha.model.objects.NakshaCatalog], which are isolated data sinks within the same storage (like a schema, an S3 bucket, an SQLite database file, a directory, aso.). Each [custom catalog][naksha.model.objects.NakshaCatalog] is a fully separated storage entity. Within each [custom catalog][naksha.model.objects.NakshaCatalog] one [admin collection][naksha.model.objects.NakshaAdminCollections] is exposed that can be used to manage the collections in the catalog. Some storages allow to access multiple catalogs from one session, others may limit a session to a single catalog, and will reject cross catalog operations with [UNSUPPORTED_OPERATION][naksha.base.NakshaError.UNSUPPORTED_OPERATION] errors.
 *
 * The storage will cache books, [catalogs][naksha.model.objects.NakshaCatalog], and [collections][naksha.model.objects.NakshaCollection]. The cache can be accessed from every [session][ISession].
 *
 * @since 2.0.7
 */
@JsExport
interface IStorage : IDictReader {

    /**
     * A lock to be used to synchronize access to this storage.
     *
     * Usage like:
     * ```kotlin
     * storage.lock.acquire().use {
     *   // use storage
     * }
     * ```
     * @since 3.0
     */
    val lock: PlatformLock

    /**
     * The configuration object with which this storage was initialized.
     *
     * **Warning**: Modification of the returned configuration object will not have any impact on the storage, but it can provide wrong information to other callers of the function, so this should be avoided, apart from that the configuration object is not thread safe!
     * @since 3.0
     * @throws naksha.base.NakshaException with error [UNINITIALIZED][naksha.base.NakshaError.UNINITIALIZED], if the storage failed to initialize.
     */
    val config: NakshaStorage

    /**
     * The storage-id, must always be the same for the same physical storage.
     * @since 2.0.8
     * @throws naksha.base.NakshaException with error [UNINITIALIZED][naksha.base.NakshaError.UNINITIALIZED], if the storage failed to initialize.
     */
    val id: Id

    /**
     * The database to which this storage is linked.
     * @since 3.0
     */
    val database: NakshaDatabase

    /**
     * The hard-cap _(max result size)_ of the storage. No result-set every can become bigger than this amount of features.
     *
     * Setting a value less than or equal to `0` means that there is no hard-cap _(unlimited)_, it will raise the cap to [Int.MAX_VALUE]. Storages can adjust this maximum value to their needs. The default is no limit _(so actually [Int.MAX_VALUE])_. Applications can modify this value to ensure that queries to not extend some hard limits, even when the storage would support this. Therefore, applications can lower the value, but not raise it beyond what the storage is capable of doing, the storage will not allow values below `1` or beyond what it supports as maximum.
     * @since 3.0
     */
    val hardCap: Int

    /**
     * Open a new write session.
     * @param options additional options, _null_ automatically creates them from the current [NakshaContext].
     * @return the write session.
     * @since 2.0.7
     * @throws naksha.base.NakshaException with error [UNINITIALIZED][naksha.base.NakshaError.UNINITIALIZED], if the storage failed to initialize; error [UNSUPPORTED_OPERATION][naksha.base.NakshaError.UNSUPPORTED_OPERATION] if this session type is not supported by the underlying implementation.     */
    fun newWriteSession(options: SessionOptions? = null): IWriteSession

    /**
     * Open a new write-session and execute the given lambda, ensuring that the session is closed after the lambda returns.
     * @param options the session-options.
     * @param lambda the lambda to execute in a try block, ensuring that the session is closed.
     * @return the result of the lambda.
     */
    fun <T> useWriteSession(options: SessionOptions? = null, lambda: Fn1<T, IWriteSession>): T {
        val session = newWriteSession(options)
        return session.use { lambda.call(session) }
    }

    /**
     * Open a new write-session and execute the given void lambda, ensuring that the session is closed after the lambda returns.
     * This is very similar to [useWriteSession] but it's not returning any value.
     * @param options the session-options.
     * @param lambda the void lambda to execute in a try block, ensuring that the session is closed.
     * @throws naksha.base.NakshaException with error [UNINITIALIZED][naksha.base.NakshaError.UNINITIALIZED], if the storage failed to initialize; error [UNSUPPORTED_OPERATION][naksha.base.NakshaError.UNSUPPORTED_OPERATION] if this session type is not supported by the underlying implementation.
     */
    fun runInWriteSession(options: SessionOptions? = null, lambda: Fx1<IWriteSession>) {
        val session = newWriteSession(options)
        session.use { lambda.call(session) }
    }

    /**
     * Open a new read-only session. The [SessionOptions] can be used to guarantee, that the session relates to the master-node, if replication lags are not acceptable.
     *
     * @param options additional options, _null_ automatically creates them from the current [NakshaContext].
     * @return the read-only session.
     * @since 2.0.7
     * @throws naksha.base.NakshaException with error [UNINITIALIZED][naksha.base.NakshaError.UNINITIALIZED], if the storage failed to initialize; error [UNSUPPORTED_OPERATION][naksha.base.NakshaError.UNSUPPORTED_OPERATION] if this session type is not supported by the underlying implementation.
     */
    fun newReadSession(options: SessionOptions? = null): IReadSession

    /**
     * Open a new read-session and execute the given lambda, ensuring that the session is closed after the lambda returns.
     * @param options the session-options.
     * @param lambda the lambda to execute in a try block, ensuring that the session is closed.
     * @return the result of the lambda.
     * @throws naksha.base.NakshaException with error [UNINITIALIZED][naksha.base.NakshaError.UNINITIALIZED], if the storage failed to initialize; error [UNSUPPORTED_OPERATION][naksha.base.NakshaError.UNSUPPORTED_OPERATION] if this session type is not supported by the underlying implementation.
     */
    fun <T> useReadSession(options: SessionOptions? = null, lambda: Fn1<T, IReadSession>): T {
        val session = newReadSession(options)
        return session.use { lambda.call(session) }
    }

    /**
     * Open a new read-session and execute the given lambda, ensuring that the session is closed after the lambda returns.
     * This is very similar to [useReadSession] but it's not returning any value.
     * @param options the session-options.
     * @param lambda the void lambda to execute in a try block, ensuring that the session is closed.
     * @throws naksha.base.NakshaException with error [UNINITIALIZED][naksha.base.NakshaError.UNINITIALIZED], if the storage failed to initialize; error [UNSUPPORTED_OPERATION][naksha.base.NakshaError.UNSUPPORTED_OPERATION] if this session type is not supported by the underlying implementation.
     */
    fun runInReadSession(options: SessionOptions? = null, lambda: Fx1<IReadSession>) {
        val session = newReadSession(options)
        session.use { lambda.call(session) }
    }

    /**
     * Open a new streaming session to read or write data in streaming mode.
     *
     * @param options additional options, _null_ automatically creates them from the current [NakshaContext].
     * @return the streaming session.
     * @since 3.0
     * @throws naksha.base.NakshaException with error [UNINITIALIZED][naksha.base.NakshaError.UNINITIALIZED], if the storage failed to initialize; error [UNSUPPORTED_OPERATION][naksha.base.NakshaError.UNSUPPORTED_OPERATION] if this session type is not supported by the underlying implementation.
     */
    fun newStreamSession(options: SessionOptions? = null): IStreamSession
}
