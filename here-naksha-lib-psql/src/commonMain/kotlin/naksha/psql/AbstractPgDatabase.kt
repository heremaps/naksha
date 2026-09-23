package naksha.psql

import naksha.base.ADMIN_CATALOG_QUOTED
import naksha.base.Id
import naksha.base.NakshaError.NakshaErrorCompanion.STORAGE_ID_MISMATCH
import naksha.base.NakshaException
import naksha.base.Platform.PlatformCompanion.logger
import naksha.base.TupleNumber
import naksha.base.forbidden
import naksha.base.illegalState
import naksha.base.notInitialized
import naksha.model.Naksha
import naksha.model.NakshaVersion
import naksha.model.objects.Index
import naksha.psql.PgUtil.PgUtilCompanion.quoteIdent
import kotlin.jvm.JvmField
import kotlin.jvm.JvmName

/**
 * A wrapper around a `NakshaDatabase` _(which does not yet exist)_.
 *
 * A database is a logical entity in Naksha, the same as the catalog, the collection and the feature. Only the [Tuple][naksha.model.Tuple] is a real physical entity. The concept is that a database can be hosted by multiple storages, but only one storage should be considered the source of truth. All others are treated like replicas or snapshots. They can use different storage layout and indices, optimized for other purposes.
 *
 * To keep the code more maintainable, we move all DDL _(data definition language)_ and SQL code into this class. So, mutation of the PostgresQL schema, tables, indices, and inserting, updating, delete rows. This class should deal with the physical reality of PostgresQL, not with the logical level of the Naksha storage concept. Therefore, this class works with `schema`, `table`, `partition` and `rows`. It should not have any knowledge about the logical part, like that a `row` represents a [Tuple][naksha.model.Tuple], it must not know about books or JBON encoding.
 *
 * Its purpose is to manage low level database entities like `schema`, `table`, `partition` and `rows`. It is fixed to the Naksha needs, so it assumes a primary key above (`nv`, `fn`, `version`). It is strictly tied up to the Naksha storage model. So it requires the three columns `nv`, `fn`, and `version` and it allows arbitrary `PgColumn`. It will understand how the history works, without making any assumptions about the content of a row, so it basically manages rows, each having a unique address, linked to partitioned history.
 * @since 3.0
 */
