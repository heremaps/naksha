package naksha.psql

import naksha.base.*
import naksha.base.Platform.PlatformCompanion.logger
import naksha.base.fn.Fx2
import naksha.jbon.*
import naksha.model.*
import naksha.model.NakshaErrorCode.StorageErrorCompanion.STORAGE_ID_MISMATCH
import naksha.model.Row
import naksha.psql.PgUtil.PgUtilCompanion.ID
import naksha.psql.PgUtil.PgUtilCompanion.OPTIONS
import naksha.psql.PgUtil.PgUtilCompanion.VERSION
import naksha.psql.PgStatic.quote_ident
import naksha.psql.PgUtil.PgUtilCompanion.OVERRIDE
import java.util.concurrent.atomic.AtomicReference

/**
 * The Java implementation of the [IStorage] interface. This storage class is extended in `here-naksha-storage-psql`, which has a
 * `PsqlStoragePlugin` class, which implements the `Plugin` contract (internal contract in _Naksha-Hub_) and makes the storage available
 * to **Naksha-Hub** as storage plugin. It parses the configuration from feature properties given, and calls this constructor.
 * @constructor Creates a new PSQL storage.
 * @property id the identifier of the storage.
 * @property cluster the PostgresQL instances used by this storage.
 * @property defaultOptions the default connection options, ignores the [PgOptions.readOnly] and [PgOptions.useMaster].
 */
open class PsqlStorage(override val cluster: PsqlCluster, override val defaultOptions: PgOptions) : PgStorage, IStorage {
    private var _pageSize: Int? = null
    override val pageSize: Int
        get() {
            val _pageSize = this._pageSize
            check(_pageSize != null) { "initStorage must be invoked before the 'pageSize' can be queried!" }
            return _pageSize
        }

    private var _maxTupleSize: Int? = null
    override val maxTupleSize: Int
        get() {
            val _maxTupleSize = this._maxTupleSize
            check(_maxTupleSize != null) { "initStorage must be invoked before the 'maxTupleSize' can be queried!" }
            return _maxTupleSize
        }

    private var _brittleTableSpace: String? = null
    override val brittleTableSpace: String?
        get() {
            check(id.get() != null) { "initStorage must be invoked before the 'brittleTableSpace' can be queried!" }
            return _brittleTableSpace
        }

    private var _tempTableSpace: String? = null
    override val tempTableSpace: String?
        get() {
            check(id.get() != null) { "initStorage must be invoked before the 'tempTableSpace' can be queried!" }
            return _tempTableSpace
        }

    private var _gzipExtension: Boolean? = null
    override val gzipExtension: Boolean
        get() {
            val _gzipExtension = this._gzipExtension
            check(_gzipExtension != null) { "initStorage must be invoked before the 'gzipExtension' can be queried!" }
            return _gzipExtension
        }

    private var _postgresVersion: NakshaVersion? = null
    override val postgresVersion: NakshaVersion
        get() {
            val _postgresVersion = this._postgresVersion
            check(_postgresVersion != null) { "initStorage must be invoked before the 'postgresVersion' can be queried!" }
            return _postgresVersion
        }

    private var id: AtomicReference<String?> = AtomicReference(null)

    /**
     * The storage-id.
     * @throws IllegalStateException if [initStorage] has not been called before.
     */
    override fun id(): String {
        val id = this.id.get()
        check(id != null) { "PsqlStorage not initialized" }
        return id
    }

