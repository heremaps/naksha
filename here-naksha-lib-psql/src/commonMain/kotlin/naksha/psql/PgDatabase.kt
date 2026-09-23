package naksha.psql

import naksha.base.Id
import naksha.model.objects.Index
import naksha.model.objects.IndexList
import naksha.model.objects.MemberList
import naksha.model.objects.NakshaCatalog
import naksha.model.objects.NakshaCollection
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
class PgDatabase(
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
     * Initialize the database, install SQL code, create extensions, create `naksha~admin` schema, create admin collections, inserting the immutable features for the standard admin collections.
     *
     * Eventually commits the successfully created or updated database.
     * @param conn the connection to use for the initialization.
     * @since 3.0
     */
    internal fun init(
        conn: PgConnection,
        adminCatalog: PgCatalog,
        collectionsCollection: PgCollection,
        transactionsCollection: PgCollection,
        catalogsCollection: PgCollection,
        booksCollection: PgCollection,
        upgrade: Boolean
    ) {

    }

    // ——————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————
    // The following methods do only low-level PostgresQL actions. They do not care about the logical part, so about the
    // corresponding administrative objects, the features representing a schema (PgCatalog), a table (PgCollection), a
    // column (PgColumn), or an index (PgIndex).
    // ——————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————

    /**
     * Create a schema, which actually represents a catalog.
     * @param conn the connection to use.
     * @param name the unquoted name of the schema.
     */
    internal fun create_schema(conn: PgConnection, name: String) {
        // TODO: We need to ensure that there is no other schema with the same feature-number:
        // Write: `ALTER SCHEMA ${catalog.quotedId} SET SCHEMA OPTION 'featureNumber' 'stringifiedFN';`
        // Read:
        // SELECT nspname, nspconfig FROM pg_namespace WHERE nspname = '${catalog.id}';
        // SELECT nspname, split_part(kv, '=', 2) AS feature_number
        // FROM pg_namespace, unnest(nspconfig) AS kv
        // WHERE nspname = '${catalog.id}' AND split_part(kv, '=', 1) = 'featureNumber';
        conn.execute("CREATE SCHEMA IF NOT EXISTS ${quoteIdent(name)}").close()
    }
    internal fun drop_schema(conn: PgConnection, name: String) {}

    internal fun create_table(conn: PgConnection, name: String, members: MemberList, indices: IndexList, shift: Int, partitions: Int) {}
    internal fun drop_table(conn: PgConnection, name: String) {}

    // TODO: Future index adding and removing
    internal fun create_index_concurrently(conn: PgConnection, index: Index) {}
    internal fun drop_index(conn: PgConnection, index: Index) {}

    // TODO: How do we provide the data? Should we use PgRows? Is there a way to make the code easier?
    //       Note, much of the current code is historically grows, as we know have three mandatory columns
    //       being `nv`, `fn`, and `version` plus a bunch of custom columns with defined types, can we simplify
    //       the low level API?
    internal fun insert_rows(conn: PgConnection, schema: String, table: String) {}
    internal fun upsert_rows(conn: PgConnection, schema: String, table: String) {}
    internal fun update_rows(conn: PgConnection, schema: String, table: String) {}
    internal fun delete_rows(conn: PgConnection, schema: String, table: String) {}
    internal fun purge_rows(conn: PgConnection, schema: String, table: String) {}
    // TODO: Executing a query generally should now be easier.
    //       We need the WHERE builder, but can we simplify this, so we can test better and just use from writer?
    internal fun query_rows(conn: PgConnection, schema: String, table: String) {}
}