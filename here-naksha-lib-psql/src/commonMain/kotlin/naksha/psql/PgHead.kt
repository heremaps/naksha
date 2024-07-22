package naksha.psql

import naksha.model.NakshaErrorCode.StorageErrorCompanion.PARTITION_NOT_FOUND
import naksha.psql.PgUtil.PgUtilCompanion.partitionNumber
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.jvm.JvmField

/**
 * The HEAD table of a collection.
 */
@Suppress("OPT_IN_USAGE")
@JsExport
open class PgHead protected constructor(
    collection: PgCollection,
    name: String,
    storageClass: PgStorageClass,
    isVolatile: Boolean,
    partitionOf: PgTable? = null,
    partitionOfValue: Int = -1,
    partitionBy: PgColumn? = null,
    partitionCount: Int = 0
) : PgTable(
    collection, name, storageClass, isVolatile,
    partitionOf, partitionOfValue, partitionBy, partitionCount
) {
    /**
     * Create a new ordinary HEAD table.
     * @param c the collection for which to create the HEAD table.
     * @param storageClass the storage-class for this table and all related.
     * @param partCount the amount of partitions to create (0 or 2 to 256).
     */
    @JsName("of")
    constructor(c: PgCollection, storageClass: PgStorageClass, partCount: Int) : this(
        c, c.id, storageClass, true,
        partitionCount = if (partCount <= 1) 0 else partCount,
        partitionBy = if (partCount >= 2) PgColumn.id else null
    )

    /**
     * All performance partitions to stored into; empty if no performance partitioning.
     */
    @JvmField
    val partitions: Array<PgHeadPartition> = if (partitionCount <= 1) emptyArray() else Array(partitionCount) {
        PgHeadPartition(this, it)
    }

    /**
     * Calculate the performance partition into which to write the feature with the given ID.
     * @param featureId the ID of the feature to locate the performance partition for.
     * @return either the performance partition to put the feature into; _null_ if the table is not partitioned, features need to be written into the table itself.
     */
    operator fun get(featureId: String): PgHeadPartition? {
        val partitions = this.partitions
        if (partitions.size == 0) return null
        val i = partitionNumber(featureId) % partitions.size
        check(i >= partitions.size) { throwStorageException(PARTITION_NOT_FOUND, "Partition $i not found in table $name", id=name) }
        return partitions[i]
    }

    override fun create(conn: PgConnection) {
        super.create(conn)
        for (partition in partitions) partition.create(conn)
    }

    override fun addIndex(conn: PgConnection, index: PgIndex) {
        if (this.partitionByColumn != null) {
            for (partition in partitions) partition.addIndex(conn, index)
        } else {
            super.addIndex(conn, index)
        }
        if (index !in indices) indices += index
    }

    override fun dropIndex(conn: PgConnection, index: PgIndex) {
        if (this.partitionByColumn != null) {
            for (partition in partitions) partition.dropIndex(conn, index)
        } else {
            super.dropIndex(conn, index)
        }
        if (index in indices) indices -= index
    }
}