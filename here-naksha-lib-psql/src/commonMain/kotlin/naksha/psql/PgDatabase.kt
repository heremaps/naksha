package naksha.psql

import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import naksha.base.*
import naksha.base.NakshaError.NakshaErrorCompanion.EXCEPTION
import naksha.base.NakshaError.NakshaErrorCompanion.STORAGE_ID_MISMATCH
import naksha.base.Platform.PlatformCompanion.logger
import naksha.base.fn.Fn0
import naksha.base.fn.Fx0
import naksha.model.Naksha
import naksha.model.NakshaVersion
import naksha.model.Tuple
import naksha.model.objects.NakshaAdmin
import naksha.model.objects.NakshaDatabase
import naksha.psql.PgUtil.PgUtilCompanion.quoteIdent
import kotlin.js.JsExport
import kotlin.time.Instant

/**
 * A database is a logical concept in Naksha, see [NakshaDatabase].
 *
 * To keep the code more maintainable, we move all DDL _(data definition language)_ and SQL code into this class. So, mutation of the PostgresQL schema, tables, indices, and inserting, updating, delete rows. This class should deal with the physical reality of PostgresQL, not with the logical level of the Naksha storage concept. Therefore, this class works with `schema`, `table`, `partition` and `rows`. It should not have any knowledge about the logical part, like that a `row` represents a [Tuple][naksha.model.Tuple], it must not know about books or JBON encoding.
 *
 * Its purpose is to manage low level database entities like `schema`, `table`, `partition` and `rows`. It is fixed to the Naksha needs, so it assumes a primary key above (`nv`, `fn`, `version`). It is strictly tied up to the Naksha storage model. So it requires the three columns `nv`, `fn`, and `version` and it allows arbitrary `PgColumn`. It will understand how the history works, without making any assumptions about the content of a row, so it basically manages rows, each having a unique address, linked to partitioned history.
 * @since 3.0
 * @see PgDatabaseImpl
 */
