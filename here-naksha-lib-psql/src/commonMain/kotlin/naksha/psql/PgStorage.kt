package naksha.psql

import naksha.base.fn.Fx2
import naksha.model.*
import kotlin.js.JsExport

/**
 * The PostgresQL storage that manages session and connections.
 *
 * This interfaces extends the [IStorage] interface with some PostgresQL specific properties and methods.
 *
 * In Java multiple instances can be created. Within the PostgresQL database (so running in PLV8 extension), a new storage instance is created as singleton and added into to the global `plv8` object, when the `naksha_start_session` SQL function is executed, which is necessary for all other Naksha SQL functions to work. This singleton will hold only a single [PgSession], trying to acquire a second one, will always throw an [IllegalStateException].
 */
@Suppress("OPT_IN_USAGE")
@JsExport
interface PgStorage : IStorage {
    /**
     * The hard-cap (limit) of the storage. No result-set every should become bigger than this amount of features.
     */
    var hardCap: Int

    /**
     * The PostgresQL cluster to which this storage is connected. Will be _null_, if being executed within [PLV8 extension](https://plv8.github.io/).
     */
    val cluster: PgCluster?

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
     * If the [pgsql-gzip][https://github.com/pramsey/pgsql-gzip] extension is installed, therefore PostgresQL supported `gzip`/`gunzip` as standalone SQL function by the database. Note, that if this is not the case, we're installing code that is implemented in JavaScript.
     */
    val gzipExtension: Boolean

    /**
     * The PostgresQL database version.
     */
    val postgresVersion: NakshaVersion

    /**
     * The default schema, used for the default map.
     */
    val defaultSchemaName: String

    /**
     * Translate the map name into a schema name.
     * @param map the map name.
     * @return the schema name.
     */
    fun mapToSchema(map: String): String = if (map.isEmpty()) defaultSchemaName else map

    /**
     * Translate the schema name into a map name.
     * @param schema the schema name.
     * @return the map name.
     */
    fun schemaToMap(schema: String): String = if (schema == defaultSchemaName) defaultSchemaName else schema

    /**
     * Returns the default schema that maps to the default map (empty string), which matches [defaultSchemaName].
     * @return the default schema.
     */
    fun defaultSchema(): PgSchema

    /**
     * The default flags to use for the storage.
     * @return default flags to use for the storage.
     */
    fun defaultFlags(): Flags

    /**
     * Returns the OID of the transaction sequence.
     */
    fun txnSequenceOid(): Int

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
     * - [PgUtil.ID]: if the storage is uninitialized, initialize it with the given storage identifier. If the storage is already
     * initialized, reads the existing identifier and compares it with the given one. If they do not match, throws an
     * [IllegalStateException]. If not given a random new identifier is generated, when no identifier yet exists. It is strongly
     * recommended to provide the identifier.
     * - [PgUtil.CONTEXT]: can be a [NakshaContext] to be used while doing the initialization; only if [superuser][NakshaContext.su] is _true_,
     * then a not uninitialized storage is installed. This requires as well superuser rights in the PostgresQL database.
     * - [PgUtil.OPTIONS]: can be a [SessionOptions] object to be used for the initialization connection (specific changed defaults to
     * timeouts and locks).
     *
     * @param params optional special parameters that are storage dependent to influence how a storage is initialized.
     * @throws NakshaException if the initialization failed.
     * @since 2.0.30
     */
    override fun initStorage(params: Map<String, *>?)

    override fun newWriteSession(options: SessionOptions?): IWriteSession
        = newSession(options ?: SessionOptions.from(null), false)

    override fun newReadSession(options: SessionOptions?): IWriteSession
        = newSession(options ?: SessionOptions.from(null), true)

    /**
     * Returns a new PostgresQL session.
     *
     * This method is invoked from [newReadSession] and [newWriteSession], just with adjusted [options].
     * @param options the session options.
     * @param readOnly if the session should be read-only.
     * @return the session.
     */
    fun newSession(options: SessionOptions, readOnly:Boolean): PgSession

    /**
     * Opens a new PostgresQL database connection. A connection received through this method will not really close when
     * [PgConnection.close] is invoked, but the wrapper returns the underlying JDBC connection to the connection pool of the instance.
     *
     * If this is the [PLV8 engine](https://plv8.github.io/), then there is only one connection available, so calling this before closing
     * the previous returned connection will always cause an [IllegalStateException].
     * @param options the options for the session.
     * @param readOnly if the connection should be read-only.
     * @param init an optional initialization function, if given, then it will be called with the string to be used to initialize the
     * connection. It may just do the work or perform arbitrary additional work.
     * @throws IllegalStateException if all connections are in use.
     */
    fun newConnection(options: SessionOptions = SessionOptions.from(null), readOnly: Boolean = false, init: Fx2<PgConnection, String>? = null): PgConnection
}