@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.base.Int64
import naksha.base.PlatformMap
import naksha.jbon.IDictManager
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

// FIXME TODO move it to proper library

/**
 * Any entity implementing the IStorage interface represents some data-sink and comes with an implementation that grants access to the data.
 * The storage normally is a singleton that opens many sessions, each representing an individual connection to the storage.
 * The default context is taken from the thread that opens the context, via NakshaContext->currentContext().
 *
 * The storage may or may not support dictionaries, but in any case it needs to return a dictionary manager (even, if this is only an immutable one with no content).
 */
@JsExport
interface IStorage : AutoCloseable {

    /**
     * The storage-id.
     * @throws IllegalStateException if [initStorage] has not been called before.
     */
    fun id(): String

    /**
     * Initializes the storage. First tries to read the storage identifier from the storage. If , create the transaction table, install
     * needed scripts and extensions. If the storage is
     * already initialized; does nothing.
     * @param params optional special parameters that are storage dependent to influence how a storage is initialized.
     * @throws StorageException if the initialization failed.
     * @since 2.0.8
     */
    fun initStorage(params: Map<String, *>? = null)

    /**
     * Convert the given [Row] into a [NakshaFeatureProxy].
     * @param row The row to convert.
     * @return The feature generated from the row.
     */
    fun rowToFeature(row: Row): NakshaFeatureProxy

    /**
     * Convert the given feature into a [Row].
     * @param feature the feature to convert.
     * @return the [Row] generated from the given feature.
     */
    fun featureToRow(feature: PlatformMap): Row

    /**
     * Returns the dictionary manager of the storage.
     * @return The dictionary manager of the storage.
     */
    fun dictManager(nakshaContext: NakshaContext): IDictManager

    // TODO: Fix me!
    fun enterLock(id: String, waitMillis: Int64): ILock

    /**
     * Open a new write session, optionally to a master-node (when being in a multi-writer cluster).
     *
     * @param context The naksha context to use in the session.
     * @return the write session.
     * @throws StorageException If acquiring the session failed.
     * @since 2.0.7
     */
    fun newWriteSession(context: NakshaContext = NakshaContext.currentContext()): IWriteSession

    /**
     * Open a new read-only session, optionally to a master-node to prevent replication lags.
     *
     * @param context The naksha context to use in the session.
     * @param useMaster _true_ if the master-node should be connected to, to avoid replication lag; _false_ if any reader is okay.
     * @return the read-only session.
     * @throws StorageException If acquiring the session failed.
     * @since 2.0.7
     */
    fun newReadSession(context: NakshaContext = NakshaContext.currentContext(), useMaster: Boolean = false): IReadSession

    /**
     * Shutdown the storage instance asynchronously. This method returns asynchronously whatever the given `onShutdown` handler returns.
     * If no shutdown handler given, then `null` is returned.
     *
     * @since 2.0.7
     */
    override fun close()
}