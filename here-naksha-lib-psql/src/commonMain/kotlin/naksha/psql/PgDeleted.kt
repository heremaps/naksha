@file:Suppress("OPT_IN_USAGE")

package naksha.psql

import naksha.model.NakshaErrorCode.StorageErrorCompanion.PARTITION_NOT_FOUND
import naksha.psql.PgUtil.PgUtilCompanion.partitionNumber
import kotlin.js.JsExport
import kotlin.jvm.JvmField

/**
 * The DELETED table of a collection.
 */
@JsExport
class PgDeleted(val head: PgHead) : PgTable(
    head.collection, "${head.collection.id}\$del", head.storageClass, true,
    partitionByColumn = head.partitionByColumn, partitionCount = head.partitionCount
) {
    @JvmField
    val partitions: Array<PgDeletedPartition> = Array(head.partitions.size) { PgDeletedPartition(this, it) }

    /**
     * Calculate the performance partition into which to write the feature with the given ID.
     * @param featureId the ID of the feature to locate the performance partition for.
     * @return either the performance partition to put the feature into; _null_ if the table is not partitioned, features need to be written into the table itself.
     */
    operator fun get(featureId: String): PgDeletedPartition? {
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
    }

    override fun dropIndex(conn: PgConnection, index: PgIndex) {
        if (this.partitionByColumn != null) {
            for (partition in partitions) partition.dropIndex(conn, index)
        } else {
            super.dropIndex(conn, index)
        }
    }
}