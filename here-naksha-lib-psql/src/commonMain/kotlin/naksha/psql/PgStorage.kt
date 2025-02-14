@file:Suppress("unused")

package naksha.psql

import naksha.base.fn.Fx2
import naksha.model.*
import naksha.model.NakshaError.NakshaErrorCompanion.UNINITIALIZED
import kotlin.js.JsExport

// TODO: Create "naksha~admin" map with map-number 0
//       Create the "naksha~transactions", "naksha~dictionaries" collections in it
//       Additionally create a new "naksha~maps" collection, in which we store maps the way we store collections in "naksha~collections"
//       We keep all maps all the time in memory (using refreshMaps).
//       Always keep it in the path, install scripts into it
//       Create the map-number-seq in it
//       Add `naksha_storage_number` method in it
//       Install scripts into it
//       Creating a map is then simply creating a schema with the "naksha~collections", and `col-number-seq`
//       Register background thread to listen for notifications
//       Send notifications whenever "naksha~transactions" is written
//       If maps are created/deleted, we should update the caches
//       Create a mechanism to call-back to the host, so allow the host to register a transaction-listener
//       Split the work into steps, initially, lets use md5-hash above map-id (schema-name) as map-number

/**
 * A storage implementation based upon PostgresQL database.
 *
 * This class is a default multi-platform implementation of the [IStorage] interface for the PostgresQL database. To get an instance of it, a platform specific code has to be used.
 *
 * ### JVM / Java,Scala,Kotlin,...
 * On the JVM platform, create an instance using a configuration, and operate with it like for example:
 * ```kotlin
 * val config = PgConfig()
 *   .withId("demo")
 *   .withClassName("naksha.psql.PgStorageImpl")
 *   .withMaster(PgInstanceConfig()
 *     .withHost("host")
 *     .withDb("testdb")
 *     .withUser("fred")
 *     .withPassword("secret"))
 * val storage = Naksha.useStorage(config)
 * storage.newReadSession().use { session ->
 *   ...
 * }
 * ```
 * If needed, you can cast the returned [IStorage] up to [PgStorage] or even `JvmPgStorage`.
 *
 * ### JavaScript / PLV8
 * When using Naksha storage within PostgresQL, an initialized storage is needed. Ones a storage was initialized from external, every PostgresQL session can prepare usage via:
 * ```SQL
 * SELECT naksha_init_session('id', 12345678, 'appName', 'appId', 'author');
 * ```
 * This creates a [PgStorage] singleton in the global context (`globalThis.naksha.storage`), a session singleton (`globalThis.naksha.session`), and the `NakshaContext` (`globalThis.naksha.context`). The _session_ is the one that is currently being used, and normally an [IWriteSession], even when execute on a read-replica, as the internal PLV8 code does not know that this instance is a read-replica. This is necessary for all other Naksha SQL functions to work. The storage only support a single [PgSession], which is already exposed via `naksha.session`, trying to acquire another session will always fail with [NakshaError.ILLEGAL_STATE]. Actually, within PLV8 each `plv8` session is always bound to a single connection/session. Usage example:
 * ```
 * SELECT naksha_init_session('id', 12345678, 'appName', 'appId', 'author');
 * DO $$
 *   // Print storage-id to server output.
 *   plv8.elog(NOTICE, "Hello storage " + naksha.storage.id);
 *   // All code that requires a ISession, should use:
 *   plv8.elog(NOTICE, naksha.session.getMapById("foo").number);
 * $$ LANGUAGE plv8;
 * ```
 * After the session was closed, a new call to `naksha_init_session` should be done.
 *
 * If needed, you can cast the returned [IStorage] up to [PgStorage], or even to `JsPgStorage`.
 *
 * ### JavaScript / Browser
 * TBD, technically every connection should be represented using a single WebSocket.
 *
 * ### JavaScript / Node
 * TBD, technically every connection can be represented by a real PostgresQL connection, the same way Java does it.
 * @since 3.0
 */
@Suppress("OPT_IN_USAGE")
@JsExport
abstract class PgStorage protected constructor() : AbstractStorage<PgConfig>() {

    private var _adminMap: PgAdminMap? = null

    /**
     * The admin-map, set by [initStorage].
     * @since 3.0
     */
    open val adminMap: PgAdminMap
        get() = _adminMap ?: throw NakshaException(UNINITIALIZED, "Storage uninitialized")

    /**
     * Private setter for the admin map, to be used in the deriving storage class.
     * @since 3.0
     */
    protected fun setAdminMap(adminMap: PgAdminMap) {
        _adminMap = adminMap
    }

    /**
     * The default flags to use for the storage.
     * @return default flags to use for the storage.
     * @since 3.0
     */
    internal val defaultFlags: Flags = Flags()
        .withFeatureEncoding(FeatureEncoding.JBON_GZIP)
        .withGeoEncoding(GeoEncoding.TWKB_GZIP)
        .withTagsEncoding(TagsEncoding.JBON_GZIP)

    override fun newWriteSession(options: SessionOptions?): IWriteSession =
        newSession(options ?: SessionOptions.from(null), false)

    override fun newReadSession(options: SessionOptions?): IWriteSession =
        newSession(options ?: SessionOptions.from(null), true)

    /**
     * Returns a new PostgresQL session.
     *
     * This method is invoked from [newReadSession] and [newWriteSession], just with adjusted [options].
     * @param options the session options.
     * @param readOnly if the session should be read-only.
     * @return the session.
     * @since 3.0
     */
    open fun newSession(options: SessionOptions, readOnly: Boolean): PgSession = PgSession(this, options, readOnly)

    /**
     * Opens a new PostgresQL database connection.
     *
     * A connection received through this method will not really close when [PgConnection.close] is invoked, but the wrapper returns the underlying JDBC connection to the connection pool of the instance it received it from. If really necessary, [PgConnection.terminate] can be used for this case (for example to ensure advisory locks are released).
     *
     * If this is the [PLV8 engine](https://plv8.github.io/), then there is only one connection available, so calling this before closing
     * a previously acquired connection will always cause an [NakshaError.TOO_MANY_CONNECTIONS].
     *
     * The returned connection normally, unless a special [init] function was provided, initializes the search-path so that all naksha function are available, and the admin schema is at the top of the search-path (recommended setup).
     *
     * - Throws [naksha.model.NakshaError.TOO_MANY_CONNECTIONS], if no more connections are available.
     * @param options the options for the connection.
     * @param readOnly if the connection should be read-only.
     * @param init an optional initialization function, if given, then it will be called with the string to be used to initialize the connection. It may just use this string, perform arbitrary additional work, or suppress initialization completely.
     * @since 3.0
     */
    abstract fun newConnection(options: SessionOptions, readOnly: Boolean, init: Fx2<PgConnection, String>? = null): PgConnection

    /**
     * Opens an admin connection.
     *
     * This is the same as [newConnection], except that it can be implemented differently, for example on the [PLV8 engine](https://plv8.github.io/). Basically, this method acquires a special connection that is only used for a short moment of time to do some administrative work.
     *
     * **WARNING**: This method is only for internal purpose, to avoid breaking the code on `PLV8`.
     *
     * @return the admin connection that does not count against connection-limit, to be closed after usage.
     * @since 3.0
     */
    abstract fun adminConnection(): PgConnection
}