    /**
     * Connects to the configured PostgresQL database and verifies if the database is initialized.
     *
     * If the database is not initialized, it checks if the [current NakshaContext][NakshaContext.currentContext] is a supervisor
     * context, so the [NakshaContext.su] is _true_. If not, it will throw an [IllegalStateException].
     *
     * Eventually, when the database is not initialized and the current actor is a supervisor, it tries to install the
     * [PLV8 extension](https://plv8.github.io/), if being available. If that succeeds, it will install the `commonjs2`, `lz4`, `jbon`,
     * and `naksha` JavaScript modules.
     *
     * Generally, it will create the schema and all needed admin-tables (for transactions, global dictionary aso.). The server side
     * (database) code is only supported, if the database supports the [PLV8 extension](https://plv8.github.io/).
     *
     * @throws IllegalStateException if the data is not yet initialized, and the [current NakshaContext][NakshaContext.currentContext] is
     * not a supervisor context, so the [NakshaContext.su] is _false_.
     */
    override fun initStorage(params: Map<String, *>?) {
        if (this.id.get() != null) return
        synchronized(this) {
            if (this.id.get() != null) return
            var options: PgOptions? = null
            if (params != null && params.containsKey(OPTIONS)) {
                val v = params[OPTIONS]
                require(v is PgOptions) { "params.${OPTIONS} must be an instance of PgOptions" }
                options = v
            }
            options = (options?: defaultOptions).copy(schema = defaultOptions.schema, readOnly = false, useMaster = true)
            val initId: String? = if (params != null && params.containsKey(ID)) {
                val _id = params[ID]
                require(_id is String && _id.length > 0) { "params.${ID} must be a string with a minimal length of 1" }
                _id
            } else null
            var override = false
            if (params != null && params.containsKey(OVERRIDE)) {
                val v = params[OVERRIDE]
                require(v is Boolean) { "params.${OVERRIDE} must be a boolean, if given" }
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
            val conn = cluster.newConnection(options)
            conn.use {
                logger.info("Start init of database {}", conn.jdbc.url)
                conn.jdbc.autoCommit = false

                logger.info("Query basic database information")
                val cursor = conn.execute(
                    """
SELECT 
    current_setting('block_size')::int4 as bs, 
    (select oid FROM pg_catalog.pg_tablespace WHERE spcname = '$TEMPORARY_TABLESPACE') as temp_oid,
    (select oid FROM pg_catalog.pg_extension WHERE extname = 'gzip') as gzip_oid,
    version() as version
""").fetch()
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
                id.set(defaultSchema().initInternally(initId, conn, version, override))
            }
        }
    }

    override fun rowToFeature(row: Row): NakshaFeatureProxy {
        return if (row.feature != null) {
            // TODO: FIXME, we need the XYZ namespace
            val featureReader = JbMapFeatureDecoder(JbDictManager()).mapBytes(row.feature!!).reader
            val feature = JbMapDecoder().mapReader(featureReader).toIMap().proxy(NakshaFeatureProxy::class)
            feature
        } else {
            TODO("We will always have at least the id, which is formally enough to generate an empty feature!")
        }
    }

    override fun featureToRow(feature: PlatformMap): Row {
        val nakshaFeature = feature.proxy(NakshaFeatureProxy::class)
        return Row(
            storage = this,
            flags = Flags(),
            id = nakshaFeature.id,
            feature = XyzEncoder().buildFeatureFromMap(nakshaFeature), // FIXME split feature to geo etc
            geoRef = null,
            geo = null,
            tags = null
        )
    }

    override fun enterLock(id: String, waitMillis: Int64): ILock {
        TODO("Not yet implemented")
    }

    /**
     * Returns a new PostgresQL session.
     * @param options the options to use for the database connection used by this session.
     * @return the session.
     */
    override fun newSession(options: PgOptions): PgSession = PgSession(this, options)

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
    override fun newConnection(options: PgOptions, init: Fx2<PgConnection, String>?): PgConnection {
        val conn = cluster.newConnection(options)
        val quotedSchema = if (options.schema == defaultOptions.schema) this.quotedSchema else quote_ident(options.schema)
        // TODO: Do we need more initialization work here?
        val query = "SET SESSION search_path TO $quotedSchema, public, topology;\n"
        if (init != null) init.call(conn, query) else conn.execute(query).close()
        return conn
    }


    private var _quotedSchema: String? = null
    val quotedSchema: String
        get() {
            var s = _quotedSchema
            if (s == null) {
                s = quote_ident(defaultOptions.schema)
                _quotedSchema = s
            }
            return s
        }

    override fun close() {
        TODO("Not yet implemented")
    }

    override fun dictManager(nakshaContext: NakshaContext): IDictManager {
        TODO("Not yet implemented")
    }

    /**
     * All cached schemata.
     */
    private val schemata: CMap<String, WeakRef<PsqlSchema>> = Platform.newCMap()

    override fun initRealm(realm: String) {
        this[realmToSchema(realm)].init()
    }

    override fun dropRealm(realm: String) {
        val schemaName = realmToSchema(realm)
        if (schemaName in this) this[schemaName].drop()
    }

    override fun defaultSchema(): PsqlSchema = this[defaultOptions.schema]

    override operator fun contains(schemaName: String): Boolean = schemata.containsKey(schemaName)

    /**
     * Returns a schema wrapper.
     * @param schemaName the schema name.
     * @return the schema wrapper.
     */
    override operator fun get(schemaName: String): PsqlSchema {
        val schemata = this.schemata
        while (true) {
            var schemaRef = schemata[schemaName]
            var schema = schemaRef?.deref()
            if (schema != null) return schema
            if (schemaRef != null) {
                if (!schemata.remove(schemaName, schemaRef)) continue
                // Schema removed successfully, no conflict with other thread.
            }
            schema = PsqlSchema(this, schemaName)
            schemaRef = Platform.newWeakRef(schema)
            schemata.putIfAbsent(schemaName, schemaRef) ?: return schema
            // Conflict, another thread was faster, retry.
        }
    }
}
