package naksha.psql

import naksha.base.*
import naksha.base.Platform.PlatformCompanion.logger
import naksha.base.fn.Fx2
import naksha.jbon.IDictManager
import naksha.jbon.JbDictManager
import naksha.jbon.JbFeatureDecoder
import naksha.jbon.JbMapDecoder
import naksha.model.*
import naksha.model.NakshaContext.NakshaContextCompanion.DEFAULT_MAP_ID
import naksha.model.NakshaError.NakshaErrorCompanion.UNINITIALIZED
import naksha.model.NakshaVersion.Companion.LATEST
import naksha.model.objects.NakshaFeature
import naksha.model.request.ResultTuple
import naksha.psql.PgUtil.PgUtilCompanion.VERSION
import naksha.psql.PgUtil.PgUtilCompanion.CONTEXT
import naksha.psql.PgUtil.PgUtilCompanion.ID
import naksha.psql.PgUtil.PgUtilCompanion.OPTIONS
import naksha.psql.PgUtil.PgUtilCompanion.OVERRIDE
import naksha.psql.PgUtil.PgUtilCompanion.quoteIdent
import kotlin.js.JsExport
import kotlin.jvm.JvmField

/**
 * The PostgresQL storage that manages session and connections.
 *
 * This class is a default multi-platform implements of the [IStorage] interface, with PostgresQL specific extensions, properties and methods.
 *
 * In Java multiple instances can be created. Within the PostgresQL database (so running in PLV8 extension), a new storage instance is created as singleton and added into to the global `plv8` object, when the `naksha_start_session` SQL function is executed, which is necessary for all other Naksha SQL functions to work. This singleton will hold only a single [PgSession], trying to acquire a second one, will always error with [NakshaError.ILLEGAL_STATE].
 */
