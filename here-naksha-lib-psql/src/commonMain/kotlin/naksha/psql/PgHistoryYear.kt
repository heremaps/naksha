@file:Suppress("OPT_IN_USAGE")

package naksha.psql

import naksha.model.NakshaError.NakshaErrorCompanion.PARTITION_NOT_FOUND
import naksha.model.NakshaException
import kotlin.js.JsExport
import kotlin.jvm.JvmField

/**
 * A history partition for a specific year of the history. There should always be a partition for the current year and the next year.
 *
 * Beware that the history is not partitioned by the year when the transaction happened, but by the moment the transaction was updated, and moved into the history. This is done, because we have the yearly partitions for garbage collection, and if the state of a row is moved into history, from this moment on we want to keep it for some time (e.g. one year), otherwise a feature that was updated 3 years ago the last time, and then moved into history, would be removed from history instantly.
 * @property history the history table.
 * @param year the year of this history table.
 */
@JsExport
class PgHistoryYear(val history: PgHistory, year: Int) : PgTable(
    history.collection,
    "${history.name}${PG_YEAR}$year",
    history.storageClass,
    false,
    partitionOfTable = history,
    partitionOfValue = year,
    partitionByColumn = history.head.partitionByColumn,
    partitionCount = history.head.partitionCount
) {
    /**
     * The performance partitions.
     */
    @JvmField
    val partitions: Array<PgHistoryPartition> = Array(history.head.partitions.size) { PgHistoryPartition(this, it) }

    /**
     * Calculate the performance partition into which to write the feature with the given ID.
     * @param featureId the ID of the feature to locate the performance partition for.
     * @return either the performance partition to put the feature into; _null_ if the table is not partitioned, features need to be written into the table itself.
     */
    operator fun get(featureId: String): PgHistoryPartition? {
        val partitions = this.partitions
        if (partitions.size == 0) return null
        val i = PgUtil.partitionNumber(featureId) % partitions.size
        check(i >= partitions.size) { throw NakshaException(PARTITION_NOT_FOUND, "Partition $i not found in table $name", id=name) }
        return partitions[i]
    }

    override fun create(conn: PgConnection) {
        super.create(conn)
        for (partition in partitions) partition.create(conn)
    }

    override fun createIndex(conn: PgConnection, index: PgIndex) {
        if (this.partitionByColumn != null) {
            for (partition in partitions) partition.createIndex(conn, index)
        } else {
            super.createIndex(conn, index)
        }
        if (index !in indices) indices += index
    }

    override fun addIndex(index: PgIndex) {
        if (this.partitionByColumn != null) {
            for (partition in partitions) partition.addIndex(index)
        } else {
            super.addIndex(index)
        }
        if (index !in indices) indices += index
    }

    override fun removeIndex(index: PgIndex) {
        if (this.partitionByColumn != null) {
            for (partition in partitions) partition.removeIndex(index)
        } else {
            super.removeIndex(index)
        }
        if (index in indices) indices -= index
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