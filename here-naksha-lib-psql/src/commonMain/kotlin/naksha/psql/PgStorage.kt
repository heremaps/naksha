@file:Suppress("unused")

package naksha.psql

import naksha.base.*
import naksha.base.fn.Fx2
import naksha.model.*
import naksha.model.NakshaError.NakshaErrorCompanion.STORAGE_ID_MISMATCH
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
 * @since 3.0.0
 */
@Suppress("OPT_IN_USAGE")
@JsExport
abstract class PgStorage protected constructor() : AbstractStorage<PgConfig>() {

    private var _adminMap: PgAdminMap? = null

    /**
     * The OID of the admin-map, set by [initStorage].
     */
    open val adminMap: PgAdminMap
        get() {
            return _adminMap ?: throw NakshaException(UNINITIALIZED, "Storage uninitialized")
        }

    /**
     * Test if this storage has the given _id_ and _number_.
     * - Throws [STORAGE_ID_MISMATCH], if an invalid _id_ or _number_ was given.
     * @param id the expected _id_.
     * @param number the expected _number_.
     * @return _true_ if the current _id_ and _number_ are the given-expected ones; _false_ if the storage is not initialized.
     */
//    internal fun isIdAndNumber(id: String, number: Int64): Boolean {
//        if (this.id != id) {
//            throw NakshaException(STORAGE_ID_MISMATCH, "The storage-id is '${this.id}', but was expected to be '$id'")
//        }
//        if (this.number != number) {
//            throw NakshaException(STORAGE_ID_MISMATCH, "The storage-number is '${this.number}', but was expected to be '$number'")
//        }
//        return true
//    }

    /**
     * An internal method invoked by [initStorage] to create the admin-map instance. The implementation will instantiate the concrete implementation of [PgAdminMap].
     *
     * Actually there are only two implementations, one for the JVM, which is able to install or upgrading SQL functions, creating schema, tables, as well as admin-collections, and the other one that is executed within the [PLV8 extension](https://plv8.github.io/) of the PostgresQL database itself, which requires that the admin-map is already setup. Future other implementations may be done for [NodeJS](https://nodejs.org/en/) or browsers (which are as well are not able to really create a new admin-map).
     *
     * This operation is executing with in the storage [lock], so that it can be sure that no other thread is doing the same thing.
     * @param config the configuration as required.
     * @param create if not _null_, overrides [StorageConfig.create].
     * @param upgrade if not _null_, overrides [StorageConfig.upgrade].
     * @return the OID of the admin schema.
     * @since 3.0.0
     */
    protected abstract fun initAdminMap(config: PgConfig, create: Boolean?, upgrade: Boolean?): PgAdminMap

    // Called from invokeInitStorage, so within a lock!
    override fun initStorage(config: PgConfig, create: Boolean?, upgrade: Boolean?) {
        _adminMap = initAdminMap(config, create, upgrade)
    }

    /**
     * The default flags to use for the storage.
     * @return default flags to use for the storage.
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
     * @since 3.0.0
     */
    abstract fun newConnection(options: SessionOptions, readOnly: Boolean, init: Fx2<PgConnection, String>? = null): PgConnection
//    {
//        val conn = cluster.newConnection(options, readOnly)
//        val query = "SET SESSION search_path TO \"naksha~admin\", hint_plan, public, topology;\n"
//        if (init != null) init.call(conn, query) else conn.execute(query).close()
//        return conn
//    }

    /**
     * Opens an admin connection.
     *
     * This is the same as [newConnection], except that it can be implemented differently, for example on the [PLV8 engine](https://plv8.github.io/). Basically, this method acquires a special connection that is only used for a short moment of time to do some administrative work.
     *
     * **WARNING**: This method is only for internal purpose, to avoid breaking the code on `PLV8`.
     *
     * @return the admin connection that does not count against connection-limit, to be closed after usage.
     * @since 3.0.0
     */
    abstract fun adminConnection(): PgConnection
    //    = newConnection(options, false, init)
}

// PgSession(storage: PgStorage)
// - commit()
//   - before committing we need to write the transaction
//   - we need to do it as last action, short before we commit, because this allows us to create the admin map, and then to insert the transaction-log into the transaction-log table we just created within the same session
//   - as transactions are always persisted in the admin-map, we know it exists!

// PgStorage(config: PgConfig)
// - on init
//   - read the admin data, and create a root PgMap (`adminMap: PgMap`)
//   - optionally create/upgrade storage
//     - create admin schema
//     - install scripts, plv8, ...
//     - create map-sequence (managed by PgStorage)
//     - core collections using PgCollection
//
//  createAdminMap(): PgAdminMap
//  upgradeAdminMap(): PgAdminMap
//  adminMap: PgAdminMap

