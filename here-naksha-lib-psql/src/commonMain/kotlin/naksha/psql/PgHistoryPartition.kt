@file:Suppress("OPT_IN_USAGE")

package naksha.psql

import naksha.base.Int64
import naksha.model.Naksha
import naksha.base.NakshaError.NakshaErrorCompanion.PARTITION_NOT_FOUND
import naksha.base.NakshaException
import naksha.model.objects.StandardMembers.StandardMembers_C.Id
import naksha.model.objects.StandardMembers.StandardMembers_C.NextVersion
import naksha.psql.PgColumn.PgColumn_C.FnColumn
import naksha.psql.PgColumn.PgColumn_C.VersionColumn
import naksha.psql.PgUtil.PgUtilCompanion.quoteIdent
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.jvm.JvmField

/**
 * A history partition for all features modified in a specific next-version range.
 * @since 3.0
 * @see [PgHistoryTable]
 * @see [PgDistributionPartition]
 */
@JsExport
class PgHistoryPartition(
    /**
     * The parent table, must be [PgHistoryTable].
     * @since 3.0
     */
    parent: PgHistoryTable,

    /**
     * The partition-index of this partition, actually `{next-version} shr {shift}`.
     * @since 3.0
     */
    @JvmField
    val partitionIndex: Int
) : PgTable(parent.collection, parent.name + '$' + partitionIndex, parent) {
    /**
     * All distribution partitions; if not partitioned, then an empty array.
     * @since 3.0
     */
    @JvmField
    val partitions: Array<PgDistributionPartition> = if (collection.partitions <= 1) emptyArray() else Array(collection.partitions) {
        PgDistributionPartition(this, it)
    }

    override fun CREATE_SQL(): String {
        val (CREATE_TABLE, TABLESPACE) = CREATE_TABLE_and_TABLESPACE()
        val toast_tuple_target:Int = collection.catalog.storage.adminCatalog.maxTupleSize
        val parent = this.parent as PgHistoryTable
        val ID = collection.column(Id)
        val NEXT_VERSION = collection.column(NextVersion)

        // HISTORY-PARTITION is NOT distribution partitioned.
        if (partitions.isEmpty()) return """$CREATE_TABLE $quotedName 
PARTITION OF ${parent.quotedName} (${parent.CONSTRAINT(name, partitionIndex)})
FOR VALUES FROM ($partitionIndex) TO (${partitionIndex+1}) 
WITH (fillfactor=50,toast_tuple_target=$toast_tuple_target)$TABLESPACE;
CREATE INDEX IF NOT EXISTS ${quoteIdent(name, "\$i_version")} ON $quotedName USING btree ($VersionColumn, $NEXT_VERSION) INCLUDE ($FnColumn, $ID);"""

        // HISTORY-PARTITION is distribution partitioned.
        return """$CREATE_TABLE $quotedName
PARTITION OF ${parent.quotedName}
FOR VALUES FROM ($partitionIndex) TO (${partitionIndex+1})
PARTITION BY RANGE ((($FnColumn & 65535)::int4 % ${collection.partitions}))$TABLESPACE"""
    }

    /**
     * Calculates the partition-number from the given [feature-number][FnColumn].
     * @param featureNumber the [feature-number][FnColumn] from which to calculate the partition-number.
     * @return the calculated partition-number.
     * @since 3.0
     */
    @JsName("partitionNumberForFeatureNumber")
    fun partitionNumber(featureNumber: Int64): Int = Naksha.partitionNumber(featureNumber) % collection.partitions

    /**
     * Calculates the partition-number from the given [feature-id][Id].
     * @param featureId the [feature-id][Id] from which to calculate the partition-number.
     * @return the calculated partition-number.
     * @since 3.0
     */
    @JsName("partitionNumberForFeatureId")
    fun partitionNumber(featureId: String): Int = Naksha.partitionNumber(featureId) % collection.partitions

    /**
     * Calculate the distribution-partition into which to write the feature with the given feature-number.
     * @param featureNumber the feature-number of the feature to return the distribution-partition for.
     * @return either the distribution-partition to put the feature into or `null` if the table is not partitioned, features need to be written into the table itself.
     */
    @JsName("getByFeatureNumber")
    operator fun get(featureNumber: Int64): PgDistributionPartition? {
        val partitions = this.partitions
        if (partitions.isEmpty()) return null
        val i = Naksha.partitionNumber(featureNumber) % collection.partitions
        check(i >= partitions.size) { throw NakshaException(PARTITION_NOT_FOUND, "Partition $i not found in table $name") }
        return partitions[i]
    }

    /**
     * Calculate the distribution-partition into which to write the feature with the given feature-id.
     * @param featureId the feature-id of the feature to return the distribution-partition for.
     * @return either the distribution-partition to put the feature into or `null` if the table is not partitioned, features need to be written into the table itself.
     */
    @JsName("getByFeatureId")
    operator fun get(featureId: String): PgDistributionPartition? {
        val partitions = this.partitions
        if (partitions.isEmpty()) return null
        val i = Naksha.partitionNumber(featureId) % collection.partitions
        check(i >= partitions.size) { throw NakshaException(PARTITION_NOT_FOUND, "Partition $i not found in table $name") }
        return partitions[i]
    }

    override fun create(conn: PgConnection) {
        super.create(conn)
        for (partition in partitions) partition.create(conn)
    }

    override fun createIndex(conn: PgConnection, index: PgIndex) {
        if (!partitions.isEmpty()) {
            for (partition in partitions) partition.createIndex(conn, index)
        } else {
            super.createIndex(conn, index)
        }
        if (index !in indices) indices += index
    }

    override fun addIndex(index: PgIndex) {
        if (!partitions.isEmpty()) {
            for (partition in partitions) partition.addIndex(index)
        } else {
            super.addIndex(index)
        }
        if (index !in indices) indices += index
    }

    override fun removeIndex(index: PgIndex) {
        if (!partitions.isEmpty()) {
            for (partition in partitions) partition.removeIndex(index)
        } else {
            super.removeIndex(index)
        }
        if (index in indices) indices -= index
    }

    override fun dropIndex(conn: PgConnection, index: PgIndex) {
        if (!partitions.isEmpty()) {
            for (partition in partitions) partition.dropIndex(conn, index)
        } else {
            super.dropIndex(conn, index)
        }
        if (index in indices) indices -= index
    }
}