package naksha.psql

import naksha.base.*
import naksha.base.Platform.PlatformCompanion.logger
import naksha.base.fn.Fx2
import naksha.jbon.JbDictionary
import naksha.model.*
import naksha.model.FetchMode.*
import naksha.model.NakshaError.NakshaErrorCompanion.FORBIDDEN
import naksha.model.NakshaError.NakshaErrorCompanion.ILLEGAL_ARGUMENT
import naksha.model.NakshaError.NakshaErrorCompanion.STORAGE_ID_MISMATCH
import naksha.model.NakshaError.NakshaErrorCompanion.UNINITIALIZED
import naksha.model.NakshaError.NakshaErrorCompanion.UNSUPPORTED_OPERATION
import naksha.model.NakshaVersion.Companion.LATEST
import naksha.model.request.FeatureTuple
import naksha.psql.PgColumn.PgColumnCompanion.app_id
import naksha.psql.PgColumn.PgColumnCompanion.attachment
import naksha.psql.PgColumn.PgColumnCompanion.author
import naksha.psql.PgColumn.PgColumnCompanion.author_ts
import naksha.psql.PgColumn.PgColumnCompanion.change_count
import naksha.psql.PgColumn.PgColumnCompanion.created_at
import naksha.psql.PgColumn.PgColumnCompanion.feature
import naksha.psql.PgColumn.PgColumnCompanion.flags
import naksha.psql.PgColumn.PgColumnCompanion.geo
import naksha.psql.PgColumn.PgColumnCompanion.here_tile
import naksha.psql.PgColumn.PgColumnCompanion.hash
import naksha.psql.PgColumn.PgColumnCompanion.id
import naksha.psql.PgColumn.PgColumnCompanion.origin
import naksha.psql.PgColumn.PgColumnCompanion.ref_point
import naksha.psql.PgColumn.PgColumnCompanion.tags
import naksha.psql.PgColumn.PgColumnCompanion.tn
import naksha.psql.PgColumn.PgColumnCompanion.txn
import naksha.psql.PgColumn.PgColumnCompanion.txn_next
import naksha.psql.PgColumn.PgColumnCompanion.ft
import naksha.psql.PgColumn.PgColumnCompanion.uid
import naksha.psql.PgColumn.PgColumnCompanion.updated_at
import naksha.psql.PgUtil.PgUtilCompanion.CONTEXT
import naksha.psql.PgUtil.PgUtilCompanion.OPTIONS
import naksha.psql.PgUtil.PgUtilCompanion.OVERRIDE
import naksha.psql.PgUtil.PgUtilCompanion.VERSION
import naksha.psql.PgUtil.PgUtilCompanion.quoteIdent
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
 * The PostgresQL storage that manages session and connections.
 *
 * This class is a default multi-platform implements of the [IStorage] interface for the PostgresQL database. To get an instance of it, a platform specific code has to be used, being:
 *
 * - In Java: Create an instance of `PsqlStorage`, which is the only version that can install needed scripts, when [initStorage] is called. Multiple instances can be created.
 * - In PLV8 (the PostgresQL database extension): A new storage instance is created as singleton, and added into to the global `plv8` object, when the `naksha_start_session` SQL function is executed, which is necessary for all other Naksha SQL functions to work. This singleton will hold only a single [PgSession], trying to acquire a second one, will always error with [NakshaError.ILLEGAL_STATE]. The [cluster] will be a fake object.
 */
