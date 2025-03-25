@file:Suppress("MemberVisibilityCanBePrivate", "unused", "OPT_IN_USAGE")

package naksha.psql

import naksha.model.illegalState
import naksha.psql.PgUtil.PgUtilCompanion.quoteIdent
import naksha.psql.PgUtil.PgUtilCompanion.quoteLiteral
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic

/**
 * Information about a single database table.
 * @see [PgHead]
 * @see [PgTransactions]
 * @see [PgDeleted]
 * @see [PgHistory]
 * @see [PgMeta]
 */
@JsExport
open class PgTable(
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
     * The storage-class where the table is located.
     * @since 3.0
     */
    @JvmField val storageClass: PgStorageClass,
    /**
     * If the table is volatile (is updated often); `false` only for history tables.
     * @since 3.0
     */
    @JvmField val isVolatile: Boolean,
    /**
     * The parent table, if this is a partition of it.
     * @since 3.0
     */
    @JvmField val partitionOfTable: PgTable? = null,
    /**
     * If this is a partition of a [history table][PgHistory], the year; otherwise, if this is a performance partition, the index in the partitions array, so a value between 0 and n, with n being `partitionOf.partitionCount - 1`.
     * @since 3.0
     */
    @JvmField val partitionOfValue: Int = -1,
    /**
     * The column by which to partition, if this is a partitioned table; must be either [PgColumn.tn] or [PgColumn.next_tn].
     * @since 3.0
     */
    @JvmField val partitionByColumn: PgColumn? = null,
    /**
     * If this table is performance-partitioned, the amount of partitions, therefore
     * - `0` when [partitionByColumn] is `null`
     * - `0` when [partitionByColumn] is [PgColumn.next_tn], so the partitions are years.
     * - `2 .. 1000` when [partitionByColumn] is [PgColumn.tn], so this is a performance-partitioned table.
     * @since 3.0
     */
    @JvmField val partitionCount: Int = 0
) {

    companion object PgTableCompanion {
        /**
         * Tests if this is any HEAD table _(either root or a performance-partition)_.
         * @param name the table name.
         * @return _true_ if this is any HEAD table.
         */
        @JvmStatic
        @JsStatic
        fun isAnyHead(name: String): Boolean = isHead(name) || isHeadPartition(name)

        /**
         * Tests if this is the root HEAD table.
         * @param name the relation name.
         * @return _true_ if this is the root HEAD table.
         */
        @JvmStatic
        @JsStatic
        fun isHead(name: String): Boolean = name.indexOf(PG_S) < 0 // does not contain a separator

        /**
         * Tests if this is a performance-partition of the HEAD table.
         * @param name the table name.
         * @return _true_ if this is a performance-partition of the HEAD table.
         */
        @JvmStatic
        @JsStatic
        fun isHeadPartition(name: String): Boolean = name.indexOf(PG_PART) > 0 // {name}$a???

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
        fun isDeleted(name: String): Boolean = name.endsWith(PG_DEL) // {name}$del

        /**
         * Tests if this is a performance-partition of the DELETED table.
         * @param name the table name.
         * @return _true_ if this is a performance-partition of the DELETED table.
         */
        @JvmStatic
        @JsStatic
        fun isDeletedPartition(name: String): Boolean = name.indexOf("${PG_DEL}${PG_PART}") > 0 // {name}$del$a???

        /**
         * Tests if this is the META table.
         * @param name the table name.
         * @return _true_ if this is the META table.
         */
        @JvmStatic
        @JsStatic
        fun isMeta(name: String): Boolean = name.endsWith(PG_META) // {name}$meta

        /**
         * Tests if this is any HISTORY table.
         * @param name the table name.
         * @return _true_ if this is any HISTORY table.
         */
        @JvmStatic
        @JsStatic
        fun isAnyHistory(name: String): Boolean = name.indexOf(PG_HST) > 0 // {name}$hst...

        /**
         * Tests if this is the root HISTORY table.
         * @param name the table name.
         * @return _true_ if this is the root HISTORY table.
         */
        @JvmStatic
        @JsStatic
        fun isHistory(name: String): Boolean = name.endsWith("${PG_S}${PG_HST}") // {name}$hst

        /**
         * Tests if this is a year-partition of HISTORY, but not a performance-partition.
         * @param name the table name.
         * @return _true_ if this is a year-partition of HISTORY.
         */
        @JvmStatic
        @JsStatic
        fun isHistoryYear(name: String): Boolean = // {name}$hst$y????
            name.lastIndexOf(PG_YEAR) == (name.length - "${PG_YEAR}????".length) // end with $y????

        /**
         * Tests if this is a performance-partition of a HISTORY year-partition.
         * @param name the table name.
         * @return _true_ if this is a performance-partition of a monthly HISTORY year-partition.
         */
        @JvmStatic
        @JsStatic
        fun isHistoryPartition(name: String): Boolean = // {name}$hst$y????$a???
            name.indexOf("${PG_HST}${PG_YEAR}") >= 0
                    && name.lastIndexOf(PG_PART) == (name.length - "${PG_PART}???".length) // ends with $a???

        /**
         * An indicator if this is an internal Naksha collection. Very special rules apply to these tables.
         * @param name the table name.
         * @return _true_ if this is an internal database table.
         */
        @JvmStatic
        @JsStatic
        fun isInternal(name: String): Boolean = name.startsWith(PG_INTERNAL_PREFIX)
    }

    /**
     * The table identifier, optionally quoted in double quotes.
     */
    @JvmField
    val quotedName: String = quoteIdent(name)

    private fun doInit(): Triple<String?, String?, String?> {
        // Copy to stack, makes it easier for the compiler to remember when values are not null!
        val partitionByColumn = this.partitionByColumn
        val partitionCount = this.partitionCount
        val parentTable = partitionOfTable
        val partValue = partitionOfValue
        val superTable: PgTable?

        // If this is a child table
        if (parentTable != null) {
            require(parentTable.partitionByColumn != null) {
                "The table '${name}' is a partition of '${parentTable.name}', but the parent does not declare 'partitionBy'"
            }
            superTable = parentTable.partitionOfTable
            when (parentTable.partitionByColumn) {
                PgColumn.tn -> {
                    require(partValue >= 0 && partValue < parentTable.partitionCount) {
                        """The table '$name' is a partition of '${parentTable.name}', but does not declare a valid 'partitionOfValue' (0 to ${parentTable.partitionCount}): $partValue"""
                    }
                }

                PgColumn.next_tn -> {
                    require(partValue in 2000..2200) {
                        """The table '$name' is a partition of '${parentTable.name}', but does not declare a valid 'partitionOfValue' (expect a year): $partValue"""
                    }
                }

                else -> throw IllegalArgumentException(
                    """The table '$name' is partitioned by invalid column: ${parentTable.partitionByColumn} (must be ${PgColumn.tn.name} or ${PgColumn.next_tn.name})"""
                )
            }
        } else {
            superTable = null
        }

        // If this table is partitioned.
        if (partitionByColumn != null) {
            when (partitionByColumn) {
                PgColumn.tn -> {
                    require(partitionCount in 2..1000) { "Invalid partition-count, expect 2 .. 1000, found : $partitionCount" }
                }
                // This is used in transaction table and history table, partition by year.
                PgColumn.next_tn -> {
                    require(partitionCount == 0) { "Invalid partition-count, expect 0, found : $partitionCount" }
                }

                else -> throw IllegalArgumentException("Unsupported partitionByColumn: '$partitionByColumn'")
            }
            // As this is a partitioned table, we do not create it.
            return Triple(null, null, null)
        }
        // This table is not partitioned, so we need to create it.
        require(partitionCount == 0) {
            "No partitioning, but partitionCount ($partitionCount) given for table '$name'"
        }

        val storage = collection.map.storage
        val CREATE_TABLE: String
        val TABLESPACE: String
        when (storageClass) {
            PgStorageClass.Ephemeral -> {
                CREATE_TABLE = "CREATE TABLE IF NOT EXISTS "
                TABLESPACE =
                    if (storage.adminMap.ephemeralTableSpace != null) " TABLESPACE ${storage.adminMap.ephemeralTableSpace}" else ""
            }

            PgStorageClass.Brittle -> {
                CREATE_TABLE = "CREATE UNLOGGED TABLE IF NOT EXISTS "
                TABLESPACE = if (storage.adminMap.brittleTableSpace != null) " TABLESPACE ${storage.adminMap.brittleTableSpace}" else ""
            }

            PgStorageClass.Temporary -> {
                CREATE_TABLE = "CREATE UNLOGGED TABLE IF NOT EXISTS "
                TABLESPACE = if (storage.adminMap.tempTableSpace != null) " TABLESPACE ${storage.adminMap.tempTableSpace}" else ""
            }

            else -> {
                CREATE_TABLE = "CREATE TABLE IF NOT EXISTS "
                TABLESPACE = ""
            }
        }
        val YEARLY_CHECK: String
        val PARTITION_CHECK: String
        if (parentTable != null) {
            // Partition of some kind.
            if (superTable != null) {
                // If the super-table is not null, then this must be a performance table of history, as we have two
                // levels above us, which only happens for history, where we have:
                // HISTORY (super), then YEARLY (parent), then PARTITION (this)
                val superPartValue = parentTable.partitionOfValue
                if (superPartValue !in 2000..2200) {
                    throw illegalState("The table '${parentTable.name}' is a yearly partition with an illegal value for year: $superPartValue")
                }
                val partCount = parentTable.partitionCount
                if (partValue !in 0 ..< partCount) {
                    throw illegalState("The table '${name}' is performance-partitioned with an partition-number being illegal: $partValue; expected 0 till $partCount")
                }
                YEARLY_CHECK = ",CONSTRAINT ${quoteIdent(name + PG_YEAR_CONSTRAINT)} CHECK (next_tn IS NOT NULL AND naksha_tn_year(next_tn)=${superPartValue})\n"
                PARTITION_CHECK = ",CONSTRAINT ${quoteIdent(name + PG_PART_CONSTRAINT)} CHECK (naksha_tn_partition_index(tn, $partCount)=$partValue)\n"
            } else if (parentTable.partitionByColumn == PgColumn.next_tn) {
                // This is a yearly partition, which only happens for transactions table!
                if (partValue !in 2000..2200) {
                    throw illegalState("The table '${parentTable.name}' is a yearly partition with an illegal value for year: $partValue")
                }
                YEARLY_CHECK = ",CONSTRAINT ${quoteIdent(name + PG_YEAR_CONSTRAINT)} CHECK (next_tn IS NOT NULL AND naksha_tn_year(next_tn)=$partValue)\n"
                PARTITION_CHECK = ""
            } else if (parentTable.partitionByColumn == PgColumn.tn) {
                val partCount = parentTable.partitionCount
                // This is a performance partition, without year on top.
                if (partValue !in 0 ..< partCount) {
                    throw illegalState("The table '${name}' is performance-partitioned with an partition-number being illegal: $partValue; expected 0 till $partCount")
                }
                YEARLY_CHECK = ""
                PARTITION_CHECK = ",CONSTRAINT ${quoteIdent(name + PG_PART_CONSTRAINT)} CHECK (naksha_tn_partition_index(tn, $partCount)=$partValue)\n"
            } else {
                throw illegalState("Table '${name}' is partition by '${parentTable.partitionByColumn}', this is not supported")
            }
        } else {
            // Normal table, for example standard HEAD, DELETE or META
            PARTITION_CHECK = ""
            YEARLY_CHECK = ""
        }
        val SQL = """$CREATE_TABLE $quotedName (
${PgColumn.allColumns.joinToString(",\n") { it.sqlDefinition }},
CONSTRAINT ${quoteIdent(PgIndex.tn_pkey.id(this))} PRIMARY KEY (${PgColumn.tn})
$YEARLY_CHECK$PARTITION_CHECK)
WITH (fillfactor=${if (isVolatile) 85 else 100},toast_tuple_target=1024)
$TABLESPACE"""
        return Triple(SQL, CREATE_TABLE, TABLESPACE)
    }

    /**
     * The SQL code needed to create the table.
     * @return the SQL code needed to create the table.
     */
    @JvmField
    internal val CREATE_SQL: String?

    @JvmField
    internal val CREATE_TABLE: String?

    @JvmField
    internal val TABLESPACE: String?

    init {
        val (CREATE_SQL, CREATE_TABLE, TABLESPACE) = doInit()
        this.CREATE_SQL = CREATE_SQL
        this.CREATE_TABLE = CREATE_TABLE
        this.TABLESPACE = TABLESPACE
    }

    /**
     * All existing and declared indices.
     */
    var indices: List<PgIndex> = emptyList()
        internal set

    /**
     * If this table is partitioned by year.
     */
    @JvmField
    val hasYearPartitions: Boolean = partitionByColumn == PgColumn.next_tn

    /**
     * If this table is performance partitioned, so features are stored based upon their ID.
     */
    @JvmField
    val hasTnPartitions: Boolean = (partitionByColumn == PgColumn.tn) && partitionCount >= 2

    /**
     * Create the table and its partitions.
     */
    internal open fun create(conn: PgConnection) {
        if (CREATE_SQL != null) conn.execute(CREATE_SQL).close()
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
            index.drop(conn, this)
            indices = indices - index
        }
    }
}

