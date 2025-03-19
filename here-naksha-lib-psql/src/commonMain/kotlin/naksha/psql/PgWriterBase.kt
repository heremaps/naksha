package naksha.psql

import naksha.base.Int64
import naksha.base.IntMutable
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
     * The list of writes to perform.
     * @since 3.0
     */
    val writes: List<PgWrite>
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
