package naksha.psql

import naksha.base.fn.Fx2
import naksha.model.*
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
     * The default options as provided in the constructor. The `schema` of the `defaultOptions` is the root schema, which will mapped to the [default map][NakshaContext.DEFAULT_MAP]. This schema is guaranteed to be there, when [initStorage] has been invoked, it is the main source of the storage-id, and it can be accessed via [defaultSchema].
     */
    val defaultOptions: PgOptions

    /**
     * The page-size of the database (`current_setting('block_size')`).
     */
    val pageSize: Int

    /**
     * The maximum size of a tuple (row).
     */
    val maxTupleSize: Int

    /**
     * The tablespace to use for storage-class "brittle"; if any.
     */
    val brittleTableSpace: String?

    /**
     * The tablespace to use for temporary tables and their indices; if any.
     */
    val tempTableSpace: String?

    /**
     * If the [pgsql-gzip][https://github.com/pramsey/pgsql-gzip] extension is installed, therefore PostgresQL supported `gzip`/`gunzip`
     * as standalone SQL function by the database. Note, that if this is not the case, we're installing code that is implemented in
     * JavaScript.
     */
    val gzipExtension: Boolean

    /**
     * The PostgresQL database version.
     */
    val postgresVersion: NakshaVersion

    /**
     * Returns the default (root) schema.
     * @return the default (root) schema.
     */
    fun defaultSchema(): PgSchema

    /**
     * Tests if this storage contains the given schema.
     * @param schemaName the name of the schema to test.
     * @return _true_ if such a schema exists; _false_ otherwise.
     */
    operator fun contains(schemaName: String): Boolean

    /**
     * Returns the schema wrapper.
     * @param schemaName the name of the schema.
     * @return the schema wrapper.
     */
    operator fun get(schemaName: String): PgSchema

    /**
     * Initializes the storage, create the transaction table, install needed scripts and extensions. If the storage is
     * already initialized; does nothing.
     *
     * Well known parameters for this storage:
     * - [PgPlatform.ID]: if the storage is uninitialized, initialize it with the given storage identifier. If the storage is already
     * initialized, reads the existing identifier and compares it with the given one. If they do not match, throws an
     * [IllegalStateException]. If not given a random new identifier is generated, when no identifier yet exists. It is strongly
     * recommended to provide the identifier.
     * - [PgPlatform.CONTEXT]: can be a [NakshaContext] to be used while doing the initialization; only if [superuser][NakshaContext.su] is _true_,
     * then a not uninitialized storage is installed. This requires as well superuser rights in the PostgresQL database.
     * - [PgPlatform.OPTIONS]: can be a [PgOptions] object to be used for the initialization connection (specific changed defaults to
     * timeouts and locks).
     *
     * @param params optional special parameters that are storage dependent to influence how a storage is initialized.
     * @throws NakshaException if the initialization failed.
     * @since 2.0.30
     */
    override fun initStorage(params: Map<String, *>?)

    override fun newWriteSession(context: NakshaContext, options: NakshaSessionOptions?): IWriteSession =
        newSession(
            defaultOptions.copy(
                readOnly = false,
                useMaster = true,
                parallel = options?.parallel ?: false,
                appId = context.appId,
                author = context.author
            )
        )

    override fun newReadSession(context: NakshaContext, options: NakshaSessionOptions?): IWriteSession =
        newSession(
            defaultOptions.copy(
                readOnly = true,
                useMaster = options?.useMaster ?: false,
                parallel = options?.parallel ?: false,
                appId = context.appId,
                author = context.author
            )
        )

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
}