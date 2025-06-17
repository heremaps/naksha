@file:Suppress("OPT_IN_USAGE")

package naksha.psql

import naksha.base.Platform.Platform_C.forKClass
import naksha.base.PlatformType
import naksha.base.collectionNotFound
import naksha.base.illegalState
import naksha.base.mapNotFound
import naksha.model.*
import naksha.model.request.FeatureTuple
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * Helper to simplify reading from partitioned tables.
 * @since 3.0
 */
internal data class PgRead(
    /**
     * The map to read from.
     */
    val map: PgMap,

    /**
     * The collection to read from.
     */
    val collection: PgCollection,

    /**
     * If a tuple by tuple-number should be read.
     */
    val tupleNumber: TupleNumber?,

    /**
     * If a tuple by id should be read.
     */
    val featureId: String?,

    /**
     * The amount of performance-partitions, `-1` if no performance partitions are there, otherwise a value between `2` and `1000`.
     */
    val partitionCount: Int = if (collection.partitions > 1) collection.partitions else -1,

    /**
     * If we need to read performance partitions.
     */
    val readPartition: Boolean = partitionCount >= 0,

    /**
     * If a tuple-number or id was given, the partition to read. Will be `-1` if either the collection does not have performance collections, or if we need to read all partitions.
     */
    val partition: Int = if (!readPartition) -1
        else if (tupleNumber != null) tupleNumber.partitionNumber % collection.partitions
        else if (featureId != null) Naksha.partitionNumber(featureId) % collection.partitions
        else -1,

    /**
     * A grouping identifier.
     *
     * Actually, allows grouping so that the same map/collection is merged, and that all reads that are from all partitions of a collection are grouped together, and all that read from the same partition. Therefore, eventually the first read from a group with the same group-id, will always read from the same map, collection, partition(s).
     */
    val groupId: String = if (!readPartition) "${map.id}:${collection.id}" else "${map.id}:${collection.id}:${partition}"
) {
    companion object PgRead_C {
        /**
         * The [PlatformType] of [PgRead].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(PgRead::class).withPackageName(PACKAGE_NAME)

        private val UNDEFINED = ArrayList<PgTable>(0)
    }

    /**
     * Optional attachment that helps loading.
     */
    var featureTuple: FeatureTuple? = null

    /**
     * Read multiple features by arbitrary query from this collection.
     * @param map the map to read.
     * @param collection the collection to read.
     */
    constructor(map: PgMap, collection: PgCollection): this(map, collection, null, null)

    /**
     * Read a tuple by tuple-number.
     * @param conn the database connection.
     * @param adminMap the admin-map.
     * @param tupleNumber the tuple-number of the tuple to read.
     */
    constructor(conn: PgConnection, adminMap: PgAdminMap, tupleNumber: TupleNumber) : this(
        adminMap.getPgMapByNumber(conn, tupleNumber.mapNumber)
            ?: throw mapNotFound("The map for map-number ${tupleNumber.mapNumber} not found"),
        adminMap.getPgMapByNumber(conn, tupleNumber.mapNumber)?.getPgCollectionByNumber(conn, tupleNumber.collectionNumber)
            ?: throw collectionNotFound("The collection for collection-number ${tupleNumber.collectionNumber} not found"),
        tupleNumber,
        null
    )

    /**
     * Read a tuple by feature-id.
     * @param map the map to read.
     * @param collection the collection to read.
     * @param id the feature-id of the tuple to read.
     */
    constructor(map: PgMap, collection: PgCollection, id: String) : this(map, collection, null, id)

    private fun initHeadTables(): List<PgTable> {
        val headTable = collection.headTable
        if (headTable is PgTransactions) {
            val tables = ArrayList<PgTable>(headTable.years.size)
            for (entry in headTable.years) {
                val year = entry.key
                val transactionTable = headTable.years[year]
                    ?: throw illegalState("Internal error, failed to add transaction year $year")
                tables.add(transactionTable)
            }
            return tables
        }
        if (readPartition) {
            val headPartitions = headTable.partitions
            // If it is enough to read a single partition, because we know where the feature is
            if (partition >= 0) return listOf(headPartitions[partition])
            // Otherwise read all partitions
            val tables = ArrayList<PgTable>(headPartitions.size)
            for (i in headPartitions.indices) {
                tables.add(headPartitions[i])
            }
            return tables
        }
        return listOf(headTable)
    }

    private var _headTables: List<PgTable>? = UNDEFINED
    /**
     * All HEAD tables to read.
     */
    val headTables: List<PgTable>
        get() {
            var tables = _headTables
            if (tables !== UNDEFINED && tables != null) return tables
            tables = initHeadTables()
            _headTables = tables
            return tables
        }

    /**
     * The meta-table to read.
     */
    val metaTable: PgTable? = collection.metaTable

    private fun initShadowTables(): List<PgTable>? {
        val deletedTable = collection.deletedTable ?: return null
        if (readPartition) {
            val delPartitions = deletedTable.partitions
            // If it is enough to read a single partition, because we know where the feature is
            if (partition >= 0) return listOf(delPartitions[partition])
            // Otherwise read all partitions
            val tables = ArrayList<PgTable>(delPartitions.size)
            for (i in delPartitions.indices) {
                tables.add(delPartitions[i])
            }
            return tables
        }
        return listOf(deletedTable)
    }

    private var _shadowTables: List<PgTable>? = UNDEFINED

    /**
     * All shadow tables to read.
     */
    val shadowTables: List<PgTable>?
        get() {
            var tables = _shadowTables
            if (tables !== UNDEFINED) return tables
            tables = initShadowTables()
            _shadowTables = tables
            return tables
        }

    fun initHistoryTables(): List<PgTable>? {
        val history = collection.historyTable ?: return null
        // TODO: hack to be be fixed as part of CASL-1095
        // it was observed that if collection is used for the first time (it is not cached) they `years` are empty
        // even if the year partitions actually exist on DB side
        // this results in returned history tables being empty (even though they can be there)
        // this behavior was observedd during CASL-1057 development
        if(history.years.isEmpty()){
            // see: PgMap.createPgCollection
            val year = Epoch().year
            history.addYear(year)
            history.addYear(year + 1)
        }
        val tables = ArrayList<PgTable>(history.years.size)
        for (entry in history.years) {
            val year = entry.key
            val historyTable = history.years[year]
                ?: throw illegalState("Internal error, failed to add history year $year")
            if (readPartition) {
                val hstPartitions = historyTable.partitions
                // If it is enough to read a single partition, because we know where the feature is
                if (partition >= 0) {
                    tables.add(hstPartitions[partition])
                } else {
                    // Otherwise read all partitions
                    for (i in 0 ..< partitionCount) {
                        tables.add(hstPartitions[i])
                    }
                }
            } else {
                // When we do not performance-partition history, only table per year.
                tables.add(historyTable)
            }
        }
        return tables
    }

    private var _historyTables: List<PgTable>? = UNDEFINED

    /**
     * All history tables to read.
     */
    val historyTables: List<PgTable>?
        get() {
            var tables = _historyTables
            if (tables !== UNDEFINED) return tables
            tables = initHistoryTables()
            _historyTables = tables
            return tables
        }
}