@Suppress("OPT_IN_USAGE")
@JsExport
open class PgStorage(
    /**
     * The PostgresQL cluster to which this storage is connected.
     *
     * Will be _null_, if being executed within [PLV8 extension](https://plv8.github.io/).
     */
    open val cluster: PgCluster,

    /**
     * The name of the default schema, being assigned to the default map.
     */
    @JvmField
    val defaultSchemaName: String
) : IStorage {
    private var _adminOptions: SessionOptions? = null
    override var adminOptions: SessionOptions
        get() = _adminOptions ?: SessionOptions(
            mapId = mapIdToSchema(defaultSchemaName),
            appName = "lib-psql/$LATEST",
            appId = NakshaContext.defaultAppId.get() ?: "lib-psql",
            author = null,
            parallel = false,
            useMaster = true,
            excludePaths = NakshaContext.defaultExcludePaths.get(),
            excludeFn = NakshaContext.defaultExcludeFn.get(),
            connectTimeout = NakshaContext.defaultConnectTimeout.get(),
            socketTimeout = NakshaContext.defaultSocketTimeout.get(),
            stmtTimeout = NakshaContext.defaultStmtTimeout.get(),
            lockTimeout = NakshaContext.defaultLockTimeout.get()
        )
        set(value) {
            _adminOptions = value.copy(mapId = mapIdToSchema(defaultSchemaName))
        }

    /**
     * The hard-cap (limit) of the storage. No result-set every should become bigger than this amount of features.
     */
    var hardCap: Int = 1_000_000

    protected var _pageSize: Int? = null

    /**
     * The page-size of the database (`current_setting('block_size')`).
     */
    val pageSize: Int
        get() = _pageSize ?: throw NakshaException(UNINITIALIZED, "Storage uninitialized")

    private var _maxTupleSize: Int? = null

    /**
     * The maximum size of a tuple (row).
     */
    val maxTupleSize: Int
        get() = _maxTupleSize ?: throw NakshaException(UNINITIALIZED, "Storage uninitialized")

    private var _brittleTableSpace: String? = null

    /**
     * The tablespace to use for storage-class "brittle"; if any.
     */
    val brittleTableSpace: String?
        get() {
            if (!isInitialized()) throw NakshaException(UNINITIALIZED, "Storage uninitialized")
            return _brittleTableSpace
        }

    private var _tempTableSpace: String? = null

    /**
     * The tablespace to use for temporary tables and their indices; if any.
     */
    val tempTableSpace: String?
        get() {
            if (!isInitialized()) throw NakshaException(UNINITIALIZED, "Storage uninitialized")
            return _tempTableSpace
        }

    private var _gzipExtension: Boolean? = null

    /**
     * If the [pgsql-gzip][https://github.com/pramsey/pgsql-gzip] extension is installed, therefore PostgresQL supported `gzip`/`gunzip` as standalone SQL function by the database. Note, that if this is not the case, we're installing code that is implemented in JavaScript.
     */
    val gzipExtension: Boolean
        get() = _gzipExtension ?: throw NakshaException(UNINITIALIZED, "Storage uninitialized")

    private var _postgresVersion: NakshaVersion? = null

    /**
     * The PostgresQL database version.
     */
    val postgresVersion: NakshaVersion
        get() = _postgresVersion ?: throw NakshaException(UNINITIALIZED, "Storage uninitialized")

    private var _id: AtomicRef<String> = AtomicRef(null)
    private var _txnSequenceOid: Int? = null

    override fun id(): String = _id.get() ?: throw NakshaException(UNINITIALIZED, "Storage uninitialized")

    override fun isInitialized(): Boolean = _id.get() != null

    private val maps: AtomicMap<String, WeakRef<out PgMap>> = Platform.newAtomicMap()
    private val mapNumberToId: AtomicMap<Int, String> = Platform.newAtomicMap()
    init {
        mapNumberToId[0] = ""
    }

    /**
     * A lock for the storage to synchronize access to some properties and to prevent, that multiple threads in parallel initialize the storage.
     */
    protected val lock = Platform.newLock()

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
    override fun initStorage(params: Map<String, *>?) {
        if (this._id.get() != null) return
        lock.acquire().use {
            if (this._id.get() != null) return
            val context: NakshaContext = if (params != null && params.containsKey(CONTEXT)) {
                val v = params[CONTEXT]
                require(v is NakshaContext) { "params.$CONTEXT must be an instance of NakshaContext" }
                v
            } else NakshaContext.currentContext()

            val options: SessionOptions = if (params != null && params.containsKey(OPTIONS)) {
                val v = params[OPTIONS]
                require(v is SessionOptions) { "params.$OPTIONS must be an instance of SessionOptions" }
                v
            } else SessionOptions.from(context)

            val initId: String? = if (params != null && params.containsKey(ID)) {
                val _id = params[ID]
                require(_id is String && _id.length > 0) { "params.$ID must be a string with a minimal length of 1" }
                _id
            } else null

            var override = false
            if (params != null && params.containsKey(OVERRIDE)) {
                val v = params[OVERRIDE]
                require(v is Boolean) { "params.$OVERRIDE must be a boolean, if given" }
                override = v
            }

            var version = NakshaVersion.latest
            if (params != null && params.contains(VERSION)) {
                val v = params[VERSION]
                version = when (v) {
                    is String -> NakshaVersion.of(v)
                    is Number -> NakshaVersion(v.toLong())
                    is Int64 -> NakshaVersion(v)
                    is NakshaVersion -> v
                    else -> throw IllegalArgumentException("params.${VERSION} must be a valid Naksha version string or binary encoding")
                }
            }

            val conn = cluster.newConnection(options, false)
            conn.use {
                logger.info("Start init of database {}", conn.toUri())
                conn.autoCommit = false

                logger.info("Query basic database information")
                var cursor = conn.execute(
                    """
SELECT 
    current_setting('block_size')::int4 as bs, 
    (select oid FROM pg_catalog.pg_tablespace WHERE spcname = '$TEMPORARY_TABLESPACE') as temp_oid,
    (select oid FROM pg_catalog.pg_extension WHERE extname = 'gzip') as gzip_oid,
    version() as version
"""
                ).fetch()
                cursor.use {
                    _pageSize = cursor["bs"]
                    val tupleSize = pageSize - 32
                    _maxTupleSize = if (tupleSize > MAX_POSTGRES_TOAST_TUPLE_TARGET) {
                        MAX_POSTGRES_TOAST_TUPLE_TARGET
                    } else if (tupleSize < MIN_POSTGRES_TOAST_TUPLE_TARGET) {
                        MIN_POSTGRES_TOAST_TUPLE_TARGET
                    } else {
                        tupleSize
                    }
                    // Note: Temporary and Brittle tables are both created in the temp-tablespace!
                    _brittleTableSpace = if (cursor.column("temp_oid") is Int) TEMPORARY_TABLESPACE else null
                    _tempTableSpace = _brittleTableSpace
                    _gzipExtension = cursor.column("gzip_oid") is Int
                    // "PostgreSQL 15.5 on aarch64-unknown-linux-gnu, compiled by gcc (GCC) 7.3.1 20180712 (Red Hat 7.3.1-6), 64-bit"
                    val v: String = cursor["version"]
                    val start = v.indexOf(' ')
                    val end = v.indexOf(' ', start + 1)
                    _postgresVersion = NakshaVersion.of(v.substring(start + 1, end))
                }
                logger.info("Invoke init_internal for default schema '$defaultSchemaName'")
                val schema = defaultMap()
                val storage_id = schema.init_internal(initId, conn, version, override)
                _id.set(storage_id)

                logger.info("Commit")
                conn.commit()

                logger.info("Load OID of transaction sequence counter (located only in default schema)")
                cursor = conn.execute("SELECT oid FROM pg_class WHERE relname='$NAKSHA_TXN_SEQ' AND relnamespace=${schema.oid}").fetch()
                cursor.use {
                    _txnSequenceOid = cursor["oid"]
                }
            }
        }
    }

    /**
     * Translate the map-id into a schema name.
     * @param mapId the map-id.
     * @return the schema name.
     */
    fun mapIdToSchema(mapId: String): String = if (mapId.isEmpty()) defaultSchemaName else mapId

    /**
     * Translate the schema name into a map-id.
     * @param schema the schema name.
     * @return the map-id.
     */
    fun schemaToMapId(schema: String): String = if (schema == defaultSchemaName) defaultSchemaName else schema

    /**
     * Returns the default map.
     * @return the default map.
     */
    override fun defaultMap(): PgMap = this[DEFAULT_MAP_ID]

    /**
     * The default flags to use for the storage.
     * @return default flags to use for the storage.
     */
    fun defaultFlags(): Flags = Flags()
        .featureEncoding(FeatureEncoding.JBON_GZIP)
        .geoEncoding(GeoEncoding.TWKB_GZIP)
        .tagsEncoding(TagsEncoding.JBON_GZIP)

    /**
     * Returns the OID of the transaction sequence.
     */
    fun txnSequenceOid(): Int = _txnSequenceOid ?: throw NakshaException(UNINITIALIZED, "Storage uninitialized")

    /**
     * Tests if this storage contains the given schema.
     * @param schemaName the name of the schema to test.
     * @return _true_ if such a schema exists; _false_ otherwise.
     */
    operator fun contains(schemaName: String): Boolean = maps.containsKey(schemaName)

    /**
     * Creates a new schema instance, internally called.
     */
    protected open fun newMap(storage: PgStorage, mapId: String): PgMap = PgMap(storage, mapId, mapIdToSchema(mapId))

    /**
     * Returns the map wrapper.
     * @param mapId the map-id.
     * @return the map wrapper.
     */
    override operator fun get(mapId: String): PgMap {
        val maps = this.maps
        while (true) {
            var schemaRef = maps[mapId]
            var schema = schemaRef?.deref()
            if (schema != null) return schema
            if (schemaRef != null) {
                if (!maps.remove(mapId, schemaRef)) continue
                // Schema removed successfully, no conflict with other thread.
            }
            schema = newMap(this, mapId)
            schemaRef = Platform.newWeakRef(schema)
            maps.putIfAbsent(mapId, schemaRef) ?: return schema
            // Conflict, another thread was faster, retry.
        }
    }

    /**
     * Translate the schema name into a map name.
     * @param mapNumber the `map-id` as encoded in [StoreNumber].
     * @return the map name.
     */
    override fun getMapId(mapNumber: Int): String? = mapNumberToId[mapNumber]

    override fun rowToFeature(tuple: Tuple): NakshaFeature {
        return if (tuple.feature != null) {
            // TODO: FIXME, we need the XYZ namespace
            val featureReader = JbFeatureDecoder(JbDictManager()).mapBytes(tuple.feature!!).reader
            val feature = JbMapDecoder().mapReader(featureReader).toAnyObject().proxy(NakshaFeature::class)
            feature
        } else {
            TODO("We will always have at least the id, which is formally enough to generate an empty feature!")
        }
    }

    override fun featureToRow(feature: NakshaFeature): Tuple {
        val nakshaFeature = feature.proxy(NakshaFeature::class)
        TODO("Implement me")
    }

    // TODO: Fix me, the dictionary manager need to read/write form naksha~dictionaries !!!
    private val dictManager = JbDictManager()

    override fun dictManager(map: String): IDictManager = dictManager

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
    open fun newSession(options: SessionOptions, readOnly: Boolean): PgSession = PgSession(this, options, readOnly)

    /**
     * Opens a new PostgresQL database connection.
     *
     * A connection received through this method will not really close when [PgConnection.close] is invoked, but the wrapper returns the underlying JDBC connection to the connection pool of the instance. If really necessary, [PgConnection.terminate] can be used for this case (for example to ensure advisory locks are released).
     *
     * If this is the [PLV8 engine](https://plv8.github.io/), then there is only one connection available, so calling this before closing
     * the previous returned connection will always cause an [NakshaError.TOO_MANY_CONNECTIONS].
     *
     * - Throws [naksha.model.NakshaError.TOO_MANY_CONNECTIONS], if no more connections are available.
     * @param options the options for the connection.
     * @param readOnly if the connection should be read-only.
     * @param init an optional initialization function, if given, then it will be called with the string to be used to initialize the connection. It may just do the work or perform arbitrary additional work or supress initialization.
     */
    open fun newConnection(options: SessionOptions, readOnly: Boolean, init: Fx2<PgConnection, String>? = null): PgConnection {
        val conn = cluster.newConnection(options, readOnly)
        // TODO: Do we need more initialization work here?
        val query = "SET SESSION search_path TO ${quoteIdent(mapIdToSchema(options.mapId))}, public, topology;\n"
        if (init != null) init.call(conn, query) else conn.execute(query).close()
        return conn
    }

    /**
     * Opens an admin connection.
     *
     * This is the same as [newConnection], except that it can be implemented differently, for example on the [PLV8 engine](https://plv8.github.io/). Basically, this method acquires a special connection that is only used for a short moment of time to do some administrative work.
     *
     * **WARNING**: This method is only for internal purpose, to avoid breaking the code on `PLV8`.
     *
     * @param options the options for the connection.
     * @param init an optional initialization function, if given, then it will be called with the string to be used to initialize the connection. It may just do the work or perform arbitrary additional work or supress initialization.
     * @return the admin connection, to be closed after usage (uses [adminOptions], and is always bound to master).
     */
    internal open fun adminConnection(options: SessionOptions = adminOptions, init: Fx2<PgConnection, String>? = null): PgConnection
        = newConnection(options, false, init)

    override fun initMap(mapId: String) {
        val schema = mapIdToSchema(mapId)
        this[schema].init()
    }

    override fun dropMap(mapId: String) {
        val schema = mapIdToSchema(mapId)
        if (schema in this) this[schema].drop()
    }

    override fun validateHandle(handle: String, ttl: Int?): Boolean {
        TODO("Not yet implemented")
    }

    override fun getRowsByFeatureId(mapId: String, collectionId: String, featureIds: Array<String>, mode: String): List<Tuple?> {
        TODO("Not yet implemented")
    }

    override fun getRows(tupleNumbers: Array<TupleNumber>, mode: String): List<Tuple?> {
        TODO("Not yet implemented")
    }

    override fun fetchRows(rows: List<ResultTuple?>, from: Int, to: Int, mode: String) {
        TODO("Not yet implemented")
    }

    override fun fetchRow(row: ResultTuple, mode: String) {
        TODO("Not yet implemented")
    }

    override fun close() {
    }
}