@Suppress("OPT_IN_USAGE")
@JsExport
open class PgStorage protected constructor(
    /**
     * The PostgresQL cluster to which this storage is connected.
     *
     * Will be _null_, if being executed within [PLV8 extension](https://plv8.github.io/).
     */
    open val cluster: PgCluster
) : IStorage {

    override var adminOptions: SessionOptions? = null

    override fun useAdminOptions(): SessionOptions = adminOptions ?: SessionOptions(
        appName = "lib-psql/$LATEST",
        appId = NakshaContext.appId(),
        author = NakshaContext.author(),
        parallel = false,
        useMaster = true,
        excludePaths = NakshaContext.defaultExcludePaths.get(),
        excludeFn = NakshaContext.defaultExcludeFn.get(),
        connectTimeout = NakshaContext.defaultConnectTimeout.get(),
        socketTimeout = NakshaContext.defaultSocketTimeout.get(),
        stmtTimeout = NakshaContext.defaultStmtTimeout.get(),
        lockTimeout = NakshaContext.defaultLockTimeout.get()
    )

    override var hardCap: Int = 16777216
        set(value) {
            if (value > 16777216) throw NakshaException(ILLEGAL_ARGUMENT, "The maximum hard-cap supported is 16777216, but $value was requested")
            field = if (value <= 0) 16777216 else value
        }

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

    override val id: String
        get() = _id.get() ?: throw NakshaException(UNINITIALIZED, "Storage uninitialized")

    private var _number: Int64? = null

    override val number: Int64
        get() = _number ?: throw NakshaException(UNINITIALIZED, "Storage uninitialized")

    override fun isInitialized(): Boolean = _id.get() != null

    protected var admin_oid: Int? = null

    /**
     * All cached maps.
     * @since 3.0.0
     */
    protected val maps: AtomicMap<String, PgMap> = Platform.newAtomicMap()

    /**
     * A map between unique map-numbers and map-identifiers.
     * @since 3.0.0
     */
    protected val mapNumberToId: AtomicMap<Int, String> = Platform.newAtomicMap()

    /**
     * A lock for the storage to synchronize access to some properties and to prevent, that multiple threads in parallel initialize the storage.
     * - [initStorage]
     */
    protected val lock = Platform.newLock()

    override fun initStorage(id: String, number: Int64, params: Map<String, *>?) {
        val this_id = this._id.get()
        if (this_id != null) {
            if (this_id != id) {
                throw NakshaException(STORAGE_ID_MISMATCH, "The storage-id is '$this_id', but is expected to be '$id'")
            }
            if (this.number != number) {
                throw NakshaException(STORAGE_ID_MISMATCH, "The storage-number is '$this.number', but is expected to be '$number'")
            }
            return
        }
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
                logger.info("Start initStorage of database {}", conn.toUri())
                conn.autoCommit = false

                logger.info("Query basic database information")
                var cursor = conn.execute(
                    """
WITH basics AS (SELECT 
    current_setting('block_size')::int4 AS bs, 
    (SELECT oid FROM pg_catalog.pg_tablespace WHERE spcname = '$TEMPORARY_TABLESPACE') AS temp_oid,
    (SELECT oid FROM pg_catalog.pg_extension WHERE extname = 'gzip') AS gzip_oid,
    (SELECT oid FROM pg_catalog.pg_namespace WHERE nspname = 'naksha~admin') AS admin_oid,
    version() AS version
), procs AS (SELECT 
    (SELECT true FROM pg_catalog.pg_proc, basics WHERE pronamespace = basics.admin_oid AND proname = 'naksha_version') AS has_naksha_version,
    (SELECT true FROM pg_catalog.pg_proc, basics WHERE pronamespace = basics.admin_oid AND proname = 'naksha_storage_id') AS has_naksha_storage_id,
    (SELECT true FROM pg_catalog.pg_proc, basics WHERE pronamespace = basics.admin_oid AND proname = 'naksha_storage_number') AS has_naksha_storage_number
)
SELECT basics.*, procs.* FROM basics, procs;
"""
                ).fetch()
                val has_naksha_version: Boolean?
                val has_naksha_storage_id: Boolean?
                val has_naksha_storage_number: Boolean?
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

                    admin_oid = cursor["admin_oid"]
                    has_naksha_version = cursor["has_naksha_version"]
                    has_naksha_storage_id = cursor["has_naksha_storage_id"]
                    has_naksha_storage_number = cursor["has_naksha_storage_number"]
                }
                // Note: PostgresQL parses the query before it evaluates it, therefore, we must not access a schema that does not exist.
                //       This forces us to execute the version read as a second query, ones we are sure that the schema and function exist.
                var naksha_version: NakshaVersion? = null
                var naksha_storage_id: String? = null
                var naksha_storage_number: Int64? = null
                if (admin_oid != null && has_naksha_version == true && has_naksha_storage_id == true && has_naksha_storage_number == true) {
                    cursor = conn.execute("SELECT \"naksha~admin\".naksha_version() AS v, \"naksha~admin\".naksha_storage_id() AS id, \"naksha~admin\".naksha_storage_number() AS n").fetch()
                    val v: Int64 = cursor["v"]
                    naksha_version = NakshaVersion(v)
                    naksha_storage_id = cursor["id"]
                    naksha_storage_number = cursor["n"]
                }
                if (override || naksha_version != version) {
                    if (!context.su) throw NakshaException(FORBIDDEN, "To install new storages admin privileges are required, please set 'su' flag in context")
                    if (naksha_storage_id != null && naksha_storage_id != id) {
                        throw NakshaException(STORAGE_ID_MISMATCH, "The storage-id is '$naksha_storage_id', but is expected to be '$id'")
                    }
                    if (naksha_storage_number != null && naksha_storage_number != number) {
                        throw NakshaException(STORAGE_ID_MISMATCH, "The storage-number is '$naksha_storage_number', but is expected to be '$number'")
                    }
                    if (naksha_version != null)
                        logger.info("Upgrade Naksha admin schema from $naksha_version to $version for storage $id / $number")
                    else
                        logger.info("Install Naksha admin schema in version $version for storage $id / $number")
                    admin_oid = upsertAdminMap(id, number, version, naksha_version)
                    logger.info("Installation done, commit changes")
                    conn.commit()
                } else {
                    if (naksha_storage_id != id) {
                        throw NakshaException(STORAGE_ID_MISMATCH, "The storage-id is '$naksha_storage_id', but is expected to be '$id'")
                    }
                    if (naksha_storage_number != number) {
                        throw NakshaException(
                            STORAGE_ID_MISMATCH,
                            "The storage-number is '$naksha_storage_number', but is expected to be '$number'"
                        )
                    }
                }
                logger.info("Load OID of sequence counters")
                cursor = conn.execute("""SELECT 
(SELECT oid FROM pg_class WHERE relname = '$NAKSHA_TXN_SEQ') AS txn_oid,
(SELECT oid FROM pg_class WHERE relname = '$NAKSHA_MAP_SEQ') AS map_oid
""").fetch()
                cursor.use {
                    _txnSequenceOid = cursor["txn_oid"]
                    _mapNumberSequenceOid = cursor["map_oid"]
                }
                logger.info("Storage $id / $number initialized, txn-seq-oid=$_txnSequenceOid, map-seq-oid=$_mapNumberSequenceOid")
                beforeInit(id, number)
                _number = number
                _id.set(id) // this must be set as last action, because it is tested first in all other places!
                afterInit()
            }
        }
    }

    /**
     * An internal method invoked by [initStorage], if it detects that the Postgresql database does not yet have the admin-schema (`naksha~admin`), or that it is in an older version, and that we need to create or upgrade it. This means optionally installing or upgrading SQL functions, creating schema, tables, as well as admin-collections.
     *
     * This operation is executing with in [lock], so that it can be sure that no other thread is doing the same thing.
     *
     * Note that [toVersion] and [fromVersion] can be the same, if the installation should be overridden explicitly!
     * @param toVersion the target version to which to upgrade, normally [NakshaVersion.latest], but can be overridden from environment.
     * @param fromVersion the version that is currently installed in `naksha~admin`, _null_ if either the schema or method do not exist.
     * @return the OID of the admin schema.
     * @since 3.0.0
     */
    protected open fun upsertAdminMap(id: String, number: Int64, toVersion: NakshaVersion, fromVersion: NakshaVersion?): Int {
        throw NakshaException(UNSUPPORTED_OPERATION, "Creating new storages is only supported by JVM code")
    }

    /**
     * Helper method invoked by [initStorage] before initialization is done, so just before [id] and [number] will be set.
     * @param id the storage-id.
     * @param number the storage-number.
     * @since 3.0.0
     */
    protected open fun beforeInit(id: String, number: Int64) {}

    /**
     * Helper method invoked by [initStorage] after initialization has been done successfully, so just after [id], and [number] were set, but before the [lock] is released.
     * @since 3.0.0
     */
    protected open fun afterInit() {}

    /**
     * The default flags to use for the storage.
     * @return default flags to use for the storage.
     */
    internal val defaultFlags: Flags = Flags()
        .withFeatureEncoding(FeatureEncoding.JBON_GZIP)
        .withGeoEncoding(GeoEncoding.TWKB_GZIP)
        .withTagsEncoding(TagsEncoding.JBON_GZIP)

    private var _txnSequenceOid: Int? = null

    /**
     * The OID of the transaction sequence.
     */
    internal val txnSequenceOid: Int
        get() = _txnSequenceOid ?: throw NakshaException(UNINITIALIZED, "Storage uninitialized")

    private var _mapNumberSequenceOid: Int? = null

    /**
     * The OID of the map-number sequence.
     */
    internal val mapNumberSequenceOid: Int
        get() = _mapNumberSequenceOid ?: throw NakshaException(
            UNINITIALIZED,
            "Storage uninitialized"
        )

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
    open fun newSession(options: SessionOptions, readOnly: Boolean): PgSession =
        PgSession(this, options, readOnly)

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
    open fun newConnection(
        options: SessionOptions,
        readOnly: Boolean,
        init: Fx2<PgConnection, String>? = null
    ): PgConnection {
        val conn = cluster.newConnection(options, readOnly)
        val query = "SET SESSION search_path TO \"naksha~admin\", hint_plan, public, topology;\n"
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
    internal open fun adminConnection(
        options: SessionOptions = useAdminOptions(),
        init: Fx2<PgConnection, String>? = null
    ): PgConnection = newConnection(options, false, init)

    /**
     * Tests if the given handle is valid, and if it is, tries to extend its live-time to the given amount of milliseconds.
     *
     * Some handles may expire after some time. For example, when custom filters were applied, the generated result-set must be stored somewhere to guarantee that it is always the same (we can't store the filter code!), but we do not store this forever, so the handle does have an expiry. Some handles may not have an expiry, for example when the storage can reproduce them at any moment, using just the information from the handle.
     *
     * There is no guarantee that the life-time of the handle can be extended, especially when invoking this method on a read-only session.
     * @param conn the connection to use.
     * @param handle the handle to test.
     * @param ttl if not _null_, the time-to-live of the handle should be extended by the given amount of milliseconds, if possible.
     * @return _true_ if the handle is valid, _false_ otherwise.
     * @since 3.0.0
     */
    fun validateHandle(conn: PgConnection, handle: String, ttl: Int? = null): Boolean {
        TODO("Implement validateHandle")
    }

    /**
     * Fetches all tuples in the given result-tuples.
     *
     * @param conn the connection to use.
     * @param featureTuples a list of result-tuples to fetch.
     * @param from the index of the first result-tuples to fetch.
     * @param to the index of the first result-tuples to ignore.
     * @param fetchFromHistory if the history should be queried.
     * @param mode the fetch mode.
     * @since 3.0.0
     * @see ISession.fetchTuples
     */
    fun fetchTuples(
        conn: PgConnection,
        featureTuples: List<FeatureTuple?>,
        from: Int = 0,
        to: Int = featureTuples.size,
        fetchFromHistory: Boolean = false,
        mode: FetchMode = FetchMode.FETCH_ALL
    ) {
        val loader = PgTupleLoader(this, fetchFromHistory, conn)
        for (rt in featureTuples) loader.add(rt, mode)
        val all = loader.execute()
        for (i in all.indices) {
            val rt = featureTuples[i]
            if (rt != null) {
                rt.op = ExecutedOp.READ
                rt.tuple = all[i]
            }
        }
    }

    companion object PgStorage_C {
        /**
         * The admin map identifier.
         * @since 3.0.0
         */
        internal const val ADMIN_MAP_ID = "naksha~admin"

        /**
         * The map-number of the admin-map.
         * @since 3.0.0
         */
        internal const val ADMIN_MAP_NUMBER = 0

        /**
         * All columns to be added into a SELECT query, already quoted, if needed.
         */
        @Deprecated(message = "Please directly use PgColumn.fullSelect", replaceWith = ReplaceWith("PgColumn.fullSelect"))
        internal val ALL_COLUMNS = PgColumn.fullSelect

        /**
         * Helper method to read a [Tuple] from a [PgCursor].
         *
         * It automatically detects which parts have been selected, but requires that at least:
         * - either [tuple_number][PgColumn.tn] or [txn][PgColumn.txn], [store_number][PgColumn.store_number] and [uid][PgColumn.uid]
         * - [flags][PgColumn.flags]
         * - [id][PgColumn.id]
         *
         * Have been selected, because otherwise it is not possible to construct the [Tuple], which requires the `tuple-number`, `id` and `flags`. Without the `flags` decoding of parts is not possible.
         * @param storage the storage from which to read.
         * @param cursor the cursor to read.
         * @return the read tuple.
         */
        internal fun readTupleFromCursor(storage: PgStorage, cursor: PgCursor): Tuple {
            val tupleNumberByteArray: ByteArray? = cursor.column(tn) as ByteArray?
            val tupleNumber = if (tupleNumberByteArray != null) TupleNumber.fromFullVariant(tupleNumberByteArray) else {
                val _txn: Int64 = cursor[txn]
                TupleNumber(
                    cursor[store_number],
                    Version(_txn),
                    cursor[uid]
                )
            }

            // We always need at least tuple-number and id
            var fetchMode: FetchMode = FetchMode.FETCH_ID
            val id: String = cursor[id]
            val flags: Flags = cursor[flags]

            val updatedAt: Int64? = cursor.column(updated_at) as Int64?
            val metadata = if (updatedAt != null) {
                fetchMode = fetchMode.withMeta()
                val createdAt = cursor.column(created_at) as Int64?
                val authorTs = cursor.column(author_ts) as Int64?
                val _txn_next = cursor.column(txn_next) as Int64?
                val _ptxn = cursor.column(ptxn) as Int64?
                Metadata(
                    storeNumber = tupleNumber.storeNumber,
                    updatedAt = updatedAt,
                    createdAt = createdAt ?: updatedAt,
                    authorTs = authorTs ?: updatedAt,
                    nextVersion = if (_txn_next != null) Version(_txn_next) else null,
                    version = tupleNumber.version,
                    prevVersion = if (_ptxn != null) Version(_ptxn) else null,
                    uid = tupleNumber.uid,
                    puid = cursor.column(puid) as Int?,
                    hash = cursor[hash],
                    changeCount = cursor[change_count],
                    hereTile = cursor[here_tile],
                    flags = flags,
                    id = id,
                    appId = cursor[app_id],
                    author = cursor.column(author) as String?,
                    ft = cursor.column(ft) as String?,
                    originTupleNumber = cursor.column(origin) as String?
                )
            } else null
            if (feature in cursor) fetchMode = fetchMode.withFeature()
            if (geo in cursor) fetchMode = fetchMode.withGeometry()
            if (ref_point in cursor) fetchMode = fetchMode.withReferencePoint()
            if (tags in cursor) fetchMode = fetchMode.withTags()
            if (attachment in cursor) fetchMode = fetchMode.withAttachment()
            return Tuple(
                storage = storage,
                tupleNumber = tupleNumber,
                state = fetchMode,
                meta = metadata,
                id = id,
                flags = flags,
                feature = cursor.column(feature) as ByteArray?,
                geo = cursor.column(geo) as ByteArray?,
                referencePoint = cursor.column(ref_point) as ByteArray?,
                tags = cursor.column(tags) as ByteArray?,
                attachment = cursor.column(attachment) as ByteArray?
            )
        }
    }

    override fun close() {}
}
