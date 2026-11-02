package naksha.psql

import naksha.base.Id.IdCompanion.ADMIN_CATALOG_QUOTED

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
import naksha.model.NewTuple
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
        _pgAdmin = PgCatalog( Tuple.encodeObject(naksha_admin, collection, 1L).tuple)
        _pgAdminCatalogs = PgCollection(pgAdmin.id, Tuple.encodeObject(naksha_admin.catalogs, collection, 1L).tuple)
        _pgAdminCollections = PgCollection(pgAdmin.id, Tuple.encodeObject(naksha_admin.collections, collection, 1L).tuple)
        _pgAdminTransactions = PgCollection(pgAdmin.id, Tuple.encodeObject(naksha_admin.transactions, collection, 1L).tuple)
        _pgAdminBooks = PgCollection(pgAdmin.id, Tuple.encodeObject(naksha_admin.books, collection, 1L).tuple)

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
     *
     * Looks up the physical PostgreSQL namespace without creating it or loading Naksha metadata.
     * The unsigned PostgreSQL OID is represented as a positive [Long].
     * @param conn the caller-owned connection; this method does not commit or close it.
     * @param name the unquoted name of the schema.
     * @return if the schema exists, the 32-bit unsigned `OID` of the schema; `-1` if no such schema exists.
     * @since 3.0
     */
    internal fun catalog_get_schema_oid(conn: PgConnection, name: String): Long {
        return execute_sql_notnull(schema = name) {
            conn.execute("SELECT oid::text AS oid FROM pg_catalog.pg_namespace WHERE nspname = $1", arrayOf(name)).use { cursor ->
                if (cursor.next()) cursor.get<String>("oid").toLong() else -1L
            }
        }
    }

    /**
     * Create the physical schema of a catalog, if it does not yet exist.
     *
     * Internal tables are created separately through [collection_create_table_if_not_exists].
     * Identifier collisions are checked by the caller's administrative writes.
     * An existing schema is reused without modifying its contents. Creation participates in the
     * caller's transaction; the signed return value distinguishes creation from reuse.
     * Cooperating creators serialize by physical schema name using a transaction-scoped advisory lock. Requires an open caller transaction; READ COMMITTED permits reuse after a concurrent commit. External creators or stale higher-isolation snapshots can still cause a database error, which is propagated without recovery here.
     * @param conn the caller-owned connection; this method does not commit or close it.
     * @param name the unquoted name of the schema.
     * @param originalName the original logical catalog name to retain as a schema comment when created. Existing schema comments are not modified.
     * @return the 32-bit unsigned `OID` of the created schema, returns a negative `OID` if the schema existed already.
     * @since 3.0
     */
    internal fun catalog_get_or_create_schema(conn: PgConnection, name: String, originalName: String): Long {
        return execute_sql_notnull(schema = name) {
            if (name.isEmpty() || '\u0000' in name || name.encodeToByteArray().size > 63) {
                throw illegalId("Physical schema name must contain 1..63 UTF-8 bytes without NUL")
            }
            conn.execute("SELECT pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended($1, 0))",
                arrayOf("naksha:schema:$name")).close()
            val existing = catalog_get_schema_oid(conn, name)
            if (existing > 0) -existing else {
                val ident = quoteIdent(name)
                conn.execute("CREATE SCHEMA $ident").close()
                conn.execute("COMMENT ON SCHEMA $ident IS ${PgUtil.quoteLiteral(originalName)}").close()
                val oid = catalog_get_schema_oid(conn, name)
                if (oid <= 0) throw internalError("Created schema has no OID")
                oid
            }
        }
    }

    /**
     * Drops the catalog schema cascading _(recursive, so including all tables, indices, ...)_.
     *
     * An absent schema is a successful no-op. Physical partition-management configuration associated
     * with the schema must also be removed. The caller separately updates logical catalog metadata.
     * @param conn the caller-owned connection; this method does not commit or close it.
     * @param name the unquoted name of the schema.
     * @since 3.0
     */
    internal fun catalog_drop_schema(conn: PgConnection, name: String) {
        execute_sql(schema = name) {
            TODO("Drop the schema cascading, if it exists")
        }
    }

    /**
     * Create a collection's physical root table, partitions, and indexes if the root does not exist.
     *
     * The root routes rows by `nv`; current rows use [Version.HEAD] and history rows use finite
     * successor versions. The default partition is restricted to HEAD. When performance partitioning
     * is enabled, children route physical `fn` values into range leaves. pg_partman owns both history
     * and performance partition provisioning and maintenance. Naksha configures it rather than
     * selecting or manually creating individual partitions. Creation must finish pg_partman's
     * initial setup before accepting writes.
     * An existing root is left unchanged; this method does not reconcile or upgrade its schema.
     *
     * @param conn the caller-owned connection; DDL is part of its transaction and is not committed here.
     * @param schema the unquoted name of the schema in which to create the table; must exist already.
     * @param table the unquoted name of the root table; creation is skipped if it exists.
     * @param columns the physical column definitions, including mandatory `fn`, `version`, and `nv`.
     * @param indices custom index descriptors; mandatory keys are created intrinsically.
     * @param options create-only history, range coverage, durability, tablespace, and TOAST settings.
     * History-OFF creates only HEAD and does not register history with pg_partman. With history ON,
     * configure pg_partman's interval, backward/forward coverage, retention, and fn subpartitioning.
     * Do not derive a starting partition from a caller-supplied transaction version. Validate the
     * configuration before issuing DDL; do not infer options from cached logical objects.
     * @throws NakshaException if the creation failed.
     * @since 3.0
     */
    internal fun collection_create_table_if_not_exists(
        conn: PgConnection,
        schema: String,
        table: String,
        columns: Array<PgColumn>,
        indices: Array<PgIndex>,
        options: PgCollectionCreateOptions
    ) {
        execute_sql(schema = schema, table = table) {
            val quotedSchema = quoteIdent(schema)
            val quotedTable = quoteIdent(table)
            val CREATE_DDL = """CREATE TABLE IF NOT EXISTS $quotedSchema.$quotedTable (
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
     *
     * Removes the collection root and dependent HEAD/history leaves and indexes, together with its
     * pg_partman registration and owned templates. Logical collection metadata is the caller's concern.
     * @param conn the caller-owned connection; the drop is not committed here.
     * @param schema the unquoted name of the schema in which to drop the table; must exist already.
     * @param table the unquoted name of the root table to drop; may be absent.
     * @since 3.0
     */
    internal fun collection_drop_if_exists(conn: PgConnection, schema: String, table: String) {
        execute_sql(schema = schema, table = table) {

        }
    }

    /**
     * Create a custom root index, including the configuration needed by future partitions.
     * [schema] and [table] are unquoted physical names; [index] is a descriptor, not a table object.
     * Mandatory indexes are intrinsic to collection creation.
     *
     * Creates/attaches the index for existing children and updates the supported partition-management
     * configuration so future children receive it. Index method and INCLUDE rules must be validated
     * before issuing DDL; the per-method contract is agreed separately.
     * @param conn the caller-owned connection; this method does not commit or close it.
     * @param schema the unquoted physical schema containing the root.
     * @param table the unquoted physical collection root name, not a partition name.
     * @param index the custom key columns/order, includes, and conditional predicate descriptor.
     * @throws NakshaException if index validation or DDL fails.
     * @since 3.0
     */
    internal fun collection_create_index(conn: PgConnection, schema: String, table: String, index: PgIndex): Unit =
        TODO("db-04: create root index and configure future partitions")

    /**
     * Drop a custom root index, its attached child indexes, and its future-partition configuration.
     * An absent index is a successful no-op. This operation does not remove mandatory collection keys.
     * @param conn the caller-owned connection; this method does not commit or close it.
     * @param schema the unquoted physical schema containing the root.
     * @param table the unquoted physical collection root name.
     * @param index the descriptor identifying the custom index to remove.
     * @throws NakshaException if the DDL fails.
     * @since 3.0
     */
    internal fun collection_drop_index(conn: PgConnection, schema: String, table: String, index: PgIndex): Unit =
        TODO("db-04: drop root index and update future partitions")

    // Row-operation boundary (ai/NAKSHA-33.md, db-01-c/db-01-g):
    // - schema/table are the catalog/collection [Id] of the target; the database identity is this
    //   instance's [id]. conn belongs to the caller; these functions never commit or roll back.
    // - Inputs are immutable: new-state operations take [NewTuple] wrappers, selection operations
    //   take feature [Id]s or [Guid]s. Cached tuples and inputs are never mutated.
    // - storeHistory selects archival (change the predecessor `nv`) versus current-only mutation.
    // - version is the caller's transaction version; delete/purge need it to archive or create states.
    // - Result order is the input order (with the agreed DELETE omissions and PURGE null positions);
    //   SQL row order is never assumed.
    // - Failures construct a [NakshaError], wrap it in [NakshaException], and throw; there is no
    //   nullable-error return. An exception may follow partial mutation: the caller rolls back.
    // - Validate target identity with id.number, schema.intValue, table.intValue and featureNumber;
    //   never reconstruct Ids from text for validation. Conflicts preserve full versions in errors.
    // - Mutations close internal plans/cursors, never the connection. Preserve caller-controlled
    //   SQL logging and planning-only EXPLAIN; EXPLAIN ANALYZE would execute the mutation.

    /**
     * Insert new HEAD states. Every input [NewTuple] must carry a `null` predecessor; a non-null
     * predecessor is an illegal argument, rejected before any mutation.
     *
     * For each feature, an existing DELETE HEAD is purged first (archived by changing its `nv` when
     * [storeHistory] is enabled, otherwise removed) before insertion. A live CREATE/UPDATE HEAD is an
     * error. New states are inserted through the root with `nv = Version.HEAD.number` and CREATE
     * action. Returns the supplied tuple states in input order.
     *
     * Preconditions: the caller has opened a savepoint, validated transaction-wide feature
     * uniqueness, and sorted inputs into the deterministic locking order. No commit/rollback here.
     * @param conn the caller-owned mutable connection; it is not closed by this method.
     * @param schema the catalog [Id] of the target collection.
     * @param table the collection [Id] of the target.
     * @param tuples the fully encoded new states; each predecessor must be `null`.
     * @param storeHistory whether purged DELETE states are archived instead of discarded.
     * @return the supplied tuple states, in input order.
     * @throws NakshaException wrapping a [NakshaError] on any failure.
     * @since 3.0
     */
    internal fun rows_insert(
        conn: PgConnection, schema: Id, table: Id, tuples: List<NewTuple>, storeHistory: Boolean
    ): List<Tuple> = TODO("db-06: insert rows through the root")

    /**
     * Replace current HEAD states or insert missing ones. A supplied predecessor is validated for
     * target identity first and never conditions phase 1. Phase 1 inserts into an absent HEAD or
     * replaces a DELETE HEAD. Only a live CREATE/UPDATE HEAD selects phase 2, which updates
     * unconditionally or, when a predecessor is supplied, conditionally on its masked version.
     *
     * Returns, per input, a [NewTuple] whose `previousTupleNumber` is set when UPDATE was performed
     * and `null` when INSERT was performed (including re-creation after a DELETE HEAD), in input order.
     *
     * Preconditions: the caller has opened a savepoint, validated transaction-wide feature
     * uniqueness, and sorted inputs into the deterministic locking order. No commit/rollback here.
     * @param conn the caller-owned mutable connection; it is not closed by this method.
     * @param schema the catalog [Id] of the target collection.
     * @param table the collection [Id] of the target.
     * @param tuples the fully encoded new states with their expected predecessors.
     * @param storeHistory whether replaced HEAD states are archived rather than discarded.
     * @return the final states with predecessor set only for the UPDATE branch, in input order.
     * @throws NakshaException wrapping a [NakshaError] on any failure.
     * @since 3.0
     */
    internal fun rows_upsert(
        conn: PgConnection, schema: Id, table: Id, tuples: List<NewTuple>, storeHistory: Boolean
    ): List<NewTuple> = TODO("db-07: upsert rows through the root")

    /**
     * Replace existing live HEAD states. An input [NewTuple] with a non-null predecessor requests an
     * atomic UPDATE conditioned on the predecessor's version (low two action bits masked); `null`
     * selects an unconditional UPDATE. A missing HEAD, or a mismatching/DELETE predecessor, is a
     * structured error.
     *
     * With [storeHistory] enabled the old HEAD is archived by changing its `nv`; otherwise the HEAD
     * row is updated in place. Returns, per input, a [NewTuple] whose `previousTupleNumber` is the
     * actual replaced live HEAD and whose [NewTuple.tuple] is the supplied new state, in input order.
     *
     * Preconditions: the caller has opened a savepoint, validated transaction-wide feature
     * uniqueness, and sorted inputs into the deterministic locking order. No commit/rollback here.
     * @param conn the caller-owned mutable connection; no commit, rollback, or close is performed here.
     * @param schema the catalog [Id] of the target collection.
     * @param table the collection [Id] of the target.
     * @param tuples the fully encoded new states with their expected predecessors.
     * @param storeHistory whether replaced HEAD states are archived rather than overwritten.
     * @return the new states with the actual replaced predecessor, in input order.
     * @throws NakshaException wrapping a [NakshaError] on any failure.
     * @since 3.0
     */
    internal fun rows_update(
        conn: PgConnection, schema: Id, table: Id, tuples: List<NewTuple>, storeHistory: Boolean
    ): List<NewTuple> = TODO("db-07: update rows through the root")

    /**
     * Purge DELETE HEAD states for the given features, returning one nullable [Guid] per input in
     * exactly the input order. Only HEAD states in DELETE action are purged: with [storeHistory]
     * enabled the tombstone is archived by changing its `nv` to [version], otherwise it is removed.
     * A live CREATE/UPDATE HEAD is left untouched and its current HEAD GUID is returned; a feature
     * with no HEAD yields `null`. This is a positional contract: `result[i]` describes
     * `featureIds[i]` and the list size equals `featureIds.size`.
     *
     * Preconditions: the caller has opened a savepoint, validated transaction-wide feature
     * uniqueness, and sorted inputs into the deterministic locking order. No commit/rollback here.
     * @param conn the caller-owned mutable connection; no commit, rollback, or close is performed here.
     * @param schema the catalog [Id] of the target collection.
     * @param table the collection [Id] of the target.
     * @param version the caller's transaction version used to archive purged tombstones.
     * @param storeHistory whether purged tombstones are archived instead of removed.
     * @param featureIds the features to purge, in the order results must be returned.
     * @return one GUID (the purged tombstone or the unchanged live HEAD) or `null` (no HEAD), per input.
     * @throws NakshaException wrapping a [NakshaError] on a genuine database failure.
     * @since 3.0
     */
    internal fun rows_purge(
        conn: PgConnection, schema: Id, table: Id, version: Long, storeHistory: Boolean, featureIds: List<Id>
    ): List<Guid?> = TODO("db-08: purge selected rows through the root")

    /**
     * Delete live HEAD states by feature [Id] without returning their payload. A missing HEAD is
     * omitted from the result; an already-deleted HEAD is returned as its existing GUID without being
     * rewritten. The generated tombstone has DELETE action and `nv = Version.HEAD.number`; with
     * [storeHistory] enabled the prior live state is archived. By default the old payload is retained
     * in the tombstone; [emptyTombstone] makes every non-key column SQL NULL.
     *
     * Results preserve input order with missing features omitted. Preconditions: the caller has opened
     * a savepoint, validated transaction-wide feature uniqueness, and sorted inputs into the
     * deterministic locking order. No commit/rollback here.
     * @param conn the caller-owned mutable connection; it is not closed by this method.
     * @param schema the catalog [Id] of the target collection.
     * @param table the collection [Id] of the target.
     * @param version the caller's transaction version for the tombstone and any archival.
     * @param storeHistory whether prior live states are archived before creating the tombstone.
     * @param featureIds the features to delete.
     * @param emptyTombstone whether to null all non-key payload columns in the new tombstone.
     * @return the GUIDs of the deleted (or already-deleted) features, in input order, omitting missing.
     * @throws NakshaException wrapping a [NakshaError] on any failure.
     * @since 3.0
     */
    internal fun rows_delete_by_id_without_result(
        conn: PgConnection, schema: Id, table: Id, version: Long, storeHistory: Boolean,
        featureIds: List<Id>, emptyTombstone: Boolean = false
    ): List<Guid> = TODO("db-08: delete selected rows by id, returning GUIDs")

    /**
     * Delete live HEAD states by feature [Id], returning the deleted states. A missing HEAD is omitted;
     * an already-deleted HEAD is returned unchanged with `previousTupleNumber = null`. The generated
     * tombstone retains the old payload; with [storeHistory] enabled the prior live state is archived.
     *
     * Results preserve input order with missing features omitted. Preconditions: the caller has opened
     * a savepoint, validated transaction-wide feature uniqueness, and sorted inputs into the
     * deterministic locking order. No commit/rollback here.
     * @param conn the caller-owned mutable connection; it is not closed by this method.
     * @param schema the catalog [Id] of the target collection.
     * @param table the collection [Id] of the target.
     * @param version the caller's transaction version for the tombstone and any archival.
     * @param storeHistory whether prior live states are archived before creating the tombstone.
     * @param featureIds the features to delete.
     * @return the created tombstones (or existing deleted states) with predecessor references, in input order.
     * @throws NakshaException wrapping a [NakshaError] on any failure.
     * @since 3.0
     */
    internal fun rows_delete_by_id_with_result(
        conn: PgConnection, schema: Id, table: Id, version: Long, storeHistory: Boolean, featureIds: List<Id>
    ): List<NewTuple> = TODO("db-08: delete selected rows by id, returning states")

    /**
     * Conditionally delete live HEAD states by expected [Guid], returning GUIDs without payload. For a
     * live HEAD the expected version must match (low two action bits masked); a mismatch or missing
     * state is an error. An already-DELETE HEAD is accepted and returned even when its tuple number
     * differs from the expectation, without being rewritten. Mirrors
     * [rows_delete_by_id_without_result], including [emptyTombstone], but keys selection on GUIDs.
     *
     * Results preserve input order with missing features omitted. Preconditions: the caller has opened
     * a savepoint, validated transaction-wide feature uniqueness, and sorted inputs into the
     * deterministic locking order. No commit/rollback here.
     * @param conn the caller-owned mutable connection; it is not closed by this method.
     * @param schema the catalog [Id] of the target collection.
     * @param table the collection [Id] of the target.
     * @param version the caller's transaction version for the tombstone and any archival.
     * @param storeHistory whether prior live states are archived before creating the tombstone.
     * @param guids the expected states to delete.
     * @param emptyTombstone whether to null all non-key payload columns in the new tombstone.
     * @return the GUIDs of the deleted (or already-deleted) features, in input order, omitting missing.
     * @throws NakshaException wrapping a [NakshaError] on a mismatch or database failure.
     * @since 3.0
     */
    internal fun rows_delete_by_guid_without_result(
        conn: PgConnection, schema: Id, table: Id, version: Long, storeHistory: Boolean,
        guids: List<Guid>, emptyTombstone: Boolean = false
    ): List<Guid> = TODO("db-08: delete selected rows by guid, returning GUIDs")

    /**
     * Conditionally delete live HEAD states by expected [Guid], returning the deleted states. For a
     * live HEAD the expected version must match (low two action bits masked); a mismatch or missing
     * state is an error. An already-DELETE HEAD is returned with `previousTupleNumber = null` even
     * when its tuple number differs from the expectation. Mirrors [rows_delete_by_id_with_result],
     * but keys selection on GUIDs.
     *
     * Results preserve input order with missing features omitted. Preconditions: the caller has opened
     * a savepoint, validated transaction-wide feature uniqueness, and sorted inputs into the
     * deterministic locking order. No commit/rollback here.
     * @param conn the caller-owned mutable connection; it is not closed by this method.
     * @param schema the catalog [Id] of the target collection.
     * @param table the collection [Id] of the target.
     * @param version the caller's transaction version for the tombstone and any archival.
     * @param storeHistory whether prior live states are archived before creating the tombstone.
     * @param guids the expected states to delete.
     * @return the created tombstones (or existing deleted states) with predecessor references, in input order.
     * @throws NakshaException wrapping a [NakshaError] on a mismatch or database failure.
     * @since 3.0
     */
    internal fun rows_delete_by_guid_with_result(
        conn: PgConnection, schema: Id, table: Id, version: Long, storeHistory: Boolean, guids: List<Guid>
    ): List<NewTuple> = TODO("db-08: delete selected rows by guid, returning states")

    /**
     * Write explicit HEAD tombstones. Each input [NewTuple] must encode DELETE action. Without a
     * supplied predecessor the existing HEAD must be live (CREATE/UPDATE); with a predecessor it must
     * be exactly that HEAD state (action-sensitive comparison). A missing or mismatching HEAD is an
     * error; an existing unrelated tombstone is never reported as a successful creation. With
     * [storeHistory] enabled the replaced live state is archived.
     *
     * Preconditions: the caller has opened a savepoint, validated transaction-wide feature
     * uniqueness, and sorted inputs into the deterministic locking order. No commit/rollback here.
     * @param conn the caller-owned mutable connection; it is not closed by this method.
     * @param schema the catalog [Id] of the target collection.
     * @param table the collection [Id] of the target.
     * @param version the caller's transaction version for the tombstone and any archival.
     * @param storeHistory whether the replaced live state is archived.
     * @param tuples the explicit tombstone states to create, with their expected predecessors.
     * @return the created tombstones with predecessor references, in input order.
     * @throws NakshaException wrapping a [NakshaError] on any failure.
     * @since 3.0
     */
    internal fun rows_delete_with_new_state(
        conn: PgConnection, schema: Id, table: Id, version: Long, storeHistory: Boolean, tuples: List<NewTuple>
    ): List<NewTuple> = TODO("db-08: write explicit tombstone states")

    /**
     * Prepare a physical SQL query without depending on the logical [PgQuery] wrapper.
     * The caller binds values and closes the returned plan and its cursors.
     * [schema] and [table] supply error context; [sql] contains the qualified query.
     * This only prepares the query: it does not execute it, consume rows, or modify an input batch.
     * @param conn the caller-owned connection on which the returned plan will execute.
     * @param schema the unquoted physical schema for error reporting.
     * @param table the unquoted physical root table for error reporting.
     * @param sql the query text, with qualified/quoted relation names and parameter placeholders.
     * @param argTypes the PostgreSQL parameter type names in placeholder order.
     * @return the prepared plan; its lifetime must not exceed the connection's lifetime.
     * @throws NakshaException if preparation fails; unlike mutations, this method returns a plan,
     * not an error status.
     * @since 3.0
     */
    internal fun rows_query(
        conn: PgConnection, schema: String, table: String, sql: String, argTypes: Array<String>
    ): PgPlan = TODO("db-09: prepare physical row query")
}

/**
 * Extract the `lib-psql` name of the database from the given identifier.
 *
 * Preserve ordinary legal text up to 42 UTF-8 bytes, including UNSAFE text requiring SQL quoting. Otherwise generate an internal `d$` alias. INTERNAL and RESERVED text follows the normal alias acceptance policy.
 * @return the identifier to be used in the database.
 * @since 3.0
 * @throws NakshaException with error [ILLEGAL_ID][NakshaError.ILLEGAL_ID], if the identifier text is illegal.
 */
internal fun Id.databaseName(): String {
    return textAlias(IdTextType.UNSAFE, 42, "d")
}

/**
 * Extract the `lib-psql` name of the catalog from the given identifier.
 *
 * Preserve URL-safe names up to 25 UTF-8 bytes and reserved built-ins up to 32 bytes. Other legal names use an internal `s$` alias, with underscore for a negative integer.
 * @return the identifier to be used in the database.
 * @since 3.0
 */
internal fun Id.catalogName(): String {
    return if (textType === IdTextType.RESERVED) text
    else textAlias(IdTextType.URL_SAFE, 25, "s")
}

/**
 * Extract the `lib-psql` name of the collection from the given identifier.
 *
 * Preserve URL-safe names up to 25 UTF-8 bytes and reserved built-ins up to 32 bytes. Other legal names use an internal `t$` alias, with underscore for a negative integer. The budget reserves space for generated children and indexes.
 * @return the identifier to be used in the database.
 * @since 3.0
 */
internal fun Id.collectionName(): String {
    return if (textType === IdTextType.RESERVED) text
    else textAlias(IdTextType.URL_SAFE, 25, "t")
}

/**
 * Extract the `lib-psql` name of the database from the given identifier.
 *
 * Preserve SAFE names up to 42 UTF-8 bytes; otherwise generate an internal `m$` alias. Logical member names are not modified.
 * @return the identifier to be used in the database.
 * @since 3.0
 */
internal fun Id.columnName(): String {
    return textAlias(IdTextType.SAFE, 42, "m")
}

/**
 * Returns the SQL quoted name of the identifier. This method will only quote when necessary.
 * @return the identifier to be used in the database.
 * @since 3.0
 */
internal fun Id.quotedName(): String {
    return quoteIdent(text)
}
