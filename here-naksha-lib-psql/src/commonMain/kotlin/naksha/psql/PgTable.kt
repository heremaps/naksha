@file:Suppress("MemberVisibilityCanBePrivate", "unused", "OPT_IN_USAGE")

package naksha.psql

import naksha.psql.PgUtil.PgUtilCompanion.quoteIdent
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic

/**
 * Information about a single database table.
 * @see [PgHeadTable]
 * @see [PgHistoryTable]
 * @see [PgMetaTable]
 */
@JsExport
abstract class PgTable(
    /**
     * The collection to which the table belongs.
     * @since 3.0
     */
    @JvmField val collection: PgCollection,

    /**
     * The table-name.
     * @since 3.0
     */
    @JvmField val name: String,

    /**
     * The parent table, if this is a partition of it.
     * @since 3.0
     */
    @JvmField val parent: PgTable? = null,
) {

    companion object PgTableCompanion {
        /**
         * Tests if this is any HEAD table, either root or a distribution-partition.
         * @param name the table name.
         * @return _true_ if this is any HEAD table.
         */
        @JvmStatic
        @JsStatic
        fun isAnyHead(name: String): Boolean = isHead(name) || isHeadDistributionPartition(name)

        /**
         * Tests if this is the root HEAD table, i.e. `foo`.
         * @param name the relation name.
         * @return _true_ if this is the root HEAD table.
         */
        @JvmStatic
        @JsStatic
        fun isHead(name: String): Boolean = name.indexOf('$') < 0

        /**
         * Tests if this is a distribution-partition of the HEAD table, i.e. `foo$12`.
         * @param name the table name.
         * @return _true_ if this is a performance-partition of the HEAD table.
         */
        @JvmStatic
        @JsStatic
        fun isHeadDistributionPartition(name: String): Boolean = name.indexOf('$') > 0
                && !name.contains("\$hst")
                && !name.contains("\$meta")

        /**
         * Tests if this is the META table, i.e. `foo$meta`.
         * @param name the table name.
         * @return _true_ if this is the META table.
         */
        @JvmStatic
        @JsStatic
        fun isMeta(name: String): Boolean = name.endsWith("\$meta")

        /**
         * Tests if this is any HISTORY table.
         * @param name the table name.
         * @return _true_ if this is any HISTORY table.
         */
        @JvmStatic
        @JsStatic
        fun isAnyHistory(name: String): Boolean = name.indexOf("\$hst") > 0

        /**
         * Tests if this is the root HISTORY table.
         * @param name the table name.
         * @return _true_ if this is the root HISTORY table.
         */
        @JvmStatic
        @JsStatic
        fun isHistory(name: String): Boolean = name.endsWith("\$hst")

        /**
         * Tests if this is a partition of HISTORY, but not a distribution-partition, i.e. `foo$hst$2026`
         * @param name the table name.
         * @return _true_ if this is a partition of HISTORY.
         */
        @JvmStatic
        @JsStatic
        fun isHistoryPartition(name: String): Boolean = name.indexOf("\$hst") > 0
            && name.count { it.code == '$'.code } == 2

        /**
         * Tests if this is a distribution-partition of a HISTORY partition, i.e. `foo$hst$2026$1`
         * @param name the table name.
         * @return _true_ if this is a performance-partition of a HISTORY year-partition.
         */
        @JvmStatic
        @JsStatic
        fun isHistoryDistributionPartition(name: String): Boolean = name.indexOf("\$hst") > 0
            && name.count { it.code == '$'.code } == 3

        /**
         * An indicator if this is an internal Naksha collection. Very special rules apply to these tables.
         * @param name the table name.
         * @return _true_ if this is an internal database table.
         */
        @JvmStatic
        @JsStatic
        fun isInternal(name: String): Boolean = name.startsWith("naksha~")
    }

    /**
     * The `TOAST` management code is triggered only when a row value to be stored in a table is wider than `TOAST_TUPLE_THRESHOLD` bytes (normally 2 kB). The `TOAST` code will compress and/or move field values out-of-line until the row value is shorter than `TOAST_TUPLE_TARGET` bytes (also normally 2 kB, adjustable) or no more gains can be had. During an `UPDATE` operation, values of unchanged fields are normally preserved as-is; so an `UPDATE` of a row with out-of-line values incurs no `TOAST` costs if none of the out-of-line values change.
     *
     * **Note**: The value of `TOAST_TUPLE_THRESHOLD` is actually via compile switch fixed to 2 kB.
     */
    val toast_tuple_target: Int
        get() = 2048 // collection.catalog.storage.adminCatalog.maxTupleSize

    /**
     * The table identifier, optionally quoted in double quotes.
     */
    @JvmField
    val quotedName: String = quoteIdent(name)

    @JvmField
    val distributionPartitions: Array<PgTable> = if (collection.partitions <= 1) emptyArray()
        else if (this is PgHistoryPartition) Array(collection.partitions) { PgDistributionPartition(this, it) }
        else if (this is PgHeadTable) Array(collection.partitions) { PgDistributionPartition(this, it) }
        else emptyArray()

    /**
     * Generates the `CREATE_TABLE` string and the `TABLESPACE` string. Use like:
     * ```kotlin
     * val (CREATE_TABLE, TABLESPACE) = CREATE_TABLE_and_TABLESPACE()
     * ```
     * The returned `CREATE_TABLE` is something like `"CREATE TABLE IF NOT EXISTS "`, the `TABLESPACE` a string like `""` or `" TABLESPACE foo"`, so that the tablespace can be appended to the end of a table creation statement, and the create-table string is the prefix for the table creation.
     * @return the `CREATE_TABLE` string and the `TABLESPACE` string.
     * @since 3.0
     */
    @Suppress("FunctionName")
    protected fun CREATE_TABLE_and_TABLESPACE(): Pair<String, String> {
        //TODO enabling this will cause a closed loop dependency where PgStorage.setAdminMap() and PgStorage.adminCatalog call each other
        //TODO but we need to enable it, for now it is bypassed simply because storageClass is not set
//        val adminCatalog = collection.catalog.storage.adminCatalog
        return when (collection.storageClass) {
            PgStorageClass.Ephemeral -> Pair(
                "CREATE TABLE IF NOT EXISTS ",
                if (collection.catalog.storage.adminCatalog.ephemeralTableSpace != null) " TABLESPACE ${collection.catalog.storage.adminCatalog.ephemeralTableSpace}" else ""
            )

            PgStorageClass.Brittle -> Pair(
                "CREATE UNLOGGED TABLE IF NOT EXISTS ",
                if (collection.catalog.storage.adminCatalog.brittleTableSpace != null) " TABLESPACE ${collection.catalog.storage.adminCatalog.brittleTableSpace}" else ""
            )

            PgStorageClass.Temporary -> Pair(
                "CREATE UNLOGGED TABLE IF NOT EXISTS ",
                if (collection.catalog.storage.adminCatalog.tempTableSpace != null) " TABLESPACE ${collection.catalog.storage.adminCatalog.tempTableSpace}" else ""
            )

            else -> Pair("CREATE TABLE IF NOT EXISTS ", "")
        }
    }

    /**
     * The SQL code needed to create the table.
     * @return the SQL code needed to create the table.
     */
    @Suppress("FunctionName")
    abstract fun CREATE_SQL(): String

    /**
     * All existing and declared indices.
     */
    var indices: List<PgIndex> = emptyList()
        internal set

    /**
     * Create the table and its partitions.
     */
    internal open fun create(conn: PgConnection) {
        conn.execute(CREATE_SQL()).close()
    }

    /**
     * Creates the given index to the table and all partitions.
     * @param conn the connection to use to execute the creation.
     * @param index the index to add.
     */
    open fun createIndex(conn: PgConnection, index: PgIndex) {
        if (!indices.contains(index)) {
            index.create(conn, this)
            indices = indices + index
        }
    }

    /**
     * Add the given index into the administrative structure, does not perform any actual database change.
     * @param index the index to add.
     */
    open fun addIndex(index: PgIndex) {
        if (!indices.contains(index)) {
            indices = indices + index
        }
    }

    /**
     * Removes the given from the administrative structure, does not perform any actual database change.
     * @param index the index to add.
     */
    open fun removeIndex(index: PgIndex) {
        if (indices.contains(index)) {
            indices = indices - index
        }
    }

    /**
     * Removes the given index from the table and all partitions.
     * @param conn the connection to use to execute the removal.
     * @param index the index to remove.
     */
    open fun dropIndex(conn: PgConnection, index: PgIndex) {
        if (indices.contains(index)) {
            index.drop(conn, name)
            indices = indices - index
        }
    }

    /**
     * Maps a [PgType] to a sort-order.
     */
    private fun pgTypeSortOrder(type: PgType): Int = when (type) {
        PgType.INT64   -> 0  // INT64
        PgType.DOUBLE  -> 1  // FLOAT64
        PgType.INT     -> 2  // INT32
        PgType.FLOAT   -> 3  // FLOAT32
        PgType.SHORT   -> 4  // INT16
        PgType.BOOLEAN -> 6  // BOOLEAN
        PgType.STRING  -> 7  // STRING
        PgType.BYTE_ARRAY -> 8  // BYTE_ARRAY / SPATIAL
        PgType.JSONB -> 9  // JSONB, aka TAGS and SET
        else -> 10 // should not happen!
    }

    /**
     * Builds the full comma-separated column-definition block for a `CREATE TABLE` statement. The reads the [PgCollection].
     * @return the SQL column declarations to be used inside a `CREATE TABLE` statement.
     */
    internal fun columnDefinitions(): String {
        val sb = StringBuilder()
        for (column in collection.columns) {
            if (sb.isNotEmpty()) sb.append(",\n")
            sb.append(column.sql)
        }
        return sb.toString()
    }
}

