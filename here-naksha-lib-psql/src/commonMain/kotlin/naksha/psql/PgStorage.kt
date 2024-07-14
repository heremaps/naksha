package naksha.psql

import naksha.base.fn.Fx2
import naksha.model.IStorage
import naksha.model.IWriteSession
import naksha.model.NakshaContext
import naksha.model.StorageException
import kotlin.js.JsExport

/**
 * The PostgresQL storage that manages session and connections. This is basically the [IStorage], but extended with some special methods
 * to acquire real PostgresQL database connections.
 *
 * In Java multiple instances can be created. Within the database, a new storage instance is created as singleton and added into to global
 * `plv8` object, when the `naksha_start_session` SQL function is executed, which is necessary for all other Naksha SQL functions to work.
 * This singleton will hold only a single [PgSession], trying to acquire a second one, will always throw an [IllegalStateException].
 */
@Suppress("OPT_IN_USAGE")
@JsExport
interface PgStorage : IStorage {

    /**
     * The PostgresQL cluster to which this storage is connected. Will be _null_, if being executed within
     * [PLV8 extension](https://plv8.github.io/).
     */
    val cluster: PgCluster?

    /**
     * The default options as provided in the constructor.
     */
    val defaultOptions: PgOptions

    /**
     * Initializes the storage, create the transaction table, install needed scripts and extensions. If the storage is
     * already initialized; does nothing.
     *
     * Well known parameters for this storage:
     * - [PgUtil.ID]: if the storage is uninitialized, initialize it with the given storage identifier. If the storage is already
     * initialized, reads the existing identifier and compares it with the given one. If they do not match, throws an
     * [IllegalStateException]. If not given a random new identifier is generated, when no identifier yet exists. It is strongly
     * recommended to provide the identifier.
     * - [PgUtil.CONTEXT]: can be a [NakshaContext] to be used while doing the initialization; only if [superuser][NakshaContext.su] is _true_,
     * then a not uninitialized storage is installed. This requires as well superuser rights in the PostgresQL database.
     * - [PgUtil.OPTIONS]: can be a [PgOptions] object to be used for the initialization connection (specific changed defaults to
     * timeouts and locks).
     *
     * @param params optional special parameters that are storage dependent to influence how a storage is initialized.
     * @throws StorageException if the initialization failed.
     * @since 2.0.30
     */
    override fun initStorage(params: Map<String, *>?)

    override fun newWriteSession(context: NakshaContext): IWriteSession =
        newSession(defaultOptions.copy(readOnly = false, useMaster = true, appId=context.appId, author = context.author))

    override fun newReadSession(context: NakshaContext, useMaster: Boolean): IWriteSession =
        newSession(defaultOptions.copy(readOnly = true, useMaster = useMaster, appId=context.appId, author = context.author))

    /**
     * Returns a new PostgresQL session.
     * @param options the options to use for the database connection used by this session.
     * @return the session.
     */
    fun newSession(options: PgOptions): PgSession

    /**
     * Opens a new PostgresQL database connection. A connection received through this method will not really close when
     * [PgConnection.close] is invoked, but the wrapper returns the underlying JDBC connection to the connection pool of the instance.
     *
     * If this is the [PLV8 engine](https://plv8.github.io/), then there is only one connection available, so calling this before closing
     * the previous returned connection will always cause an [IllegalStateException].
     * @param options the options for the session; defaults to [defaultOptions].
     * @param init an optional initialization function, if given, then it will be called with the string to be used to initialize the
     * connection. It may just do the work or perform arbitrary additional work.
     * @throws IllegalStateException if all connections are in use.
     */
    fun newConnection(options: PgOptions = defaultOptions, init: Fx2<PgConnection, String>? = null): PgConnection

    /**
     * Returns the database information.
     * @return the database information, cached when [conn] is _null_.
     */
    fun getPgDbInfo(): PgInfo
}