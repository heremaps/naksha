package naksha.psql

import naksha.base.Int64
import naksha.base.IntMutable
import naksha.base.fn.Fx3
import naksha.model.Tuple
import naksha.base.Version
import naksha.base.illegalArg
import naksha.base.illegalState
import naksha.model.objects.NakshaTx
import naksha.model.objects.StandardMembers
import kotlin.collections.mutableMapOf
import kotlin.jvm.JvmStatic

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
    val pgWriter: PgWriter,

    /**
     * The collection to operate upon.
     * @since 3.0
     */
    val pgCollection: PgCollection,

    /**
     * The list of writes to perform.
     * @since 3.0
     */
    val pgWrites: List<PgWrite>,

    /**
     * The index of first [PgWrite] from the [pgWrites] list to process.
     */
    val start: Int,

    /**
     * The index of first [PgWrite] from the [pgWrites] list to **NOT** process.
     */
    val end: Int,
) {
    companion object PgWriterBase_C {
        @JvmStatic
        protected val UNDEFINED: ByteArray = "undefined".encodeToByteArray()
    }

    val session: PgSession
        get() = pgWriter.session

    val storageNumber: Int64
        get() = pgCollection.storage.number

    val catalogNumber: Int
        get() = pgCollection.catalog.catalogNumber

    val collectionNumber: Int
        get() = pgCollection.collectionNumber

    /** The _HEAD_ table. */
    val headTable = pgCollection.headTable
    /** The quoted name of the _HEAD_ table. */
    val headIdent = headTable.quotedName
    /** The _HISTORY_ table or `null`, if _HISTORY_ is disabled. */
    val historyTable = if (pgCollection.storeHistory) pgCollection.historyTable else null
    /** The quoted name of the _HISTORY_ table or `null`, if _HISTORY_ is disabled. */
    val historyIdent = historyTable?.quotedName
    /** The `id` column */
    val ID: PgColumn = pgCollection.column(StandardMembers.Id) ?: throw illegalState("The collection does not have an 'id' column.")
    /** The change-count column, if there is any defined. */
    val CC: PgColumn? = pgCollection.column(StandardMembers.ChangeCount)

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
        get() = tx.nakshaTx

    /**
     * The rows to write.
     * @since 3.0
     */
    val inRows = PgRows()
        .withDatabaseNumber(storageNumber)
        .withCatalogNumber(catalogNumber)
        .withCollectionNumber(collectionNumber)

    /**
     * Generates a live mapping between the write instructions and the partition-index into which they will write.
     * @return a map where the key is the partition index and the value the amount of write-operations in this partition, with `-1` being used as key for unknown partition-index, or when there is no partitioning.
     *
     * @since 3.0
     * @see [featureCountByPartitionJoined]
     */
    val featureCountByPartition: Map<Int, IntMutable>
        get() {
            val partitions = pgCollection.partitions
            val partIndices = mutableMapOf<Int, IntMutable>()
            for (i in 0 ..< pgWrites.size) {
                val write = pgWrites[i]
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
            val partitions = pgCollection.head.partitions
            return if (partitions <= 1) "-1: ${pgWrites.size}"
            else featureCountByPartition.entries.joinToString(", ") { "${it.key}=${it.value.value}" }
        }

    /**
     * Execute the operation.
     * @param conn the connection to be used.
     * @since 3.0
     */
    fun execute(conn: PgConnection) {
        pgCollection.catalog.setSearchPath(conn)
        return doExecute(conn)
    }

    /**
     * Add all tuple from [pgWrites], expects that the columns are prepared.
     * @param lambda a lambda optionally being called after every imported tuple, with `row`, `tuple` and `pgWrite` as arguments.
     * @return the number of rows loaded.
     */
    protected fun loadAllTuple(lambda: Fx3<Int, Tuple, PgWrite>? = null): Int {
        var row = 0
        inRows.setMinRows(inRows.size + (end - start))
        for (i in start ..< end) {
            val pgWrite = pgWrites[i]
            val tuple = pgWrite.tuple ?: throw illegalArg("The write #$i has no tuple, failed to load all tuple")
            inRows[row] = tuple
            lambda?.call(row, tuple, pgWrite)
            row++
        }
        return row
    }

    /**
     * Execute the operation.
     * @param conn the connection to be used.
     * @since 3.0
     */
    protected abstract fun doExecute(conn: PgConnection)
}
