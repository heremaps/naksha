@file:Suppress("unused")

package naksha.psql

import naksha.base.Id
import naksha.base.fn.Fx2
import naksha.model.*
import naksha.model.objects.NakshaStorage
import kotlin.js.JsStatic
import kotlin.jvm.JvmStatic
import kotlin.reflect.KClass

/**
 * A storage implementation based upon PostgresQL database.
 *
 * This class is a multi-platform implementation of the [IStorage] interface for the PostgresQL database.
 *
 * ### JVM
 * Create an instance using a configuration, and operate with it like for example:
 * ```kotlin
 * val instance = PgInstanceConfig()
 *     .withHost("host")
 *     .withDb("testdb")
 *     .withUser("fred")
 *     .withPassword("secret")
 * val config = PgConfig()
 *     .withId("demo")
 *     .withMaster(instance)
 * val storage = PgStorage(config)
 * storage.newReadSession().use { session ->
 *   // ...
 * }
 * ```
 * The instance of the storage should be kept somewhere in a static member.
 *
 * ### JavaScript / PLV8
 * When using Naksha storage within PostgresQL via [Plv8 Engine](https://plv8.github.io/), the same as in JVM can be done:
 * ```SQL
 * DO $$
 * var instance = PgInstanceConfig()
 *     .withHost("localhost")
 *     .withDb("testdb")
 *     .withUser("fred")
 *     .withPassword("secret")
 * var config = PgConfig()
 *     .withId("demo")
 *     .withMaster(instance)
 * var storage = PgStorage(config)
 * storage.newReadSession().use { session ->
 *   // Print storage-id to server output.
 *   plv8.elog(NOTICE, "Hello storage " + storage.id);
 *   // All code that requires a ISession, should use:
 *   plv8.elog(NOTICE, session.getMapById("foo").number);
 * }
 * $$ LANGUAGE plv8;
 * ```
 * There are some things to notice. Actually, within PLV8 each `plv8` session is always bound to a single connection/session. Therefore, opening a second connection will work, but shared the underlying same physical connection. When execute on a read-replica, the internal PLV8 code does not know that this instance is a read-replica.
 *
 * ### JavaScript / Node
 * TBD, technically every connection can be represented by a real PostgresQL connection, the same way Java does it. It is planned to be supported exactly the same way as on JVM.
 *
 * ### JavaScript / Browser
 * TBD, technically every connection should be represented using a single WebSocket. It is planned to be supported exactly the same way as on JVM.
 * @since 3.0
 */
expect open class PgStorage: AbstractStorage<PgConfig> {

    /**
     * Create a new instance and initialize it.
     *
     * This method actually is the same as:
     * ```kotlin
     * val i = new PgInstance()
     * i.initStorage(config)
     * ```
     * @param config the configuration.
     * @param create if not `null`, overrides [NakshaStorage.create].
     * @param upgrade if not `null`, overrides [NakshaStorage.upgrade].
     * @since 3.0
     */
    constructor(config: PgConfig, create: Boolean? = null, upgrade: Boolean? = null)

    /**
     * The [database][PgDatabase], set by [initStorage].
     * @since 3.0
     */
    override val database: PgDatabase

    override fun newWriteSession(options: SessionOptions?): IWriteSession
    override fun newReadSession(options: SessionOptions?): IWriteSession
    override fun newStreamSession(options: SessionOptions?): IStreamSession

    /**
     * Returns a new PostgresQL session.
     *
     * This method is invoked from [newReadSession] and [newWriteSession], just with adjusted [options].
     * @param options the session options.
     * @param readOnly if the session should be read-only.
     * @return the session.
     * @since 3.0
     */
    open fun newSession(options: SessionOptions, readOnly: Boolean): PgSession

    /**
     * Opens a new PostgresQL database connection.
     *
     * A connection received through this method will not really close when [PgConnection.close] is invoked, but the wrapper returns the underlying JDBC connection to the connection pool of the instance it received it from. If really necessary, [PgConnection.terminate] can be used for this case (for example to ensure advisory locks are released).
     *
     * If this is the [PLV8 engine](https://plv8.github.io/), then there is only one connection available, so calling this will just return a new dummy wrapper around the only connection that exists.
     *
     * The returned connection normally initializes the search-path, so that all naksha function are available, auto-commit is off, and the `naksha~admin` schema is at the top of the search-path (recommended setup).
     *
     * If an [init] function is provided, the normally used `SQL` code is given to this function, which can execute it, modify it before execution, skip the initialization _(like [SKIP_INIT])_, or do other initialization work.
     *
     * - Throws [TOO_MANY_CONNECTIONS][naksha.base.NakshaError.TOO_MANY_CONNECTIONS], if no more connections are available.
     * @param options the options for the connection.
     * @param readOnly if the connection should be read-only.
     * @param init an optional initialization function, if given, then it will be called with the string to be used to initialize the connection. It may just use this string, perform arbitrary additional work, or suppress initialization completely. If no initialization is wanted, use [SKIP_INIT].
     * @since 3.0
     * @see SKIP_INIT
     */
    open fun newConnection(options: SessionOptions, readOnly: Boolean, init: Fx2<PgConnection, String>? = null): PgConnection
    override val configKlass: KClass<PgConfig>
    override fun initStorage(config: PgConfig, create: Boolean?, upgrade: Boolean?)
    override fun afterInit()
    override fun shutdownStorage(dropCache: Boolean)

    companion object PgStorageCompanion {
        /**
         * A special function to be used with [initStorage] to suppress all initialization. This allows to use the connection as is.
         * @since 3.0
         */
        val SKIP_INIT: Fx2<PgConnection, String>
    }
}