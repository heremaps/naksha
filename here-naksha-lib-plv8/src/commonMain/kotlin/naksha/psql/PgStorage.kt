package naksha.psql

import naksha.model.IStorage
import naksha.model.IWriteSession
import naksha.model.NakshaContext
import naksha.model.StorageException
import kotlin.js.JsExport

/**
 * The PostgresQL storage that manages session (aka connections). This is basically the [IStorage], but extended with some special methods
 * to acquire real PostgresQL database connections.
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
        newNakshaSession(context, defaultOptions.copy(readOnly = false, useMaster = true))

    override fun newReadSession(context: NakshaContext, useMaster: Boolean): IWriteSession =
        newNakshaSession(context, defaultOptions.copy(readOnly = true, useMaster = useMaster))

    /**
     * Returns a new Naksha session.
     * @param context the context to use for this session.
     * @param options the options to use for the database connection used by this Naksha session.
     * @return the Naksha session.
     */
    fun newNakshaSession(context: NakshaContext, options: PgOptions): NakshaSession

    /**
     * Opens a new PostgresQL database session (a PostgresQL database connection). A session received through this method will not
     * really close, then [PgConnection.close] is invoked, but return to the connection pool.
     *
     * If this is the [PLV8 engine](https://plv8.github.io/), then there is only one connection available, so calling this before closing
     * the previously returned connection will always cause an [IllegalStateException].
     * @param context the context for which to initialize the session.
     * @param options the options for the session; defaults to [defaultOptions].
     * @throws IllegalStateException if all connections are in use.
     */
    fun newConnection(context: NakshaContext, options: PgOptions = defaultOptions): PgConnection

    /**
     * Returns the database information.
     * @return the database information.
     */
    fun getPgDbInfo(): PgInfo
}