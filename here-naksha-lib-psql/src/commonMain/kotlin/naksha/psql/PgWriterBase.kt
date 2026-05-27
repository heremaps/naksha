package naksha.psql

import naksha.base.Int64
import naksha.base.IntMutable
import naksha.model.Version
import naksha.model.illegalState
import naksha.model.objects.NakshaTx

/**
 * Base class for all operations, so for:
 * - [PgWriterInsert]
 * - [PgWriterUpsert]
 * - [PgWriterUpdate]
 * - [PgWriterDelete]
 * @since 3.0
 * @see [PgWriter]
 */
internal abstract class PgWriterBase protected constructor(
    /**
     * The [writer][PgWriter] to which this write is bound.
     * @since 3.0
     */
    val writer: PgWriter,

    /**
     * The collection to operate upon.
     * @since 3.0
     */
    val collection: PgCollection,

    /**
     * The partition to write into, `-1` if writes should enter base table.
     * @since 3.0
     */
    val partition: Int,

    /**
     * The list of writes to perform.
     * @since 3.0
     */
    val writes: List<PgWrite>,
) {
    val session: PgSession
        get() = writer.session

    val storageNumber: Int64
        get() = collection.storage.number

    val mapNumber: Int
        get() = collection.map.number

    val collectionNumber: Int
        get() = collection.number

    /**
     * The transaction to operate upon.
     * @since 3.0
     */
    val tx = session.useTx()

    val version: Version
        get() = tx.version

    /**
     * The Naksha transaction.
     * @since 3.0
     */
    val transaction: NakshaTx
        get() = tx.transaction

    /**
     * The rows to write.
     * @since 3.0
     */
    val inRows = PgColumnRows()
        .withStorageNumber(storageNumber)
        .withMapNumber(mapNumber)
        .withCollectionNumber(collectionNumber)
        .withDefaultFlags(collection.head.defaultFlags ?: naksha.model.Naksha.DEFAULT_FLAGS)
        .withMinSize(writes.size)

    /**
     * Generates a live mapping between the write instructions and the partition-index into which they will write.
     * @return a map where the key is the partition index and the value the amount of write-operations in this partition, with `-1` being used as key for unknown partition-index, or when there is no partitioning.
     *
     * @since 3.0
     * @see [featureCountByPartitionJoined]
     */
    val featureCountByPartition: Map<Int, IntMutable>
        get() {
            val partitions = collection.head.partitions
            val partIndices = mutableMapOf<Int, IntMutable>()
            for (i in writes.indices) {
                val write = writes[i]
                val partIndex = write.tupleNumber?.partitionIndex(partitions) ?: -1
                val existing = partIndices[partIndex]
                if (existing != null) existing.plus(1) else partIndices[partIndex] = IntMutable(1)
            }
            return partIndices
        }

    /**
     * The [featureCountByPartition] serialized into a string.
     *
     * This method should be preferred instead of doing:
     * ```kotlin
     * writer.featureCountByPartition.entries
     *   .joinToString(", ") { "${it.key}=${it.value.value}" }
     * ```
     * It actually will not even invoke [featureCountByPartition], when there is no partitioning.
     * @since 3.0
     * @see [featureCountByPartition]
     */
    val featureCountByPartitionJoined: String
        get() {
            val partitions = collection.head.partitions
            return if (partitions <= 1) "-1: ${writes.size}"
            else featureCountByPartition.entries.joinToString(", ") { "${it.key}=${it.value.value}" }
        }

    /**
     * If this write should be done into a partition.
     */
    val writeIntoPartition: Boolean = partition >= 0

    /**
     * The year when the transaction started, for transactions and history writes.
     */
    val year: Int = tx.version.year

    private fun initHeadTable(): PgTable {
        if (writeIntoPartition) {
            return collection.headTable.partitions[partition]
        }
        if (collection.headTable is PgTransactions) {
            val transactions = collection.headTable as PgTransactions
            var table = transactions.years[year]
            if (table == null) {
                transactions.addYear(year)
                table = transactions.years[year]
                if (table == null) {
                    throw illegalState("Internal error, failed to add transaction year $year")
                }
            }
            return table
        }
        return collection.headTable
    }

    /**
     * The head table to write into.
     */
    val headTable: PgTable = initHeadTable()

    private fun initHistoryTable(): PgTable? {
        val hst = collection.historyTable ?: return null
        var yearTable: PgHistoryYear? = hst.years[year]
        if (yearTable == null) {
            hst.addYear(year)
            yearTable = hst.years[year]
            if (yearTable == null) {
                throw illegalState("Internal error, failed to add history year $year")
            }
        }
        return if (writeIntoPartition) yearTable.partitions[partition] else yearTable
    }

    /**
     * The history table to write into, if any.
     */
    val historyTable: PgTable? = initHistoryTable()

    /**
     * Execute the operation.
     * @param conn the connection to be used.
     * @since 3.0
     */
    fun execute(conn: PgConnection) {
        collection.map.setSearchPath(conn)
        return doExecute(conn)
    }

    /**
     * Execute the operation.
     * @param conn the connection to be used.
     * @since 3.0
     */
    protected abstract fun doExecute(conn: PgConnection)
}
