package naksha.plv8

import naksha.model.response.Row
import naksha.base.Int64
import naksha.model.ILock
import naksha.model.NakshaContext
import naksha.model.NakshaFeatureProxy
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
@OptIn(ExperimentalJsExport::class)
@JsExport
interface IStorage {

    /**
     * Storage id
     */
    fun id(): String

    /**
     * Initializes the storage - executes all necessary actions and scripts on it.
     */
    fun initStorage()

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
}