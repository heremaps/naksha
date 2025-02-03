package naksha.psql

import naksha.base.*
import naksha.base.Platform.PlatformCompanion.logger
import naksha.base.fn.Fx2
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
 *   .withMaster(PgInstanceConfig()
 *   .withHost("host")
 *   .withDb("testdb")
 *   .withUser("fred")
 *   .withPassword("secret"))
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

    protected var _pageSize: Int? = null

    /**
     * The page-size of the database (`current_setting('block_size')`).
     * - Throws [NakshaError.UNINITIALIZED], if the storage is not yet initialized.
     * @since 3.0.0
     */
    val pageSize: Int
        get() = _pageSize ?: throw NakshaException(UNINITIALIZED, "Storage uninitialized")

    private var _maxTupleSize: Int? = null

    /**
     * The maximum size of a tuple (row).
     * - Throws [NakshaError.UNINITIALIZED], if the storage is not yet initialized.
     * @since 3.0.0
     */
    val maxTupleSize: Int
        get() = _maxTupleSize ?: throw NakshaException(UNINITIALIZED, "Storage uninitialized")

    private var _brittleTableSpace: String? = null
    private var _brittleTableSpaceOid: Int? = null

    /**
     * The tablespace to use for storage-class "brittle"; if any.
     * - Throws [NakshaError.UNINITIALIZED], if the storage is not yet initialized.
     * @since 3.0.0
     */
    val brittleTableSpace: String?
        get() {
            if (!initialized) throw NakshaException(UNINITIALIZED, "Storage uninitialized")
            return _brittleTableSpace
        }

    private var _tempTableSpace: String? = null
    private var _tempTableSpaceOid: Int? = null

    /**
     * The tablespace to use for temporary tables and their indices; if any.
     * - Throws [NakshaError.UNINITIALIZED], if the storage is not yet initialized.
     * @since 3.0.0
     */
    val tempTableSpace: String?
        get() {
            if (!initialized) throw NakshaException(UNINITIALIZED, "Storage uninitialized")
            return _tempTableSpace
        }

    private var _ephemeralTableSpace: String? = null
    private var _ephemeralTableSpaceOid: Int? = null

    /**
     * The tablespace to use for ephemeral tables and their indices; if any.
     * - Throws [NakshaError.UNINITIALIZED], if the storage is not yet initialized.
     * @since 3.0.0
     */
    val ephemeralTableSpace: String?
        get() {
            if (!initialized) throw NakshaException(UNINITIALIZED, "Storage uninitialized")
            return _ephemeralTableSpace
        }

    private var _gzipExtension: Boolean? = null

    /**
     * If the [pgsql-gzip][https://github.com/pramsey/pgsql-gzip] extension is installed, therefore PostgresQL supported `gzip`/`gunzip` as standalone SQL function by the database. Note, that if this is not the case, we're installing code that is implemented in JavaScript.
     * - Throws [NakshaError.UNINITIALIZED], if the storage is not yet initialized.
     * @since 3.0.0
     */
    val gzipExtension: Boolean
        get() = _gzipExtension ?: throw NakshaException(UNINITIALIZED, "Storage uninitialized")

    private var _postgresVersion: NakshaVersion? = null

    /**
     * The PostgresQL database version.
     * - Throws [NakshaError.UNINITIALIZED], if the storage is not yet initialized.
     * @since 3.0.0
     */
    val postgresVersion: NakshaVersion
        get() = _postgresVersion ?: throw NakshaException(UNINITIALIZED, "Storage uninitialized")

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
     * Test if this storage has the given _id_ and _number_.
     * - Throws [STORAGE_ID_MISMATCH], if an invalid _id_ or _number_ was given.
     * @param id the expected _id_.
     * @param number the expected _number_.
     * @return _true_ if the current _id_ and _number_ are the given-expected ones; _false_ if the storage is not initialized.
     */
    private fun isIdAndNumber(id: String, number: Int64): Boolean {
        if (this.id != id) {
            throw NakshaException(STORAGE_ID_MISMATCH, "The storage-id is '${this.id}', but was expected to be '$id'")
        }
        if (this.number != number) {
            throw NakshaException(STORAGE_ID_MISMATCH, "The storage-number is '${this.number}', but was expected to be '$number'")
        }
        return true
    }

    /**
     * If there is a special tablespace for temporary tables.
     */
    protected var temp_tablespace: String? = null

    /**
     * If there is a special tablespace for brittle tables.
     */
    protected var brittle_tablespace: String? = null

    /**
     * If there is a special tablespace for ephemeral tables.
     */
    protected var ephemeral_tablespace: String? = null

    /**
     * Internally invoked by [initStorage], if the storage is outdated or not available at all.
     * @param config the configuration to be used for setup or upgrade.
     * @since 3.0.0
     */
    protected open fun createOrUpgradeStorage(config: PgConfig) {
        throw NakshaException(UNSUPPORTED_OPERATION, "This implementation is not capable of setting-up or upgrading the storage")
    }

    override fun initStorage(config: PgConfig, create: Boolean?, upgrade: Boolean?) {
        if (isIdAndNumber(id, number)) return
        lock.acquire().use {
            if (isIdAndNumber(id, number)) return

            // Ensure that the cluster is available.
            // Within PLV8 implementation this will create a fake PgCluster instance, only supporting a single connection.
            setupCluster(config)

            val override = config.override
            val temp_spcname: String = config.temp_tablespace ?: "temp"
            val brittle_spcname: String = config.brittle_tablespace ?: "brittle"
            val ephemeral_spcname: String = config.ephemeral_tablespace ?: "ephemeral"
            val config_version = config.version
            val version = if (config_version != null) NakshaVersion.of(config_version) else NakshaVersion.latest

            val conn = cluster.newConnection(options, false)
            conn.use {
                logger.info("Start initStorage of database {}", conn.toUri())
                conn.autoCommit = false

                logger.info("Query basic database information")
                var cursor = conn.execute(
                    """
WITH basics AS (SELECT 
    current_setting('block_size')::int4 AS bs, 
    (SELECT oid FROM pg_catalog.pg_tablespace WHERE spcname = '$temp_spcname') AS temp_oid,
    (SELECT oid FROM pg_catalog.pg_tablespace WHERE spcname = '$brittle_spcname') AS brittle_oid,
    (SELECT oid FROM pg_catalog.pg_tablespace WHERE spcname = '$ephemeral_spcname') AS ephemeral_oid,
    (SELECT oid FROM pg_catalog.pg_namespace WHERE nspname = 'naksha~admin') AS admin_oid,
    (SELECT oid FROM pg_catalog.pg_extension WHERE extname = 'gzip') AS gzip_oid,
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
                    var raw = cursor.column("temp_oid")
                    if (raw is Int) {
                        _tempTableSpaceOid = raw
                        _tempTableSpace = temp_spcname
                    }
                    raw = cursor.column("brittle_oid")
                    if (raw is Int) {
                        _brittleTableSpaceOid = raw
                        _brittleTableSpace = brittle_spcname
                    }
                    raw = cursor.column("ephemeral_oid")
                    if (raw is Int) {
                        _ephemeralTableSpaceOid = raw
                        _ephemeralTableSpace = ephemeral_spcname
                    }
                    _gzipExtension = cursor.column("gzip_oid") is Int
                    // "PostgreSQL 15.5 on aarch64-unknown-linux-gnu, compiled by gcc (GCC) 7.3.1 20180712 (Red Hat 7.3.1-6), 64-bit"
                    val v: String = cursor["version"]
                    val start = v.indexOf(' ') + 1
                    val end = v.indexOf(' ', start)
                    _postgresVersion = NakshaVersion.of(v.substring(start, end))

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
    internal open fun newSession(options: SessionOptions, readOnly: Boolean): PgSession = PgSession(this, options, readOnly)

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
     * @param options the options for the connection.
     * @param init an optional initialization function, if given, then it will be called with the string to be used to initialize the connection. It may just use this string, perform arbitrary additional work, or suppress initialization completely.
     * @return the admin connection that does not count against connection-limit, to be closed after usage.
     * @since 3.0.0
     */
    internal open fun adminConnection(options: SessionOptions = Naksha.adminOptions, init: Fx2<PgConnection, String>? = null): PgConnection
        = newConnection(options, false, init)

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

    override fun shutdownStorage() {}
}

// PgAdminMap(storage: PgStorage)
//
//  getTxn(session: IReadSession): Int64
//  newTxn(session: IWriteSession): Int64
//  getMapNumber(session: IReadSession): Int
//  newMapNumber(session: IWriteSession): Int
//  refreshMapCache(session: IReadSession): Boolean
//  createMap(session: IWriteSession, map: NakshaMap): PgMap
//  getMapById(session: IWriteSession, id: String): PgMap?
//  getMapByNumber(session: IWriteSession, number: Int): PgMap?
//  listMaps(session: IWriteSession): PgMapList

// PgMap(storage: PgStorage, feature: NakshaMap)
// PgMap(storage: PgStorage, tuple: Tuple)
//   - requires a NakshaMap object, which is used as configuration
//     note: we do no longer read data from the database, because this is too slow
//           when a PgMap is created, it should be an instance action, not requiring any DB access, it only parses the config!
//   - we add all helpers needed to manage a map to it, like reading and updating the collection-sequence
//   - every map has one internal PgCollection (`naksha~collections`), which stores the collections them-self!
//   - creating a map requires to create a collection-sequence, and then the initial `naksha~collections` collection
//   - for the root admin map, the pg-storage will then create
//     - `naksha~maps`
//     - `naksha~transactions`
//     - `naksha~dictionaries`
//     - `naksha~handles`
//   - we do not allow to create any other collections within the root map nor do we allow modification of collections
//   - therefore all maps need to support an `readOnly` setting
//
// - state: FeatureTuple
// - drop(session: IWriteSession) - drop this map physically in the database
// - getCollectionNumber(session: IReadSession): Int
// - newCollectionNumber(session: IWriteSession): Int
// - createCollection(session: IWriteSession, feature: NakshaCollection): PgCollection
// - getCollectionById(session: IWriteSession, id: String): PgCollection?
// - getCollectionByNumber(session: IWriteSession, number: Int): PgCollection?

// PgCollection(map: PgMap, feature: NakshaCollection)
// PgCollection(map: PgMap, tuple: Tuple)
//   - requires a NakshaCollection, which is used as configuration
//     note: we do no longer read data from the database, because this is too slow
//           when a PgCollection is created, it should be an instance action, not requiring any DB access, it only parses the config!
//   - low level helper that allows reading and writing data
//   - the NakshaCollections of the internal collections are immutable
//
// - state: FeatureTuple
// - exists(session: IReadSession) - tests if this collection exists physically
// - drop(session: IWriteSession) - drop this collection physically in the database
// - queryHead(session: IReadSession, queries: ?): ResultSet - see BINARY.md
// - queryHistory(session: IReadSession, minVersion: Version?, version: Version, queries: ?): ResultSet - see BINARY.md
// - queryMultiVersion(session: IReadSession, minVersion: Version?, version: Version, versions: Int, queries: ?): ResultSet - see BINARY.md
// - getTuples(session: IReadSession, ids) - see BINARY.md
// - write(session: IWriteSession, req: List<Write>): Response
//   this needs to be optimal performing, so we need always to use bulk-writer!

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

