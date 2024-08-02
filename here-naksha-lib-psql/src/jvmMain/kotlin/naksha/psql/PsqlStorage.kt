package naksha.psql

import naksha.base.*
import naksha.base.Platform.PlatformCompanion.logger
import naksha.base.fn.Fx2
import naksha.jbon.*
import naksha.model.*
import naksha.model.NakshaError.NakshaErrorCompanion.UNINITIALIZED
import naksha.model.NakshaVersion.Companion.LATEST
import naksha.model.Row
import naksha.model.objects.NakshaFeature
import naksha.model.request.ResultRow
import naksha.psql.PgUtil.PgUtilCompanion.quoteIdent
import naksha.psql.PgUtil.PgUtilCompanion.ID
import naksha.psql.PgUtil.PgUtilCompanion.OPTIONS
import naksha.psql.PgUtil.PgUtilCompanion.OVERRIDE
import naksha.psql.PgPlatform.PgPlatformCompanion.VERSION
import naksha.psql.PgUtil.PgUtilCompanion.CONTEXT
import java.util.concurrent.atomic.AtomicReference

/**
 * The Java implementation of the [IStorage] interface.
 *
 * This storage class is extended in `here-naksha-storage-psql`, which has a `PsqlStoragePlugin` class that implements the `Plugin` contract (internal contract in _Naksha-Hub_) and makes the storage available to the **Naksha-Hub** as a storage plugin. It parses the configuration from feature properties given, and calls this constructor.
 * @constructor Creates a new PSQL storage.
 * @property cluster the PostgresQL instances used by this storage.
 * @property defaultSchemaName the default schema name.
 */
open class PsqlStorage(override val cluster: PsqlCluster, override val defaultSchemaName: String) : PgStorage, IStorage {
    private var _adminOptions: SessionOptions? = null
    override var adminOptions: SessionOptions
        get() = _adminOptions ?: SessionOptions(
            map = mapToSchema(defaultSchemaName),
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
            _adminOptions = value.copy(map = mapToSchema(defaultSchemaName))
        }

    override var hardCap: Int = 1_000_000

    private var _pageSize: Int? = null
    override val pageSize: Int
        get() = _pageSize ?: throw NakshaException(UNINITIALIZED, "Storage uninitialized")

    private var _maxTupleSize: Int? = null
    override val maxTupleSize: Int
        get() = _maxTupleSize ?: throw NakshaException(UNINITIALIZED, "Storage uninitialized")

    private var _brittleTableSpace: String? = null
    override val brittleTableSpace: String?
        get() {
            if (_id.get() == null) throw NakshaException(UNINITIALIZED, "Storage uninitialized")
            return _brittleTableSpace
        }

    private var _tempTableSpace: String? = null
    override val tempTableSpace: String?
        get() {
            if (_id.get() == null) throw NakshaException(UNINITIALIZED, "Storage uninitialized")
            return _tempTableSpace
        }

    private var _gzipExtension: Boolean? = null
    override val gzipExtension: Boolean
        get() = _gzipExtension ?: throw NakshaException(UNINITIALIZED, "Storage uninitialized")

    private var _postgresVersion: NakshaVersion? = null
    override val postgresVersion: NakshaVersion
        get() = _postgresVersion ?: throw NakshaException(UNINITIALIZED, "Storage uninitialized")

    private var _id: AtomicReference<String?> = AtomicReference(null)
    private var _txnSequenceOid: Int? = null

    override fun id(): String = _id.get() ?: throw NakshaException(UNINITIALIZED, "Storage uninitialized")

