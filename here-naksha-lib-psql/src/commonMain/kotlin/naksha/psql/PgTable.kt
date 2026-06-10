@file:Suppress("MemberVisibilityCanBePrivate", "unused", "OPT_IN_USAGE")

package naksha.psql

import naksha.model.illegalState
import naksha.model.objects.Index
import naksha.model.objects.IndexType
import naksha.model.objects.MemberList
import naksha.model.objects.MemberType
import naksha.psql.PgUtil.PgUtilCompanion.quoteIdent
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
     * The column by which to partition, if this is a partitioned table; must be either [PgColumn.fn] or [PgColumn.next_version].
     * @since 3.0
     */
    @JvmField val partitionByColumn: PgColumn? = null,
    /**
     * If this table is performance-partitioned, the amount of partitions, therefore
     * - `0` when [partitionByColumn] is `null`
     * - `0` when [partitionByColumn] is [PgColumn.next_version], so the partitions are years.
     * - `2 .. 1000` when [partitionByColumn] is [PgColumn.fn], so this is a performance-partitioned table.
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
         * New naming: `{collection}$hst$<digits>` at end (no `$p` after).
         * @param name the table name.
         * @return _true_ if this is a year-partition of HISTORY.
         */
        @JvmStatic
        @JsStatic
        fun isHistoryYear(name: String): Boolean {
            val hstIdx = name.indexOf(PG_HST)
            if (hstIdx < 0) return false
            val after = hstIdx + PG_HST.length
            if (after >= name.length || name[after] != '$') return false
            val keyStart = after + 1
            val partIdx = name.indexOf(PG_PART, keyStart)
            val keyEnd = if (partIdx < 0) name.length else partIdx
            if (keyEnd <= keyStart) return false
            return name.substring(keyStart, keyEnd).all { it.isDigit() } && partIdx < 0
        }

        /**
         * Tests if this is a performance-partition of a HISTORY year-partition.
         * New naming: `{collection}$hst$<digits>$p<digits>`.
         * @param name the table name.
         * @return _true_ if this is a performance-partition of a HISTORY year-partition.
         */
        @JvmStatic
        @JsStatic
        fun isHistoryPartition(name: String): Boolean {
            val hstIdx = name.indexOf(PG_HST)
            if (hstIdx < 0) return false
            val after = hstIdx + PG_HST.length
            if (after >= name.length || name[after] != '$') return false
            val keyStart = after + 1
            val partIdx = name.indexOf(PG_PART, keyStart)
            if (partIdx < 0) return false
            val keyEnd = partIdx
            if (keyEnd <= keyStart) return false
            return name.substring(keyStart, keyEnd).all { it.isDigit() }
        }

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

    /**
     * Generate [CREATE_SQL] and [TABLESPACE].
     *
     * Actually, history is always partitioned by year, all other tables are optionally performance partitioned.
     */
    private fun doInit(): Pair<String?, String?> {
        // About TOAST_TUPLE_TARGET
        //
        // Each page in PostgresQL looks like:
        // Page header: ~24 bytes
        // ItemId array: 4 bytes per tuple
        // Line pointer: part of ItemId array
        // Tuple header: 23 bytes (for a normal heap tuple)
        // NULL bitmap: variable (~1 byte per 8 columns)
        // Actual data: variable
        //
        // Now, PostgresQL maximum page size is 32768 (configurable at compile time), therefore, setting TOAST_TUPLE_TARGET to 32767
        // will ensure that whatever size a page is, PostgresQL will try to insert the row completely, before falling back to TOAST !
        val map = collection.map;
        val toast_tuple_target = if (map is PgAdminMap) map.maxTupleSize else map.storage.adminMap.maxTupleSize;

        // Copy to stack, makes possible for the compiler to remember when values are not null!
        val shift = collection.head.shift
        val partitionCount = this.partitionCount
        val partitionColumn = this.partitionByColumn
        val partitionValue = partitionOfValue
        val parentTable = partitionOfTable
        val superTable: PgTable? = parentTable?.partitionOfTable

        // Verify state, if this is a child table.
        if (parentTable != null) {
            require(parentTable.partitionByColumn != null) {
                "The table '${name}' is a partition of '${parentTable.name}', but the parent does not declare 'partitionBy'"
            }
            when (parentTable.partitionByColumn) {
                PgColumn.fn -> {
                    require(partitionValue >= 0 && partitionValue < parentTable.partitionCount) {
                        """The table '$name' is a partition of '${parentTable.name}', but does not declare a valid 'partitionOfValue' (0 to ${parentTable.partitionCount}): $partitionValue"""
                    }
                }

                PgColumn.next_version -> {
                    require(partitionValue > 0) {
                        """The table '$name' is a partition of '${parentTable.name}', but does not declare a valid 'partitionOfValue' (expect a positive integer from next_version >> $shift): $partitionValue"""
                    }
                }

                else -> throw IllegalArgumentException(
                    """The table '$name' is partitioned by invalid column: ${parentTable.partitionByColumn} (must be ${PgColumn.fn.name} or ${PgColumn.next_version.name})"""
                )
            }
        }

        // If this table is partitioned.
        if (partitionColumn != null) {
            when (partitionColumn) {
                // The children are performance tables, partitioned by partition-number.
                PgColumn.fn -> {
                    require(partitionCount in 2..1000) { "Invalid partition-count, expect 2 .. 1000, found : $partitionCount" }
                }
                // The children are yearly tables, either in transaction table or history table, partition by year.
                PgColumn.next_version -> {
                    require(partitionCount == 0) { "Invalid partition-count, expect 0, found : $partitionCount" }
                }

                else -> throw IllegalArgumentException("Unsupported partitionByColumn: '$partitionColumn'")
            }
        } else {
            // This table is not partitioned, so we need to create it.
            require(partitionCount == 0) {
                "No partitioning, but partitionCount ($partitionCount) given for table '$name'"
            }
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

        // If this table is a child table (a partition).
        if (parentTable != null) {
            // If this table has a parent and a super-table, then this must be a performance partition of history,
            // as this is the only situation where we have two levels above us, we have:
            // HISTORY -> YEARLY -> PARTITION (this)
            if (superTable != null) {
                val superPartValue = parentTable.partitionOfValue
                if (superPartValue <= 0) {
                    throw illegalState("The table '${parentTable.name}' is a yearly partition with an illegal partition key: $superPartValue")
                }
                val parentPartCount = parentTable.partitionCount
                if (partitionValue !in 0 ..< parentPartCount) {
                    throw illegalState("The table '${name}' is performance-partitioned with an partition-number being illegal: $partitionValue; expected 0 till $parentPartCount")
                }
                // HISTORY year + perf-partition leaf: PK includes (fn, version, next_version) because next_version is the year-partition key.
                val SQL = """$CREATE_TABLE $quotedName PARTITION OF ${parentTable.quotedName} (
  CONSTRAINT ${quoteIdent(PgIndex.fn_pkey.id(this))} PRIMARY KEY (${PgColumn.fn}, ${PgColumn.version}, ${PgColumn.next_version}),
  CONSTRAINT ${quoteIdent(name + PG_TN_NEXT_CONSTRAINT)} CHECK (next_version IS NOT NULL AND ((next_version >> $shift)::int4)=${superPartValue}),
  CONSTRAINT ${quoteIdent(name + PG_ID_CONSTRAINT)} CHECK (((fn & 65535)::int4 % $parentPartCount)=$partitionValue)
) FOR VALUES FROM ($partitionValue) TO (${partitionValue+1})
WITH (fillfactor=100,toast_tuple_target=$toast_tuple_target)
$TABLESPACE"""
                return Pair(SQL, TABLESPACE)
            }

            // If the parent table is partitioned by year, but there is no super table, this is a yearly table.
            if (parentTable.partitionByColumn == PgColumn.next_version) {
                if (partitionValue <= 0) {
                    throw illegalState("The table '$name' is a yearly partition with an illegal partition key: $partitionValue")
                }

                // If this table is further partitioned, this must be part of history.
                // HISTORY -> YEARLY (this) -> PARTITION
                if (partitionColumn != null) {
                    if (partitionColumn != PgColumn.fn) {
                        throw illegalState("The history table '$name' must be partitioned only by `fn`, but ${partitionColumn.name} was used!")
                    }
                    val SQL = """$CREATE_TABLE $quotedName PARTITION OF ${parentTable.quotedName} (
  CONSTRAINT ${quoteIdent(name + PG_TN_NEXT_CONSTRAINT)} CHECK (next_version IS NOT NULL AND ((next_version >> $shift)::int4)=$partitionValue)
) FOR VALUES FROM ($partitionValue) TO (${partitionValue+1})
PARTITION BY RANGE (((fn & 65535)::int4 % $partitionCount))
$TABLESPACE"""
                    return Pair(SQL, TABLESPACE)
                }

                // HISTORY/TX yearly leaf (no further partitioning). PK includes (fn, version, next_version).
                val SQL = """$CREATE_TABLE $quotedName PARTITION OF ${parentTable.quotedName} (
  CONSTRAINT ${quoteIdent(PgIndex.fn_pkey.id(this))} PRIMARY KEY (${PgColumn.fn}, ${PgColumn.version}, ${PgColumn.next_version}),
  CONSTRAINT ${quoteIdent(name + PG_TN_NEXT_CONSTRAINT)} CHECK (next_version IS NOT NULL AND ((next_version >> $shift)::int4)=$partitionValue)
) FOR VALUES FROM ($partitionValue) TO (${partitionValue+1})
WITH (fillfactor=100,toast_tuple_target=$toast_tuple_target)
$TABLESPACE"""
                return Pair(SQL, TABLESPACE)
            }

            // If parent is partitioned by `fn`, then this is a performance partition either of HEAD or DELETED:
            // HEAD/DELETED -> PARTITION (this)
            if (parentTable.partitionByColumn == PgColumn.fn) {
                if (partitionColumn != null) {
                    throw illegalState("The performance partition '$name' must not be partitioned further, but is by $partitionColumn")
                }
                val parentPartCount = parentTable.partitionCount
                if (partitionValue !in 0 ..< parentPartCount) {
                    throw illegalState("The table '${name}' is performance-partitioned with an partition-number being illegal: $partitionValue; expected 0 till $parentPartCount")
                }
                val nextVersionConstraint = if (isDeleted(parentTable.name))
                    "  CONSTRAINT ${quoteIdent(name + PG_TN_NEXT_CONSTRAINT)} CHECK (next_version = version),\n"
                else ""
                val SQL = """$CREATE_TABLE $quotedName PARTITION OF ${parentTable.quotedName} (
  CONSTRAINT ${quoteIdent(PgIndex.fn_pkey.id(this))} PRIMARY KEY (${PgColumn.fn}) INCLUDE (${PgColumn.version}),
$nextVersionConstraint  CONSTRAINT ${quoteIdent(name + PG_ID_CONSTRAINT)} CHECK (((fn & 65535)::int4 % $parentPartCount)=$partitionValue)
) FOR VALUES FROM ($partitionValue) TO (${partitionValue+1})
WITH (fillfactor=100,toast_tuple_target=$toast_tuple_target)
$TABLESPACE"""
                return Pair(SQL, TABLESPACE)
            }

            // This is an unsupported setup!
            throw illegalState("Table '${name}' is partition by '${parentTable.partitionByColumn}', this is not supported")
        }
        // We do not have a parent table, this is a root table.

        // This is table is partitioned by year, it must be either transaction or history!
        if (partitionColumn == PgColumn.next_version) {
            // TX (this) -> YEARLY
            // HISTORY (this) -> YEARLY
            // HISTORY (this) -> YEARLY -> PARTITION
            val columns = PgColumn.allColumns
            val SQL = """$CREATE_TABLE $quotedName (
${columnDefinitions(columns)},
CONSTRAINT ${quoteIdent(name + PG_TN_NEXT_CONSTRAINT)} CHECK (next_version IS NOT NULL),
CONSTRAINT ${quoteIdent(name + PG_ID_CONSTRAINT)} CHECK ((fn < 0 AND id IS NOT NULL) OR (fn >= 0 AND id IS NULL))
) PARTITION BY RANGE (((next_version >> $shift)::int4))
$TABLESPACE"""
            return Pair(SQL, TABLESPACE)
        }

        // If the root table is not partitioned.
        if (partitionColumn == null) {
            // HEAD, META, or DELETED. One row per feature; PK = (fn) INCLUDE (version).
            // All tables always use the full head column set; custom members are appended via
            // columnDefinitions().
            val columns = if (isAnyHead(name) || isMeta(name)) PgColumn.headColumns else PgColumn.allColumns
            val SQL = """$CREATE_TABLE $quotedName (
${columnDefinitions(columns)},
CONSTRAINT ${quoteIdent(PgIndex.fn_pkey.id(this))} PRIMARY KEY (${PgColumn.fn}) INCLUDE (${PgColumn.version}),
CONSTRAINT ${quoteIdent(name + PG_ID_CONSTRAINT)} CHECK ((fn < 0 AND id IS NOT NULL) OR (fn >= 0 AND id IS NULL))
)
WITH (fillfactor=100,toast_tuple_target=$toast_tuple_target)
$TABLESPACE"""
            return Pair(SQL, TABLESPACE)
        }

        // If performance partitioned and not META.
        if (partitionColumn == PgColumn.fn && !isMeta(name)) {
            // HEAD (this) -> PARTITION
            // DELETED (this) -> PARTITION
            val columns = if (isDeleted(name)) PgColumn.allColumns else PgColumn.headColumns
            val nextVersionConstraint = if (isDeleted(name))
                "CONSTRAINT ${quoteIdent(name + PG_TN_NEXT_CONSTRAINT)} CHECK (next_version = version)"
            else ""
            val SQL = """$CREATE_TABLE $quotedName (
${columnDefinitions(columns)}${if (nextVersionConstraint.isNotEmpty()) ",\n$nextVersionConstraint" else ""},
CONSTRAINT ${quoteIdent(name + PG_ID_CONSTRAINT)} CHECK ((fn < 0 AND id IS NOT NULL) OR (fn >= 0 AND id IS NULL))
) PARTITION BY RANGE (((fn & 65535)::int4 % $partitionCount))
$TABLESPACE"""
            return Pair(SQL, TABLESPACE)
        }

        // Illegal partitioning.
        throw illegalState("The table '$name' is partitioned by $partitionColumn, this is not supported")
    }

    /**
     * The SQL code needed to create the table.
     * @return the SQL code needed to create the table.
     */
    @JvmField
    internal val CREATE_SQL: String?

    @JvmField
    internal val TABLESPACE: String?

    init {
        val (CREATE_SQL, TABLESPACE) = doInit()
        this.CREATE_SQL = CREATE_SQL
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
    val hasYearPartitions: Boolean = partitionByColumn == PgColumn.next_version

    /**
     * If this table is performance partitioned, so features are stored based upon their ID.
     */
    @JvmField
    val hasTnPartitions: Boolean = (partitionByColumn == PgColumn.fn) && partitionCount >= 2

    /**
     * Create the table and its partitions.
     */
    internal open fun create(conn: PgConnection) {
        if (CREATE_SQL != null) conn.execute(CREATE_SQL).close()
    }

    /**
     * Add any custom member columns that are declared in [NakshaCollection.members] but do not yet
     * exist in the physical table. This is the migration path for collections that have gained new
     * members after their initial creation.
     *
     * For each declared member whose name is not already a known [PgColumn], issues:
     * ```sql
     * ALTER TABLE <table> ADD COLUMN IF NOT EXISTS <col> <type>
     * ```
     * For known built-in [PgColumn] names the same statement is issued so that existing tables
     * gain optional columns added in later releases (e.g. `pn`, `pt`, `gv`).
     */
    internal open fun addMissingCustomColumns(conn: PgConnection) {
        // Always ensure all standard head columns are present (covers tables created with the old
        // lean schema that predates the full-schema approach).
        for (col in PgColumn.headColumns) {
            conn.execute("ALTER TABLE $quotedName ADD COLUMN IF NOT EXISTS ${col.sqlDefinition}").close()
        }
        // Also add any explicitly declared members that go beyond the standard set.
        val members = collection.head.members ?: return
        val knownCols = PgColumn.allColumnsByName
        val standardSet = PgColumn.headColumns.toSet()
        for (m in members) {
            if (m == null) continue
            val knownPgCol = knownCols[m.name]
            // For known PgColumn entries not in the standard headColumns (e.g. pn, pt, gv),
            // use the PgColumn sql definition; for truly custom members use the member type mapping.
            val colDef = when {
                knownPgCol != null && knownPgCol !in standardSet -> knownPgCol.sqlDefinition
                knownPgCol != null -> continue // already in headColumns loop above
                else -> PgCustomMemberValues.sqlDefinitionFor(m)
            }
            conn.execute("ALTER TABLE $quotedName ADD COLUMN IF NOT EXISTS $colDef").close()
        }
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

    /**
     * Returns the SQL fragment with extra column definitions for user-declared [members][naksha.model.objects.NakshaCollection.members].
     *
     * The result is empty when there are no members, otherwise it is comma-prefixed and ready to be appended to the built-in column list in `CREATE TABLE`.
     */
    internal fun extraColumnDefinitions(): String {
        val members = collection.head.members ?: return ""
        if (members.isEmpty()) return ""
        val knownCols = PgColumn.allColumnsByName
        val standardSet = PgColumn.headColumns.toSet()
        val sb = StringBuilder()
        for (member in members) {
            if (member == null) continue
            val knownPgCol = knownCols[member.name]
            val colDef = when {
                // Known PgColumn not in the standard head set (e.g. pn, pt, gv): use the canonical definition.
                knownPgCol != null && knownPgCol !in standardSet -> knownPgCol.sqlDefinition
                // Standard head columns are already in baseColumns; skip to avoid duplicates.
                knownPgCol != null -> continue
                // Truly custom member: generate from type mapping.
                else -> PgCustomMemberValues.sqlDefinitionFor(member)
            }
            sb.append(",\n").append(colDef)
        }
        return sb.toString()
    }

    /**
     * Maps a [PgType] to the same sort-order bucket used by [PgCustomMemberValues.columnSortOrder],
     * so that standard and custom columns can be interleaved into the correct type-alignment group.
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
        else -> 11
    }

    /**
     * Builds the full comma-separated column-definition block for a `CREATE TABLE` statement, merging
     * [baseColumns] with any custom/SPECIAL members declared on the collection.
     *
     * Extra members (SPECIAL like `pn`/`pt`/`gv`, plus truly custom ones) are inserted **within
     * their correct type-alignment bucket** — immediately after the last standard column of the same
     * type group. The result follows the canonical layout:
     *
     * ```
     * [INT64 std cols] [INT64 extras] [FLOAT64 std] [FLOAT64 extras] ... [BYTE_ARRAY std] [BYTE_ARRAY extras] [TAGS extras]
     * ```
     *
     * When [NakshaCollection.members] is `null` (backward-compatible mode) no custom columns exist and
     * [baseColumns] are emitted as-is.
     */
    internal fun columnDefinitions(baseColumns: List<PgColumn>): String {
        val members = collection.head.members
        if (members.isNullOrEmpty()) {
            return baseColumns.joinToString(",\n") { it.sqlDefinition }
        }
        val knownCols = PgColumn.allColumnsByName
        val standardSet = baseColumns.toSet()

        // Collect extra (SPECIAL + custom) column definitions, grouped by sort-order bucket.
        // Bucket → ordered list of SQL column definition strings.
        val extrasByBucket = mutableMapOf<Int, MutableList<String>>()
        for (m in members) {
            if (m == null) continue
            val knownPgCol = knownCols[m.name]
            val colDef = when {
                knownPgCol != null && knownPgCol !in standardSet -> knownPgCol.sqlDefinition
                knownPgCol != null -> continue // already in baseColumns — skip
                else -> PgCustomMemberValues.sqlDefinitionFor(m)
            }
            val bucket = PgCustomMemberValues.columnSortOrder(m.dataType)
            extrasByBucket.getOrPut(bucket) { mutableListOf() }.add(colDef)
        }

        if (extrasByBucket.isEmpty()) {
            return baseColumns.joinToString(",\n") { it.sqlDefinition }
        }

        // Determine the index of the last standard column in each bucket.
        val lastIndexForBucket = mutableMapOf<Int, Int>()
        for ((idx, col) in baseColumns.withIndex()) {
            val bucket = pgTypeSortOrder(col.type)
            lastIndexForBucket[bucket] = idx
        }
        // Also build a sorted list of standard bucket values for gap detection.
        val standardBuckets = lastIndexForBucket.keys.sorted()

        val sb = StringBuilder()
        for ((idx, col) in baseColumns.withIndex()) {
            if (sb.isNotEmpty()) sb.append(",\n")
            sb.append(col.sqlDefinition)
            // After the last standard column of this bucket, flush:
            // 1. Extras for this exact bucket.
            // 2. Extras for any intermediate buckets that have no standard columns and fall
            //    between this bucket and the next standard bucket (or end-of-list).
            val bucket = pgTypeSortOrder(col.type)
            if (lastIndexForBucket[bucket] == idx) {
                // Flush this bucket's extras.
                val extras = extrasByBucket.remove(bucket)
                if (extras != null) for (def in extras) sb.append(",\n").append(def)
                // Determine the next standard bucket (if any).
                val nextStdBucket = standardBuckets.firstOrNull { it > bucket }
                // Flush all intermediate extra-only buckets that belong before the next standard bucket.
                for (extraBucket in extrasByBucket.keys.sorted()) {
                    if (nextStdBucket != null && extraBucket >= nextStdBucket) break
                    val gapExtras = extrasByBucket.remove(extraBucket) ?: continue
                    for (def in gapExtras) sb.append(",\n").append(def)
                }
            }
        }
        // Flush any remaining extras (buckets beyond the last standard bucket, e.g. TAGS).
        for (bucket in extrasByBucket.keys.sorted()) {
            for (def in extrasByBucket[bucket]!!) sb.append(",\n").append(def)
        }
        return sb.toString()
    }

    /**
     * Returns the unique identifier for a user-defined index on this table.
     *
     * The result follows the pattern `{table-name}$ci_{index-name}` and is truncated to 63 bytes (Postgres identifier limit).
     */
    internal fun customIndexId(indexName: String): String {
        val id = "${name}${PG_CUSTOM_IDX}${indexName}"
        return if (id.length > 63) id.substring(0, 63) else id
    }

    /**
     * Creates a [CustomIndex][naksha.model.objects.CustomIndex] on this table.
     *
     * Internal naming follows `{table-name}$ci_{index.name}`; truncated to Postgres's 63-byte identifier limit.
     * @param conn the connection to use to execute the creation.
     * @param index the index to create.
     */
    open fun createCustomIndex(conn: PgConnection, index: Index) {
        val sql = buildCustomIndexSql(index) ?: return
        conn.execute(sql).close()
    }

    /**
     * Drops a custom [Index] from this table.
     */
    open fun dropCustomIndex(conn: PgConnection, indexName: String) {
        val id = customIndexId(indexName)
        conn.execute("DROP INDEX IF EXISTS ${quoteIdent(id)} CASCADE").close()
    }

    private fun buildCustomIndexSql(index: Index): String? {
        val on = index.on
        if (on.isEmpty()) return null
        val id = quoteIdent(customIndexId(index.name))
        val unique = if (index.unique) "UNIQUE INDEX" else "INDEX "
        val fillFactor = if (isVolatile) "WITH (deduplicate_items=OFF, fillfactor=80)" else "WITH (deduplicate_items=OFF, fillfactor=100)"
        val members = collection.head.members
        val firstCol = on[0] ?: return null
        return when (index.type) {
            IndexType.BTREE -> {
                val cols = on.filterNotNull().joinToString(", ") { col ->
                    val pgIdent = quoteIdent(physicalColumnName(col, members))
                    val opclass = if (isTextColumn(col, members)) " text_pattern_ops" else ""
                    "$pgIdent$opclass DESC"
                }
                val include = index.include?.takeIf { !it.isEmpty() }?.let { inc ->
                    " INCLUDE (${inc.filterNotNull().joinToString(", ") { quoteIdent(physicalColumnName(it, members)) }})"
                } ?: ""
                """
CREATE $unique IF NOT EXISTS $id ON ${quotedName}
USING btree ($cols)$include
$fillFactor ${TABLESPACE};""".trim()
            }
            IndexType.SPATIAL -> {
                if (!isSpatialColumn(firstCol, members)) {
                    throw naksha.model.illegalArg("SPATIAL index '${index.name}' must target a member of dataType SPATIAL (column '$firstCol' is not)")
                }
                val cCol = quoteIdent(physicalColumnName(firstCol, members))
                """
CREATE INDEX  IF NOT EXISTS $id ON ${quotedName}
USING gist (naksha_2d($cCol))
WITH (fillfactor=${if (isVolatile) 80 else 100}) ${TABLESPACE}
WHERE naksha_2d($cCol) IS NOT NULL;""".trim()
            }
            IndexType.TAGS -> {
                if (!isFlatMapColumn(firstCol, members)) {
                    throw naksha.model.illegalArg("TAGS custom index '${index.name}' must target a member of dataType TAGS or TAGS_FROM_ARRAY")
                }
                val pgIdent = quoteIdent(physicalColumnName(firstCol, members))
                """
CREATE INDEX  IF NOT EXISTS $id ON ${quotedName}
USING gin ($pgIdent)
$fillFactor ${TABLESPACE};""".trim()
            }
            IndexType.SET -> {
                if (!isSetColumn(firstCol, members)) {
                    throw naksha.model.illegalArg("SET custom index '${index.name}' must target a member of dataType SET")
                }
                val pgIdent = quoteIdent(physicalColumnName(firstCol, members))
                """
CREATE INDEX  IF NOT EXISTS $id ON ${quotedName}
USING gin ($pgIdent)
$fillFactor ${TABLESPACE};""".trim()
            }
            else -> null
        }
    }

    private fun physicalColumnName(name: String, members: MemberList?): String {
        if (members != null) {
            for (m in members) {
                if (m != null && m.name == name) return PgCustomMemberValues.pgColumnName(name)
            }
        }
        return name
    }

    private fun isTextColumn(name: String, members: MemberList?): Boolean {
        if (members != null) {
            for (m in members) {
                if (m != null && m.name == name) {
                    return m.dataType == MemberType.STRING
                }
            }
        }
        if (name == "id" || name == "app_id" || name == "author" || name == "ft" || name == "origin" || name == "target") return true
        return false
    }

    private fun isFlatMapColumn(name: String, members: MemberList?): Boolean {
        if (members == null) return false
        for (m in members) {
            if (m != null && m.name == name) {
                return m.dataType == MemberType.TAGS
                    || m.dataType == MemberType.TAGS_FROM_ARRAY
            }
        }
        return false
    }

    private fun isSetColumn(name: String, members: MemberList?): Boolean {
        // The built-in `tags` column is a SET (jsonb array) by default.
        if (name == PgColumn.tags.name) return true
        if (members == null) return false
        for (m in members) {
            if (m != null && m.name == name) return m.dataType == MemberType.SET
        }
        return false
    }

    private fun isSpatialColumn(name: String, members: MemberList?): Boolean {
        // Built-in spatial columns are always valid targets.
        if (name == PgColumn.geo.name || name == PgColumn.ref_point.name) return true
        // Custom members declared as SPATIAL are valid.
        if (members != null) {
            for (m in members) {
                if (m != null && m.name == name) return m.dataType == MemberType.SPATIAL
            }
        }
        return false
    }
}