abstract class AbstractPgDatabase(
    /**
     * The storage in which the database is located.
     * @since 3.0
     */
    @JvmField
    val storage: PgStorage,

    /**
     * The instance object to use to acquire connections to access the database.
     * @since 3.0
     */
    @JvmField
    val instance: PgInstance,

    /**
     * The identifier of the database.
     *
     * This is installed into the admin-schema `naksha~admin` in the functions `naksha_storage_id()` and `naksha_storage_number()`.
     * @since 3.0
     */
    @JvmField
    val id: Id
) {

    /**
     * The physical name of the database in the PostgresQL cluster.
     * @since 3.0
     * @see PgInstance.database
     */
    @get:JvmName("name")
    val name: String
        get() = instance.database

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
    val adminCatalog: PgCatalog
        get() = _adminCatalog ?: throw notInitialized("PgDatabase")
    private var _adminCatalog: PgCatalog? = null

    /**
     * The collections' collection _(`naksha~collections`)_ in the admin-schema (`naksha~admin`).
     * @since 3.0.0
     */
    val adminCollections: PgCollection
        get() = _adminCollections ?: throw notInitialized("PgDatabase")
    private var _adminCollections: PgCollection? = null

    /**
     * The transactions' collection _(`naksha~transactions`)_ in the admin-schema (`naksha~admin`).
     * @since 3.0.0
     */
    val adminTransactions: PgCollection
        get() = _adminTransactions ?: throw notInitialized("PgDatabase")
    private var _adminTransactions: PgCollection? = null

    /**
     * The catalogs' collection _(`naksha~catalogs`)_ in the admin-schema (`naksha~admin`).
     * @since 3.0.0
     */
    val adminCatalogs: PgCollection
        get() = _adminCatalogs ?: throw notInitialized("PgDatabase")
    private var _adminCatalogs: PgCollection? = null

    /**
     * The books' collection _(`naksha~books`)_ in the admin-schema (`naksha~admin`).
     * @since 3.0.0
     */
    val adminBooks: PgCollection
        get() = _adminBooks ?: throw notInitialized("PgDatabase")
    private var _adminBooks: PgCollection? = null

    /**
     * Opens the database.
     *
     * Depending on parameters, installs SQL code, creates extensions, create `naksha~admin` schema, create admin collections, inserting the immutable features for the standard admin collections. Eventually commits the successfully created or updated database.
     * @param conn the connection to use for the opening and optional initialization.
     * @param adminCatalog if the database is not yet initialized, the `naksha~admin` schema.
     * @param adminCollections if the database is not yet initialized, the `naksha~collections` collection in `naksha~admin` schema.
     * @param adminTransactions if the database is not yet initialized, the `naksha~transactions` collection in `naksha~admin` schema.
     * @param adminCatalogs if the database is not yet initialized, the `naksha~catalogs` collection in `naksha~admin` schema.
     * @param adminBooks if the database is not yet initialized, the `naksha~books` collection in `naksha~admin` schema.
     * @since 3.0
     */
    fun open(
        conn: PgConnection,
        adminCatalog: PgCatalog,
        adminCollections: PgCollection,
        adminTransactions: PgCollection,
        adminCatalogs: PgCollection,
        adminBooks: PgCollection,
        doOverride: Boolean = storage.config.override ?: false,
        doCreate: Boolean = storage.config.create,
        doUpgrade: Boolean = storage.config.upgrade
    ) {
        val id = this.id.text
        val number = this.id.number
        val config = storage.config
        val config_version = config.version
        val psql_version = if (config_version != null) NakshaVersion.of(config_version) else adminVersion

        // Switch to admin context.
        val conn = storage.newConnection(Naksha.adminOptions, false)
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

            // This only creates the logical structure, no database access is yet done!
            // Beware: We need to do this here, because `PgCollection` back-refers to `maxTupleSize` !
            // TODO: Fix this!
            //       As we do not create the admin-catalog feature or any of the collection features,
            //       we need to do this on demand using `insert_rows`.
            // _adminCatalog = PgCatalog(this)
            // _adminCollections = PgCollection(this)
            // _adminTransactions = PgCollection(this)
            // _adminBooks = PgCollection(this)
            // _adminCatalogs = PgCollection(this)

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
            conn.execute(SQL).fetch().use { cursor ->
                _versionSequenceOid = cursor["oid"]
                //mapNumberSequenceOid = cursor["map_oid"]
                //colNumberSequenceOid = cursor["col_oid"]
            }
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
     * Create a schema, which actually represents a catalog.
     * @param conn the connection to use.
     * @param name the unquoted name of the schema.
     * @return the `OID` of the created schema.
     */
    internal fun create_schema(conn: PgConnection, name: String): Int {
        // TODO: We need to ensure that there is no other schema with the same feature-number:
        // Write: `ALTER SCHEMA ${catalog.quotedId} SET SCHEMA OPTION 'featureNumber' 'stringifiedFN';`
        // Read:
        // SELECT nspname, nspconfig FROM pg_namespace WHERE nspname = '${catalog.id}';
        // SELECT nspname, split_part(kv, '=', 2) AS feature_number
        // FROM pg_namespace, unnest(nspconfig) AS kv
        // WHERE nspname = '${catalog.id}' AND split_part(kv, '=', 1) = 'featureNumber';
        conn.execute("CREATE SCHEMA IF NOT EXISTS ${quoteIdent(name)}").close()
        // TODO: Return OID of created schema!
        return 0
    }

    internal fun drop_schema(conn: PgConnection, name: String) {}
    internal fun create_table(
        conn: PgConnection,
        schema: String,
        table: String,
        columns: Array<PgColumn>,
        indices: Array<PgIndex>,
        shift: Int,
        partitions: Int
    ) {}
    internal fun drop_table(conn: PgConnection, name: String) {}

    // TODO: Future index adding and removing
    internal fun create_index_concurrently(conn: PgConnection, index: Index) {}
    internal fun drop_index(conn: PgConnection, index: Index) {}

    // TODO: When executing upsert, update or delete, we need to verify the result of the purge query.
    //       It tells us which state each row had before execution. When the state is not the expected one,
    //       we will actually do a rollback and return back the error. This avoids all kind of
    //       conditional queries, everything just relies upon an index-only scan of the primary-key index,
    //       so above (nv, fn, version). It will be faster when being successful (that is what we hope for
    //       in most cases), while we will be slower when a real conflict arises (which we expect to be rare).
    internal fun insert_rows(conn: PgConnection, schema: String, table: String, rows: PgRows) {}
    internal fun upsert_rows(conn: PgConnection, schema: String, table: String, rows: PgRows) {}
    internal fun update_rows(conn: PgConnection, schema: String, table: String, rows: PgRows) {}
    internal fun delete_rows_old_state(conn: PgConnection, schema: String, table: String, ids: Array<Id>?) {}
    internal fun purge_rows(conn: PgConnection, schema: String, table: String, ids: Array<Id>) {}
    // TODO: Executing a query generally should now be easier.
    //       We need the WHERE builder, but can we simplify this, so we can test better and just use from writer?
    internal fun query_rows(conn: PgConnection, schema: String, table: String, query: PgQuery) {}

    // TODO: Future delete mechanisms: first for storage-api, second as new standard way, because it is much faster and sufficient
    internal fun delete_rows_new_states(conn: PgConnection, schema: String, table: String, rows: PgRows) {}
    internal fun delete_rows_no_states(conn: PgConnection, schema: String, table: String) {}
}