@JsExport
abstract class PgDatabase(
    /**
     * The storage in which the database is located.
     * @since 3.0
     */
    override val storage: PgStorage,

    /**
     * The identifier of the database.
     *
     * This is installed into the admin-schema `naksha~admin` in the functions `naksha_storage_id()` and `naksha_storage_number()`.
     *
     * In the current implementation, this matches [IStorage.id][naksha.model.IStorage.id].
     * @since 3.0
     */
    override val id: Id = storage.id
): PgCache(), NakshaDatabase {
    override val isMaster: Boolean = true
    override val isReplica: Boolean = false
    override val parent: PgCache? = null

    /**
     * The page-size of the database (`current_setting('block_size')`).
     * @since 3.0.0
     */
    val pageSize: Int
        get() = if (_pageSize == -1) throw notInitialized("PgDatabase") else _pageSize
    private var _pageSize: Int = -1

    /**
     * The maximum size of a row.
     * @since 3.0.0
     */
    val maxTupleSize: Int
        get() = if (_maxTupleSize == -1) throw notInitialized("PgDatabase") else _maxTupleSize
    private var _maxTupleSize: Int = -1

    /**
     * If the [pgsql-gzip][https://github.com/pramsey/pgsql-gzip] extension is installed, therefore PostgresQL supported `gzip`/`gunzip` as standalone SQL function by the database. Note, that if this is not the case, we're installing code that is implemented in JavaScript.
     * @since 3.0.0
     */
    val gzipExtension: Boolean
        get() = _gzipExtension ?: throw notInitialized("PgDatabase")
    private var _gzipExtension: Boolean? = null

    /**
     * The PostgresQL version, e.g. `18.6`.
     * @since 3.0.0
     */
    val postgresVersion: NakshaVersion
        get() = _postgresVersion ?: throw notInitialized("PgDatabase")
    private var _postgresVersion: NakshaVersion? = null

    /**
     * The OID of the current HEAD version.
     * @since 3.0.0
     */
    val versionSequenceOid: Int
        get() = if (_versionSequenceOid == -1) throw notInitialized("PgDatabase") else _versionSequenceOid
    private var _versionSequenceOid: Int = -1

    /**
     * The `OID` of the admin-map aka admin schema.
     * @since 3.0.0
     */
    val schemaOid: Int
        get() = if (_schemaOid == -1) throw notInitialized("PgDatabase") else _schemaOid
    private var _schemaOid: Int = -1

    /**
     * The admin-catalog itself _(`naksha~admin`)_.
     * @since 3.0.0
     */
    override val pgAdmin: PgCatalog
        get() = _pgAdmin ?: throw notInitialized("PgDatabase")
    private var _pgAdmin: PgCatalog? = null

    /**
     * The collections' collection _(`naksha~collections`)_, should exist in any schema, including the admin-schema (`naksha~admin`).
     * @since 3.0.0
     */
    override val pgAdminCollections: PgCollection
        get() = _pgAdminCollections ?: throw notInitialized("PgDatabase")
    private var _pgAdminCollections: PgCollection? = null

    /**
     * The transactions' collection _(`naksha~transactions`)_ only in the admin-schema (`naksha~admin`).
     * @since 3.0.0
     */
    override val pgAdminTransactions: PgCollection
        get() = _pgAdminTransactions ?: throw notInitialized("PgDatabase")
    private var _pgAdminTransactions: PgCollection? = null

    /**
     * The catalogs' collection _(`naksha~catalogs`)_ only in the admin-schema (`naksha~admin`).
     * @since 3.0.0
     */
    override val pgAdminCatalogs: PgCollection
        get() = _pgAdminCatalogs ?: throw notInitialized("PgDatabase")
    private var _pgAdminCatalogs: PgCollection? = null

    /**
     * The books' collection _(`naksha~books`)_ only in the admin-schema (`naksha~admin`).
     * @since 3.0.0
     */
    override val pgAdminBooks: PgCollection
        get() = _pgAdminBooks ?: throw notInitialized("PgDatabase")
    private var _pgAdminBooks: PgCollection? = null

    /**
     * Opens and initialized the database.
     *
     * Depending on parameters, installs SQL code, creates extensions, create `naksha~admin` schema, create admin collections, inserting the immutable features for the standard admin collections. Eventually commits the successfully created or updated database.
     * @param doOverride Can be set to _true_ to force the storage to reinstall the admin-map, even when the existing installed version of Naksha code is up-to-date (matches the code coming together with the library).
     * @param doCreate If the storage will create missing structures when opening a database. If explicitly _false_ and the database is not initialized, the storage will throw an [INITIALIZATION_FAILED][naksha.base.NakshaError.INITIALIZATION_FAILED].
     * @param doUpgrade If the storage should automatically upgrade the admin-structures (e.g. stored procedures) of a database, when necessary; otherwise the initialization does not modify the admin-structure of the database. However, it will throw an [INITIALIZATION_FAILED][naksha.base.NakshaError.INITIALIZATION_FAILED] if there are incompatibilities that prevent the usage of the found database without upgrade of admin-structures.
     * @since 3.0
     */
    internal fun init(
        doOverride: Boolean = storage.config.override ?: false,
        doCreate: Boolean = storage.config.create,
        doUpgrade: Boolean = storage.config.upgrade
    ) {
        val id = this.id.text
        val number = this.id.number
        val config = storage.config
        val config_version = config.version
        val psql_version = if (config_version != null) NakshaVersion.of(config_version) else adminVersion

        // Bootstrap the infrastructure.
        val naksha_admin = NakshaAdmin(this.id)
        val collection = naksha_admin.collections
        _pgAdmin = PgCatalog( Tuple.encodeObject(naksha_admin, collection, 1L))
        _pgAdminCatalogs = PgCollection(pgAdmin.id, Tuple.encodeObject(naksha_admin.catalogs, collection, 1L))
        _pgAdminCollections = PgCollection(pgAdmin.id, Tuple.encodeObject(naksha_admin.collections, collection, 1L))
        _pgAdminTransactions = PgCollection(pgAdmin.id, Tuple.encodeObject(naksha_admin.transactions, collection, 1L))
        _pgAdminBooks = PgCollection(pgAdmin.id, Tuple.encodeObject(naksha_admin.books, collection, 1L))

        // Switch to admin context.
        val conn = storage.newConnection(Naksha.useAdminOptions(), false)
        conn.use {
            logger.info("Start initStorage of database {}", conn.toUri())
            conn.autoCommit = false

            logger.info("Query basic database information")
            val has_naksha_version: Boolean?
            val has_naksha_storage_id: Boolean?
            val has_naksha_storage_number: Boolean?
            val admin_schema_oid: Int?
            conn.execute(
                """
WITH basics AS (SELECT 
  current_setting('block_size')::int4 AS bs, 
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
            ).fetch().use { cursor ->
                _pageSize = cursor["bs"]
                val tupleSize = pageSize - 32
                _maxTupleSize = if (tupleSize > MAX_POSTGRES_TOAST_TUPLE_TARGET) {
                    MAX_POSTGRES_TOAST_TUPLE_TARGET
                } else if (tupleSize < MIN_POSTGRES_TOAST_TUPLE_TARGET) {
                    MIN_POSTGRES_TOAST_TUPLE_TARGET
                } else {
                    tupleSize
                }
                _gzipExtension = cursor.column("gzip_oid") is Int
                // "PostgreSQL 15.5 on aarch64-unknown-linux-gnu, compiled by gcc (GCC) 7.3.1 20180712 (Red Hat 7.3.1-6), 64-bit"
                // "PostgreSQL 16.2 on aarch64-unknown-linux-gnu, compiled by gcc (GCC) 11.4.1 20230605 (Red Hat 11.4.1-2), 64-bit"
                val v: String = cursor["version"]
                val start = v.indexOf(' ') + 1
                val end = v.indexOf(' ', start)
                _postgresVersion = NakshaVersion.of(v.substring(start, end))

                admin_schema_oid = cursor.column("admin_oid") as Int?
                has_naksha_version = cursor.column("has_naksha_version") as Boolean?
                has_naksha_storage_id = cursor.column("has_naksha_storage_id") as Boolean?
                has_naksha_storage_number = cursor.column("has_naksha_storage_number") as Boolean?
            }

            if (admin_schema_oid == null) {
                if (!doCreate) throw forbidden("Creation of 'naksha~admin' needed, but forbidden by config")
                logger.info("Install Naksha admin-map in version $psql_version for storage $id / $number")
                _schemaOid = upsert_naksha_admin(conn, this.id,  psql_version, null, null)
            } else {
                _schemaOid = admin_schema_oid
                if (has_naksha_version != true) {
                    throw illegalState("The storage '$id' does have a schema 'naksha~admin', but it is broken, because function `naksha_version` is missing")
                }
                if (has_naksha_storage_id != true) {
                    throw illegalState("The storage '$id' does have a schema 'naksha~admin', but it is broken, because function `naksha_storage_id` is missing")
                }
                if (has_naksha_storage_number != true) {
                    throw illegalState("The storage '$id' does have a schema 'naksha~admin', but it is broken, because function `naksha_storage_number` is missing")
                }
                var installed_version: NakshaVersion
                var installed_storage_id: String
                var installed_storage_number: Long
                conn.execute("SELECT $ADMIN_CATALOG_QUOTED.naksha_version() AS v, $ADMIN_CATALOG_QUOTED.naksha_storage_id() AS id, $ADMIN_CATALOG_QUOTED.naksha_storage_number() AS n").fetch().use { cursor ->
                    try {
                        val v: Long = cursor["v"]
                        installed_version = NakshaVersion(v)
                        installed_storage_id = cursor["id"]
                        installed_storage_number = cursor["n"]
                    } catch (pe: Exception) {
                        throw illegalState(
                            "The storage '$id' does have an admin schema, but it is broken, because reading storage version, id, and/or number failed",
                            pe
                        )
                    }
                }
                if (installed_storage_id != id) {
                    throw NakshaException(
                        STORAGE_ID_MISMATCH,
                        "Failed to initialize storage, the 'naksha_storage_id()' is '$installed_storage_id', but was expected to be '$id'"
                    )
                }
                if (installed_storage_number != number) {
                    throw NakshaException(
                        STORAGE_ID_MISMATCH,
                        "Failed to initialize the storage, the 'naksha_storage_number()' is '$installed_storage_number', but was expected to be '$number'"
                    )
                }
                if (installed_version != psql_version) {
                    logger.info("The admin-map of '$id' is in version $installed_version, this library uses version $psql_version")
                    if (doOverride) {
                        logger.warn("Forcefully upgrade storage '$id' admin-map (current=$installed_version, new=$psql_version)")
                        upsert_naksha_admin(conn, this.id, psql_version, admin_schema_oid, installed_version)
                    } else {
                        if (installed_version > psql_version) {
                            throw illegalState("The storage '$id' is in a newer version ($installed_version) than this library ($psql_version), access denied (otherwise we risk damaging the storage)")
                        }
                        if (installed_version < minAdminVersion) {
                            if (!doUpgrade) {
                                throw illegalState("The storage '$id' is in a newer version ($installed_version) that this library ($psql_version), access denied (there is a risk damaging the storage)")
                            }
                            logger.info("Upgrade Naksha admin-map from $installed_version to $psql_version for storage $id")
                            upsert_naksha_admin(conn, this.id, psql_version, admin_schema_oid, installed_version)
                        } else if (doUpgrade){
                            logger.info("Upgrade Naksha admin-map from $installed_version to $psql_version for storage $id")
                            upsert_naksha_admin(conn, this.id, psql_version, admin_schema_oid, installed_version)
                        } else {
                            logger.info("In storage '$id' admin-map is in version $installed_version, this library is version $psql_version, but we should not upgrade the storage, and are okay working with the older version")
                        }
                    }
                } else {
                    logger.info("The admin-map of '$id' is up-to-date: $psql_version")
                }
            }
            conn.execute("SET search_path = \\\"naksha~admin\\\", topology, hint_plan, public").close()
            logger.info("Load OID of '$NAKSHA_VERSION_SEQ' from admin schema (schema-oid=$schemaOid)")
            val SQL = "SELECT oid FROM pg_class WHERE relnamespace = $schemaOid AND relname = '$NAKSHA_VERSION_SEQ'"
            conn.execute(SQL).fetch().use { cursor -> _versionSequenceOid = cursor["oid"] }
            logger.info("Storage ${config.id} / ${config.databaseNumber} initialized, version-seq-oid=$versionSequenceOid, commit")
            conn.commit()
        }
    }

    // ——————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————
    // The following methods do only low-level PostgresQL actions. They do not care about the logical part, so about the
    // corresponding administrative objects, the features representing a schema (PgCatalog), a table (PgCollection), a
    // column (PgColumn), or an index (PgIndex).
    // ——————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————

   /**
     * Return a list of the names of all database available _(not necessarily all being Naksha databases)_.
     * @return the list of the names of all databases available in the storage.
     * @since 3.0
     */
    internal fun list_databases(conn: PgConnection): List<String> {
        val list = ArrayList<String>()
        conn.execute("SELECT datname FROM pg_database WHERE datallowconn AND NOT datistemplate").use { cursor: PgCursor ->
            while (cursor.next()) {
                list.add( cursor["datname"] as String )
            }
        }
        return list
    }

    /**
     * Returns the current version-number, so the last used one.
     *
     * **Beware**: The returned version-number can be located long in the past, days or month ago.
     * @param conn the connection to use to access the database.
     * @return the next _(yet unused)_ version-number.
     * @since 3.0
     * @see Version
     */
    fun get_version(conn: PgConnection): Long {
        conn.execute("SELECT currval($1) as txn", arrayOf(versionSequenceOid)).fetch().use { cursor ->
            val seq: Long = cursor["txn"]
            return seq
        }
    }

    /**
     * Allocate a new version and return the details of it.
     *
     * ### Warning
     * The method will invoke `commit` on the given database connection, before returning the new version information.
     * @param conn the connection to use to access the database.
     * @return the allocated version.
     * @since 3.0.0
     */
    fun new_version(conn: PgConnection): PgTxn {
        while (true) {
            // Note: We read the current _(real)_ time from the server as `time`, not the transaction time, which
            //       would be the start-time of the transaction.
            val QUERY = "SELECT nextval($1) as version, (extract(epoch from clock_timestamp())*1000)::int8 as time, (extract(epoch from transaction_timestamp())*1000)::int8 as txn"
            val cursor = conn.execute(QUERY, arrayOf(versionSequenceOid)).fetch()
            cursor.use {
                var txn: Long = cursor["txn"]
                var postgresClock: Long = cursor["time"]
                var postgresInstant = Instant.fromEpochMilliseconds(postgresClock.toLong())
                var postgresDate = postgresInstant.toLocalDateTime(TimeZone.UTC)
                var year = postgresDate.year
                var month = postgresDate.month.number
                var day = postgresDate.day
                var versionNumber: Long = cursor["version"]
                var version = Version(versionNumber)
                if (version.isBehind(year, month, day)) {
                    logger.info("Transaction sequence ({}/{}/{}-{}) lags behind real date ({}/{}/{}), acquire advisory lock",
                        version.month, version.day, version.year, version.seq, month, day, year)
                    conn.execute("SELECT pg_advisory_lock($1)", arrayOf(PgUtil.TXN_LOCK_ID)).close()
                    try {
                        logger.info("Holding advisory lock now")
                        val c2 = conn.execute(QUERY, arrayOf(versionSequenceOid)).fetch()
                        c2.use {
                            txn = c2["txn"]
                            postgresClock = c2["time"]
                            postgresInstant = Instant.fromEpochMilliseconds(postgresClock.toLong())
                            postgresDate = postgresInstant.toLocalDateTime(TimeZone.UTC)
                            year = postgresDate.year
                            month = postgresDate.month.number
                            day = postgresDate.day
                            versionNumber = c2["version"]
                            version = Version(versionNumber)
                        }
                        if (version.isBehind(year, month, day)) {
                            logger.info("Transaction sequence ({}/{}/{}-{}) still lags behind real date ({}/{}/{}), rollover to next day as we now hold the advisory lock",
                                version.month, version.day, version.year, version.seq, month, day, year)
                            version = Version.auto(year, month, day, 0L, Action.VERSION)
                            versionNumber = version.number
                            conn.execute("SELECT setval($1, $2)", arrayOf(versionSequenceOid, versionNumber)).close()
                            // SELECT setval($1, $2); — Next nextval will return $2 + Increment, currval will return $2, no need to add 4!
                            // See: https://www.postgresql.org/docs/current/functions-sequence.html
                        }
                        logger.info("Release advisory lock")
                        conn.execute("SELECT pg_advisory_unlock($1)", arrayOf(PgUtil.TXN_LOCK_ID)).close()
                    } catch (e: Throwable) {
                        logger.error("Fatal exception while holding an advisory lock, terminating connection: {}", e)
                        // This must not happen, to release the advisory lock, we need to terminate the connection!
                        conn.terminate()
                        throw NakshaException(
                            EXCEPTION,
                            "Failed to increment 'txn', exception while holding advisory lock, terminating connection"
                        )
                    }
                }
                logger.debug("Final transaction version is: {} - {}/{}/{}-{}", version, version.month, version.day, version.year, version.seq)
                // Note: We know, that we only get a new transaction number before we start a transaction.
                //       Doing a commit here is necessary to avoid that we get a lock to the txn sequence!
                //       Even while sequences are normally not locked, it can happen under circumstances.
                conn.commit()
                return PgTxn(versionNumber, txn, version)
            }
        }
    }

    /**
     * Update or create the admin schema `naksha~admin`.
     *
     * This method will create the schema, tables, install the SQL code, create extensions, ... Therefore, this method can only be implemented in Java, the `PLv8` version should just throw a [UNSUPPORTED_OPERATION][naksha.base.NakshaError.UNSUPPORTED_OPERATION].
     * @param conn the connection to use to create or upgrade.
     * @param id the [Id] of the database.
     * @param psql_version the version of this library that is being installed.
     * @param schema_oid if the schema `naksha~admin` exists already and should be upgraded; `null` if we should create a new one.
     * @param installed_version if the schema `naksha~admin` exists already, the version that exists to only apply needed changes.
     * @return the `OID` of the `naksha~admin` schema created, should be the same as `schemaOid`, if given.
     * @since 3.0
     */
    protected abstract fun upsert_naksha_admin(
        conn: PgConnection,
        id: Id,
        psql_version: NakshaVersion,
        schema_oid: Int?,
        installed_version: NakshaVersion?
    ): Int

    /**
     * Execute the given SQL code, when throwing, this method will catch all exceptions and ensure that a valid [NakshaException] is thrown.
     * @param schema the schema being used.
     * @param table the table being used.
     * @param code the function that executes SQL code, it does not need to use any exception handling.
     * @return the result `code` function.
     * @throws NakshaException if the code raised any exception, converting especially SQL exceptions into correct [NakshaError] objects.
     */
    protected abstract fun <R> execute_sql_nullable(schema: String? = null, table: String? = null, code: Fn0<R?>): R?

    /**
     * Execute the given SQL code, when throwing, this method will catch all exceptions and ensure that a valid [NakshaException] is thrown.
     * @param schema the schema being used.
     * @param table the table being used.
     * @param code the function that executes SQL code, it does not need to use any exception handling.
     * @return the result `code` function.
     * @throws NakshaException if the code raised any exception, converting especially SQL exceptions into correct [NakshaError] objects.
     */
    protected abstract fun <R> execute_sql_notnull(schema: String? = null, table: String? = null, code: Fn0<R>): R

    /**
     * Execute the given SQL code, when throwing, this method will catch all exceptions and ensure that a valid [NakshaException] is thrown.
     * @param schema the schema being used.
     * @param table the table being used.
     * @param code the function that executes SQL code, it does not need to use any exception handling.
     * @throws NakshaException if the code raised any exception, converting especially SQL exceptions into correct [NakshaError] objects.
     */
    protected abstract fun execute_sql(schema: String? = null, table: String? = null, code: Fx0)

    //
    // Note: We can't ensure that another schema or table with a different name, but same number is created.
    // To prevent collisions, we need to reply upon the `naksha~catalogs` or `naksha~collections` and we need
    // to ensure that the feature-number in there is truncated to 32-bit!
    //

    /**
     * Returns the `OID` of the schema with the given name.
     * @param conn the connection to use.
     * @param name the unquoted name of the schema.
     * @return if the schema exists, the 32-bit unsigned `OID` of the schema; `-1` if no such schema exists.
     * @since 3.0
     */
    internal fun catalog_get_schema_oid(conn: PgConnection, name: String): Long {
        return execute_sql_notnull(schema = name) {
            TODO("SELECT '\"naksha~admin\"'::regnamespace::oid;")
            // Note: long oid = resultSet.getLong("oid");
            0L
        }
    }

    /**
     * Create the schema of a catalog plus the `naksha~collections` table, if it does not yet exist.
     *
     * This method works fine all catalogs, except for `naksha~admin`, where we need to create additionally the tables `naksha~catalogs`, `naksha~transactions`, and `naksha~books`.
     * @param conn the connection to use.
     * @param name the unquoted name of the schema.
     * @return the 32-bit unsigned `OID` of the created schema, returns a negative `OID` if the schema existed already.
     * @since 3.0
     */
    internal fun catalog_get_or_create_schema(conn: PgConnection, name: String): Long {
        return execute_sql_notnull(schema = name) {
            TODO("Create the schema, catalog-number collisions must be caught in naksha~catalogs")
            // conn.execute("CREATE SCHEMA IF NOT EXISTS ${quoteIdent(name)}").close()
            0L
        }
    }

    /**
     * Drops the catalog schema cascading _(recursive, so including all tables, indices, ...)_.
     * @param conn the connection to use.
     * @param name the unquoted name of the schema.
     * @since 3.0
     */
    internal fun catalog_drop_schema(conn: PgConnection, name: String) {
        execute_sql(schema = name) {
            TODO("Drop the schema cascading, if it exists")
        }
    }

    /**
     * Create the table with the specified criteria.
     * @param conn the connection to use.
     * @param schema the unquoted name of the schema in which to create the table; must exist already.
     * @param table the unquoted name of the table to create; must not exist.
     * @param columns the columns to create.
     * @param indices the indices to create.
     * @param shift the shift to be used for history.
     * @param partitions the amount of performance partitions to create, must be a value between 1 and 256.
     * @throws NakshaException if the creation failed.
     * @since 3.0
     */
    internal fun collection_create_table_if_not_exits(
        conn: PgConnection,
        schema: String,
        table: String,
        columns: Array<PgColumn>,
        indices: Array<PgIndex>,
        shift: Int,
        partitions: Int
    ) {
        execute_sql(schema = schema, table = table) {
            val quotedSchema = quoteIdent(schema)
            val quotedTable = quoteIdent(table)
            val CREATE_DDL = """CREATE TABLE IF NOT EXISTS $quotedTable (
  fn bigint NOT NULL,
  version bigint NOT NULL,
  nv bigint NOT NULL DEFAULT 9007199254740991,
  PRIMARY KEY (nv DESC, fn DESC, version DESC)
) PARTITION BY RANGE (nv);"""
            // TODO: Create the database table, use pg_partman.
            //       Default contains head, so needs an additional check for `nv` being 9007199254740991
            //       Now, each partition should be partitioned again by `fn`
            //       As always only need `fn`, `version`, and `nv`, we need them to be in any index for index-only scans!
        }
    }

    /**
     * Drops the table cascading, if it exists. Does not fail, if the table does not exist.
     * @param conn the connection to use.
     * @param schema the unquoted name of the schema in which to drop the table; must exist already.
     * @param table the unquoted name of the table to drop; must not exist.
     * @since 3.0
     */
    internal fun collection_drop_if_exists(conn: PgConnection, schema: String, table: String) {
        execute_sql(schema = schema, table = table) {

        }
    }

    // TODO: When executing upsert, update or delete, we need to verify the result of the purge query.
    //       It tells us which state each row had before execution. When the state is not the expected one,
    //       we will actually do a rollback and return back the error. This avoids all kind of
    //       conditional queries, everything just relies upon an index-only scan of the primary-key index,
    //       so above (nv, fn, version). It will be faster when being successful (that is what we hope for
    //       in most cases), while we will be slower when a real conflict arises (which we expect to be rare).
    internal fun rows_insert(conn: PgConnection, schema: String, table: String, rows: PgRows) {
        execute_sql(schema = schema, table = table) {

        }
    }
    internal fun rows_upsert(conn: PgConnection, schema: String, table: String, rows: PgRows) {}
    internal fun rows_update(conn: PgConnection, schema: String, table: String, rows: PgRows) {}
    internal fun rows_purge(conn: PgConnection, schema: String, table: String, ids: Array<Id>) {}
    // TODO: Executing a query generally should now be easier.
    //       We need the WHERE builder, but can we simplify this, so we can test better and just use from writer?
    internal fun rows_query(conn: PgConnection, schema: String, table: String, query: PgQuery) {}
    internal fun rows_delete_keep_old_state(conn: PgConnection, schema: String, table: String, ids: Array<Id>?) {}
    // TODO: Future delete mechanisms: first for storage-api, second as new standard way, because it is much faster and sufficient
    internal fun rows_delete_with_new_state(conn: PgConnection, schema: String, table: String, rows: PgRows) {}
    internal fun rows_delete_without_state(conn: PgConnection, schema: String, table: String) {}
}

/**
 * An array that contains `0` for characters being valid as first character of a name, `1` for characters valid anywhere and `2` for invalid characters.
 * @since 3.0
 */
private val validChars = IntArray(128) {
    when (it) {
        in 'a'.code..'z'.code -> 0
        in '0'.code..'9'.code -> 1
        '_'.code -> 1
        else -> 2
    }
}

/**
 * Tests if the given character is a valid first character as database name.
 * @since 3.0
 */
private fun isValidFirstChar(c: Char): Boolean = c.code < validChars.size && validChars[c.code] == 0
/**
 * Tests if the given character is a valid character as database name.
 * @since 3.0
 */
private fun isValidChar(c: Char): Boolean = c.code < 256 && validChars[c.code] < 2

/**
 * Tests if the given string only contains valid character as database name, and does not start with the internal prefix `naksha_`.
 * @since 3.0
 */
private fun isValidName(name: String, maxLength: Int): Boolean {
    if (name.isEmpty()) return false
    if (name.length > maxLength) return false
    if (!isValidFirstChar(name[0])) return false
    for (i in 1 until name.length) if (!isValidChar(name[i])) return false
    return !name.startsWith("naksha_")
}

private fun int_to_string_with_dollar(prefix: String, value: Int): String {
    val sb = StringBuilder()
    sb.append(prefix)
    sb.append(value)
    if (sb[prefix.length] == '-') sb[prefix.length] = '$'
    return sb.toString()
}

/**
 * Extract the `lib-psql` name of the database from the given identifier.
 *
 * Technically, we match the identifier against `[a-z][a-z0-9_]{41}`, when it matches, we use this name; otherwise this method throws an exception.
 * @return the identifier to be used in the database.
 * @since 3.0
 * @throws NakshaException with error [ILLEGAL_ARGUMENT][NakshaError.ILLEGAL_ARGUMENT], if the given database name is invalid.
 */
internal fun Id.databaseName(): String {
    if (isValidName(this.text, 42)) return this.text
    throw illegalId(this.text)
}

/**
 * Extract the `lib-psql` name of the catalog from the given identifier.
 *
 * Technically, we match the identifier against `[a-z][a-z0-9_]{24}`, when it matches, we use this name, otherwise we use `s_{id.intValue}`, but replace the minus with a dollar, e.g. `s_-12345` becomes `s_$12345`.
 * @return the identifier to be used in the database.
 * @since 3.0
 */
internal fun Id.catalogName(): String {
    if (isValidName(this.text, 25)) return this.text
    return int_to_string_with_dollar("s_", this.intValue)
}

/**
 * Extract the `lib-psql` name of the collection from the given identifier.
 *
 * Technically, we match the identifier against `[a-z][a-z0-9_]{24}`, when it matches, we use this name, otherwise we use `t_{id.intValue}`, but replace the minus with a dollar, e.g. `t_-12345` becomes `t_$12345`.
 * @return the identifier to be used in the database.
 * @since 3.0
 */
internal fun Id.collectionName(): String {
    if (isValidName(this.text, 25)) return this.text
    return int_to_string_with_dollar("t_", this.intValue)
}

/**
 * Extract the `lib-psql` name of the database from the given identifier.
 *
 * Technically, we match the identifier against `[a-z][a-z0-9_]{41}`, when it matches, we use this name, otherwise we use `c_{id.intValue}`, but replace the minus with a dollar, e.g. `c_-12345` becomes `c_$12345`.
 * @return the identifier to be used in the database.
 * @since 3.0
 */
internal fun Id.columnName(): String {
    if (isValidName(this.text, 42)) return this.text
    return int_to_string_with_dollar("c_", this.intValue)
}

/**
 * Returns the SQL quoted name of the identifier. This method will only quote when necessary.
 * @return the identifier to be used in the database.
 * @since 3.0
 */
internal fun Id.quotedName(): String {
    return quoteIdent(text)
}