    override fun initStorage(params: Map<String, *>?) {
        if (this._id.get() != null) return
        synchronized(this) {
            if (this._id.get() != null) return
            val context: NakshaContext = if (params != null && params.containsKey(CONTEXT)) {
                val v = params[CONTEXT]
                require(v is NakshaContext) { "params.${CONTEXT} must be an instance of NakshaContext" }
                v
            } else NakshaContext.currentContext()

            val options: SessionOptions = if (params != null && params.containsKey(OPTIONS)) {
                val v = params[OPTIONS]
                require(v is SessionOptions) { "params.${OPTIONS} must be an instance of SessionOptions" }
                v
            } else SessionOptions.from(context)

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

            val conn = cluster.newConnection(options, false)
            conn.use {
                logger.info("Start init of database {}", conn.jdbc.url)
                conn.jdbc.autoCommit = false

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
                val schema = defaultSchema()
                val storage_id = schema.init_internal(initId, conn, version, override)
                _id.set(storage_id)

                logger.info("Load OID of transaction sequence counter (located only in default schema)")
                cursor = conn.execute("SELECT oid FROM pg_class WHERE relname='$NAKSHA_TXN_SEQ' AND relnamespace=${schema.oid}").fetch()
                cursor.use {
                    _txnSequenceOid = cursor["oid"]
                }
            }
        }
    }

    override fun rowToFeature(row: Row): NakshaFeature {
        return if (row.feature != null) {
            // TODO: FIXME, we need the XYZ namespace
            val featureReader = JbFeatureDecoder(JbDictManager()).mapBytes(row.feature!!).reader
            val feature = JbMapDecoder().mapReader(featureReader).toAnyObject().proxy(NakshaFeature::class)
            feature
        } else {
            TODO("We will always have at least the id, which is formally enough to generate an empty feature!")
        }
    }

    override fun featureToRow(feature: NakshaFeature): Row {
        val nakshaFeature = feature.proxy(NakshaFeature::class)
        TODO("Implement me")
    }

    override fun enterLock(id: String, waitMillis: Int64): ILock {
        // Very likely, we should move "enterLock" into ISession
        // The reason is, that when we implement using advisory lock, we need to block connection
        // When being in a session, we can allow the locker to use this session to do stuff and not to waste an idle connection!
        TODO("Not yet implemented")
    }

    override fun newSession(options: SessionOptions, readOnly: Boolean): PgSession = PsqlSession(this, options, readOnly)

    override fun newConnection(options: SessionOptions, readOnly: Boolean, init: Fx2<PgConnection, String>?): PgConnection {
        val conn = cluster.newConnection(options, readOnly)
        val quotedSchema = quoteIdent(mapToSchema(options.map))
        // TODO: Do we need more initialization work here?
        val query = "SET SESSION search_path TO $quotedSchema, public, topology;\n"
        if (init != null) init.call(conn, query) else conn.execute(query).close()
        return conn
    }

    override fun close() {
        TODO("Not yet implemented")
    }

    override fun dictManager(map: String): IDictManager {
        TODO("Not yet implemented")
    }

    /**
     * All cached schemata.
     */
    private val schemata: AtomicMap<String, WeakRef<PsqlSchema>> = Platform.newAtomicMap()

    override fun initMap(map: String) {
        val schema = mapToSchema(map)
        this[schema].init()
    }

    override fun dropMap(map: String) {
        val schema = mapToSchema(map)
        if (schema in this) this[schema].drop()
    }

    override fun defaultSchema(): PsqlSchema = this[defaultSchemaName]

    override fun defaultFlags(): Flags = Flags()
        .featureEncoding(FeatureEncoding.JBON_GZIP)
        .geoEncoding(GeoEncoding.TWKB_GZIP)
        .tagsEncoding(TagsEncoding.JBON_GZIP)

    override operator fun contains(schemaName: String): Boolean = schemata.containsKey(schemaName)

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

    override fun validateHandle(handle: String, ttl: Int?): Boolean {
        TODO("Not yet implemented")
    }

    override fun fetchRowsById(map: String, collectionId: String, rowIds: Array<RowId>, mode: String): List<Row?> {
        TODO("Not yet implemented")
    }

    override fun fetchRows(rows: List<ResultRow?>, from: Int, to: Int, mode: String) {
        TODO("Not yet implemented")
    }

    override fun fetchRow(row: ResultRow, mode: String) {
        TODO("Not yet implemented")
    }

    override fun txnSequenceOid(): Int = _txnSequenceOid ?: throw NakshaException(UNINITIALIZED, "Storage uninitialized")

    // TODO: We need a background job that listens to notification (see PG notify).
    //       This job should update the schema, table, and all other caches instantly!
    //       This means as well, we need to implement notifications, whenever something changes that relates to cache!
}