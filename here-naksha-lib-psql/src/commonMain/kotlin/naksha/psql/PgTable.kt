@file:Suppress("MemberVisibilityCanBePrivate", "unused", "OPT_IN_USAGE")

package naksha.psql

import naksha.psql.PgUtil.PgUtilCompanion.quoteIdent
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic

/**
 * Information about a single database table.
 * @property collection the collection to which the table belongs.
 * @property name the table-name.
 * @property storageClass the storage-class where the table is located.
 * @property isVolatile if the table is volatile (is updated often); _false_ only for history tables.
 * @property partitionOfTable the parent table, if this is a partition of it.
 * @property partitionOfValue if this is a partition of a yearly history table, the year; otherwise, if this is a performance partition, the index in the partitions array, so a value between 0 and n, with n being `partitionOf.partitionCount - 1`.
 * @property partitionByColumn the column by which to partition.
 * @property partitionCount the amount of partitions, must be 0 when [partitionByColumn] is _null_ or when the number of partitions is flexible; otherwise must be a value between 2 and 256 (for fixed partition count).
 */
@JsExport
open class PgTable(
    @JvmField val collection: PgCollection,
    @JvmField val name: String,
    @JvmField val storageClass: PgStorageClass,
    @JvmField val isVolatile: Boolean,
    @JvmField val partitionOfTable: PgTable? = null,
    @JvmField val partitionOfValue: Int = -1,
    @JvmField val partitionByColumn: PgColumn? = null,
    @JvmField val partitionCount: Int = 0
) {
    companion object PgTableCompanion {
        /**
         * Tests if this is any HEAD table.
         * @param name the table name.
         * @return _true_ if this is any HEAD table.
         */
        @JvmStatic
        @JsStatic
        fun isAnyHead(name: String): Boolean = isHead(name) || isHeadPartition(name)

        /**
         * Tests if this is the root HEAD table.
         * @param name the table name.
         * @return _true_ if this is the root HEAD table.
         */
        @JvmStatic
        @JsStatic
        fun isHead(name: String): Boolean = name.indexOf('$') < 0

        /**
         * Tests if this is a partition of the HEAD table.
         * @param name the table name.
         * @return _true_ if this is a partition of the HEAD table.
         */
        @JvmStatic
        @JsStatic
        fun isHeadPartition(name: String): Boolean = name.indexOf("\$p") > 0 // {name}$p000

        /**
         * Tests if this is any DELETED table.
         * @param name the table name.
         * @return _true_ if this is any DELETED table.
         */
        @JvmStatic
        @JsStatic
        fun isAnyDeleted(name: String): Boolean = isDeleted(name) || isDeletedPartition(name)

        /**
         * Tests if this is the root DELETED table.
         * @param name the table name.
         * @return _true_ if this is the root DELETED table.
         */
        @JvmStatic
        @JsStatic
        fun isDeleted(name: String): Boolean = name.endsWith("\$del") // {name}$del

        /**
         * Tests if this is a partition of the DELETED table.
         * @param name the table name.
         * @return _true_ if this is a partition of the DELETED table.
         */
        @JvmStatic
        @JsStatic
        fun isDeletedPartition(name: String): Boolean = name.indexOf("\$del_p") > 0 // {name}$del_p000

        /**
         * Tests if this is the META table.
         * @param name the table name.
         * @return _true_ if this is the META table.
         */
        @JvmStatic
        @JsStatic
        fun isMeta(name: String): Boolean = name.endsWith("\$meta") // {name}$meta

        /**
         * Tests if this is any HISTORY table.
         * @param name the table name.
         * @return _true_ if this is any HISTORY table.
         */
        @JvmStatic
        @JsStatic
        fun isAnyHistory(name: String): Boolean = name.indexOf("\$hst") > 0 // {name}$hst...

        /**
         * Tests if this is the root HISTORY table.
         * @param name the table name.
         * @return _true_ if this is the root HISTORY table.
         */
        @JvmStatic
        @JsStatic
        fun isHistory(name: String): Boolean = name.endsWith("\$hst") // {name}$hst

        /**
         * Tests if this is a monthly partition of HISTORY.
         * @param name the table name.
         * @return _true_ if this is a monthly partition of HISTORY.
         */
        @JvmStatic
        @JsStatic
        fun isHistoryYear(name: String): Boolean = // {name}$hst_yyyy
            name.lastIndexOf("\$hst_") == (name.length - "\$hst_yyyy".length)

        /**
         * Tests if this is a sub-partition of a monthly HISTORY partition.
         * @param name the table name.
         * @return _true_ if this is a sub-partition of a monthly HISTORY partition.
         */
        @JvmStatic
        @JsStatic
        fun isHistoryPartition(name: String): Boolean = // {name}$hst_yyyy_p000
            name.indexOf("\$hst_") >= 0 && name.lastIndexOf("_p") == (name.length - "_p000".length)

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
     * The table identifier, optionally quoted in double quotes.
     */
    @JvmField
    val quotedName: String = quoteIdent(name)

    /**
     * The SQL code needed to create the table.
     * @return the SQL code needed to create the table.
     */
    @JvmField
    internal val CREATE_SQL: String

    @JvmField
    internal val CREATE_TABLE: String

    @JvmField
    internal val TABLESPACE: String

    init {
        // Sanity check only.
        if (partitionByColumn == null) require(partitionCount == 0) {
            "No partitioning, but partitionCount ($partitionCount) given for table '$name'"
        } else {
            // Note: We allow partitionBy to be set, with partitionCount being 0 or 2..256
            require(partitionCount == 0 || partitionCount in 2..256) {
                "Invalid number of partitions for '$name', must bei 0 or 2 to 256, given $partitionCount"
            }
        }
        if (partitionOfTable != null) {
            val parent = partitionOfTable
            require(parent.partitionByColumn != null) {
                "The table '${name}' is a partition of '${parent.name}', but the parent does not declare 'partitionBy'"
            }
            val pofValue = partitionOfValue
            when (parent.partitionByColumn) {
                PgColumn.id -> {
                    require(pofValue >= 0 && pofValue < parent.partitionCount) {
                        """The table '$name' is a partition of '${parent.name}', but does not declare a valid 'partitionOfValue' (0 to ${parent.partitionCount}): $pofValue"""
                    }
                }

                PgColumn.txn_next, PgColumn.txn -> {
                    require(pofValue in 2000..3000) {
                        """The table '$name' is a partition of '${parent.name}', but does not declare a valid 'partitionOfValue' (expect a year): $pofValue"""
                    }
                }

                else -> throw IllegalArgumentException(
                    """The table '$name' is partitioned by invalid column: ${parent.partitionByColumn} (must be ${PgColumn.id.name}, ${PgColumn.txn.name} or ${PgColumn.txn_next.name})"""
                )
            }
        }
        val storage = collection.schema.storage
        when (storageClass) {
            PgStorageClass.Brittle -> {
                CREATE_TABLE = "CREATE UNLOGGED TABLE IF NOT EXISTS "
                TABLESPACE = if (storage.brittleTableSpace != null) " TABLESPACE ${storage.brittleTableSpace}" else ""
            }

            PgStorageClass.Temporary -> {
                CREATE_TABLE = "CREATE UNLOGGED TABLE IF NOT EXISTS "
                TABLESPACE = if (storage.tempTableSpace != null) " TABLESPACE ${storage.tempTableSpace}" else ""
            }

            else -> {
                CREATE_TABLE = "CREATE TABLE IF NOT EXISTS "
                TABLESPACE = ""
            }
        }
        val TABLE_BODY = "(${PgColumn.columns.joinToString(",\n") { it.sqlDefinition }})"
        // See: https://www.ongres.com/blog/toast_and_its_influences_-on_parallelism_in_postgres/
        // parallel_workers: A storage parameter for tables, that allows change the behavior of number of workers to
        // execute a query activity in parallel, similar to max_parallel_workers_per_gather, but only for a specific table;
        // ALTER TABLE tabname SET (parallel_workers = N);
        val WITH = if (partitionByColumn == null)
            "WITH (fillfactor=${if (isVolatile) 65 else 100},toast_tuple_target=${storage.maxTupleSize})"
        else
            ""
        val PARTITION_BY = when (partitionByColumn) {
            // Not partitioned by itself.
            null -> ""
            // When we partition by ID, we do this using the first byte of the md5 hash of the feature id
            PgColumn.id -> {
                require(partitionCount in 2..256) { "Invalid partition-count, expect 2 .. 256, found : $partitionCount" }
                "PARTITION BY RANGE ((get_byte(digest(id,'md5'),0) % $partitionCount))"
            }
            // This is used in transaction table and history table, partition by year.
            PgColumn.txn, PgColumn.txn_next -> "PARTITION BY RANGE ((${partitionByColumn.name} >> 41))"
            else -> throw IllegalArgumentException("Unsupported partitionByColumn: '$partitionByColumn'")
        }
        if (partitionOfTable != null) {
            val PARTITION_OF = """ PARTITION OF ${partitionOfTable.quotedName} FOR VALUES FROM (${partitionOfValue}) TO (${partitionOfValue + 1}) """
            CREATE_SQL = """$CREATE_TABLE ${collection.schema.nameQuoted}.$quotedName ${PARTITION_OF}${PARTITION_BY}${WITH}${TABLESPACE}"""
        } else {
            CREATE_SQL = "$CREATE_TABLE ${collection.schema.nameQuoted}.$quotedName ${TABLE_BODY}${PARTITION_BY}${WITH}${TABLESPACE}"
        }
    }

    /**
     * All existing and declared indices.
     */
    var indices: List<PgIndex>? = null
        internal set

    /**
     * If this table is partitioned by year.
     */
    @JvmField
    val hasYearPartitions: Boolean = partitionByColumn == PgColumn.txn || partitionByColumn == PgColumn.txn_next

    /**
     * If this table is performance partitioned, so features are stored based upon their ID.
     */
    @JvmField
    val hasIdPartitions: Boolean = partitionByColumn == PgColumn.id && partitionCount >= 2

    /**
     * Create the table and its partitions.
     */
    internal open fun create(conn: PgConnection) = conn.execute(CREATE_SQL).close()

    fun addIndex(index: PgIndex) {
        // TODO: "Implement me!"
    }

    fun dropIndex(index: PgIndex) {
        // TODO: "Implement me!"
    }
}

