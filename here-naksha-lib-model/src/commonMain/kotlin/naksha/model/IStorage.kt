@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.base.Int64
import naksha.base.PlatformLock
import naksha.jbon.IDictReader
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.jvm.JvmOverloads

/**
 * Any entity implementing the [IStorage] interface represents some data-sink, and comes with an implementation that grants access to the data. The storage normally is a singleton that opens many sessions in parallel.
 *
 * Storages operate on maps. All storages do have an [administrative map][Naksha.ADMIN_MAP], which can be virtual or real, implementation dependent. In this admin-map the storage exposes and manages the custom maps it stores, the transaction-logs of the storage, and the global dictionaries needed for the [JBON](https://github.com/heremaps/naksha/blob/v3/docs/JBON.md), plus optional implementation specific information.
 *
 * All other maps are custom maps, which are isolated data sinks within the same storage (like an own database schema, an own S3 bucket, an own SQLite database, an own directory or file, aso.). Each custom map is a fully separated storage entity. Within each custom map one [virtual admin collection][Naksha.COLLECTIONS_COL] is exposed, which can be used to manage the collections in the map. Some storages allow to access multiple maps from one session, others may limit a session to a single map, and will reject cross map operations with [NakshaError.UNSUPPORTED_OPERATION].
 *
 * The storage will cache the dictionaries to avoid that just for [Tuple] decoding a new session need to be opened, which would require object creation for every single feature being decoded, therefore every storage implements the [IDictReader] interface, which internally should be attached to a storage local cache, that is automatically kept up-to-date. The same cache can be accessed from every [session][ISession], because every [session][ISession] implements as well the [dictionary-reader interface][IDictReader].
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
     * @since 3.0.0
     */
    val lock: PlatformLock

    /**
     * The configuration object with which this storage was initialized.
     *
     * **Warning**: Modification of the returned configuration object will not have any impact on the storage, but it can provide wrong information to other callers of the function, so this should be avoided, apart from that the configuration object is not thread safe!
     * - Throws [NakshaError.UNINITIALIZED], if not initialized.
     * @since 3.0.0
     */
    val config: StorageConfig

    /**
     * The storage-id, optionally stored in the storage, must always be the same for the same physical storage.
     *
     * - Throws [NakshaError.UNINITIALIZED], if the storage failed to initialize.
     * @since 2.0.8
     */
    val id: String

    /**
     * The storage-number, managed by environment, optionally stored in the storage, must always be the same for the same physical storage.
     *
     * - Throws [NakshaError.UNINITIALIZED], if the storage failed to initialize.
     * @since 3.0.0
     */
    val number: Int64

    /**
     * The hard-cap _(max result size)_ of the storage. No result-set every can become bigger than this amount of features.
     *
     * Setting the value is optionally support, storages may throw an [NakshaError.UNSUPPORTED_OPERATION] exception, when trying to modify the hard-cap, or they may only allow certain values and throw an [NakshaError.ILLEGAL_ARGUMENT] exception, if the value too big. Zero and negative values are changed into the maximum of whatever the storage supports, [Int.MAX_VALUE] means no hard-cap (if supported by the storage).
     *
     * Note that technically, due to binary encoding, there is normally a hard-cap at `16777216`.
     * @since 3.0.0
     */
    val hardCap: Int

    /**
     * Open a new write session.
     *
     * - Throws [NakshaError.UNINITIALIZED], if the storage failed to initialize.
     * @param options additional options, _null_ automatically creates them from the current [NakshaContext].
     * @return the write session.
     * @since 2.0.7
     */
    fun newWriteSession(options: SessionOptions? = null): IWriteSession

    /**
     * Open a new read-only session. The [SessionOptions] can be used to guarantee, that the session relates to the master-node, if replication lags are not acceptable.
     *
     * - Throws [NakshaError.UNINITIALIZED], if the storage failed to initialize.
     * @param options additional options, _null_ automatically creates them from the current [NakshaContext].
     * @return the read-only session.
     * @since 2.0.7
     */
    fun newReadSession(options: SessionOptions? = null): IReadSession

    /**
     * The best flags to encode the given feature.
     *
     * - Throws [NakshaError.UNINITIALIZED], if the storage failed to initialize.
     * @param feature the feature to encode; _null_ if no specific one is available.
     * @param context the context in which the encoding happens (for example the [map][naksha.model.objects.NakshaMap] or [collection][naksha.model.objects.NakshaCollection]); _null_ if none is available.
     * @return best flags to use for encoding.
     * @since 3.0.0
     */
    fun getEncodingFlags(feature: Any?, context: Any? = null): Flags
}