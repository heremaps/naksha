package naksha.model

import naksha.base.Int64
import naksha.model.response.Row
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.js.JsName

// FIXME TODO move it to proper library

/**
 * Any entity implementing the IStorage interface represents some data-sink and comes with an implementation that grants access to the data.
 * The storage normally is a singleton that opens many sessions, each representing an individual connection to the storage.
 * The default context is taken from the thread that opens the context, via NakshaContext->currentContext().
 *
 * The storage may or may not support dictionaries, but in any case it needs to return a dictionary manager (even, if this is only an immutable one with no content).
 */
@OptIn(ExperimentalJsExport::class)
@JsExport
interface IStorage {

    /**
     * Storage id
     */
    fun id(): String

    /**
     * Initializes the storage, create the transaction table, install needed scripts and extensions.
     *
     * @throws StorageException If the initialization failed.
     * @since 2.0.7
     */
    @JsName("initStorageLazy")
    fun initStorage()

    /**
     * Initializes the storage, create the transaction table, install needed scripts and extensions.
     *
     * @param params Special parameters that are storage dependent to influence how a storage is initialized.
     * @throws StorageException If the initialization failed.
     * @since 2.0.8
     */
    fun initStorage( params: Map<String,*>? = null) {
        initStorage()
    }

    /**
     * Might return null if the row doesn't contain attributes to calculate feature properly.
     */
    fun convertRowToFeature(row: Row): NakshaFeatureProxy?

    fun convertFeatureToRow(feature: NakshaFeatureProxy): Row

    // FIXME
    //  fun dictManager(nakshaContext: NakshaContext): IDictManager

    fun enterLock(id: String, waitMillis: Int64): ILock

    /**
     * Opens new read-only session for given context.
     * Use this to execute search queries.
     *
     * @param nakshaContext current context
     * @param useMaster true to use master when running on replica set env.
     */
    fun openReadSession(nakshaContext: NakshaContext, useMaster: Boolean): IReadSession

    /**
     * Opens new write session for given context.
     * Allows to read and write.
     *
     * @param nakshaContext current context
     * @param useMaster true to use master when running on replica set env.
     */
    fun openWriteSession(nakshaContext: NakshaContext, useMaster: Boolean): IWriteSession

    /**
     * Starts the maintainer thread that will take about history garbage collection, sequencing and other background jobs.
     * @since 2.0.7
     */
    fun startMaintainer()

    /**
     * Blocking call to perform maintenance tasks right now. One-time maintenance.
     * @since 2.0.7
     */
    fun maintainNow()

    /**
     * Stops the maintainer thread.
     * @since 2.0.7
     */
    fun stopMaintainer()

    /**
     * Open a new write-session, optionally to a master-node (when being in a multi-writer cluster).
     *
     * @param context   the [NakshaContext] to which to link the session.
     * @param useMaster `true` if the master-node should be connected to; false if any writer is okay.
     * @return the write-session.
     * @throws StorageException If acquiring the session failed.
     * @since 2.0.7
     */
    fun newWriteSession(context: NakshaContext?, useMaster: Boolean): IWriteSession

    /**
     * Open a new read-session, optionally to a master-node to prevent replication lags.
     *
     * @param context   the [NakshaContext] to which to link the session.
     * @param useMaster `true` if the master-node should be connected to, to avoid replication lag; false if any reader is okay.
     * @return the read-session.
     * @throws StorageException If acquiring the session failed.
     * @since 2.0.7
     */
    fun newReadSession(context: NakshaContext?, useMaster: Boolean): IReadSession

    /**
     * Shutdown the storage instance asynchronously. This method returns asynchronously whatever the given `onShutdown` handler returns.
     * If no shutdown handler given, then `null` is returned.
     *
     * @param onShutdown The (optional) method to call when the shutdown is done.
     * @return The future when the shutdown will be done.
     * @since 2.0.7
     */
    fun <T> shutdown( onShutdown: ((IStorage) -> T)? = null)
}