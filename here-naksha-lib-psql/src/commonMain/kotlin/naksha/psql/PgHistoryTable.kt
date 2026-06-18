@file:Suppress("OPT_IN_USAGE")

package naksha.psql

import naksha.base.Int64
import naksha.model.objects.StandardMembers.StandardMembers_C.Id
import naksha.model.objects.StandardMembers.StandardMembers_C.NextVersion
import naksha.psql.PgColumn.PgColumn_C.FN
import naksha.psql.PgColumn.PgColumn_C.VERSION
import naksha.psql.PgUtil.PgUtilCompanion.quoteIdent
import kotlin.js.JsExport
import kotlin.jvm.JvmField

/**
 * The _HISTORY_ table of a collection , partitioned by [next_version][PgColumn.NEXT_VERSION].
 *
 * Features are moved into the history table when new states _([tuple][naksha.model.Tuple])_ are created in _HEAD_. In that case the previous [tuple][naksha.model.Tuple] is moved from _HEAD_ into _HISTORY_. When moving the [tuple][naksha.model.Tuple], its next-version property is set to the version of the new _head_ [tuple][naksha.model.Tuple], that replaces it. Within the history table the features are partitioned by this [next-version][naksha.model.objects.StandardMembers.StandardMembers_C.NextVersion]. With the default [shift][naksha.model.objects.NakshaCollection.shift] of 41 the partitioning effectively is done by calendar year of when the [tuple][naksha.model.Tuple] has become obsolete and was moved into _HISTORY_.
 * @since 3.0
 * @see [PgHistoryPartition]
 * @see [PgDistributionPartition]
 * @see [PgTable]
 */
@JsExport
class PgHistoryTable(
    /** The collection to which this HEAD table belongs. */
    collection: PgCollection,
) : PgTable(collection, collection.id + "\$hst", null) {

    /**
     * All history partitions with the key being the partition-number and the value being the partition.
     *
     * Beware that history-partitions are named like `{name}$hst${nextVersion >> shift}` for example `foo$hst$1`.
     * @since 3.0
     */
    @JvmField
    val partitions: MutableMap<Int, PgHistoryPartition> = mutableMapOf()

    @Suppress("FunctionName")
    internal fun CONSTRAINT(historyPartition:Int): String {
        val ID = collection.column(Id)
        val NEXT_VERSION = collection.column(NextVersion)
        return """
  CONSTRAINT ${quoteIdent(name, "\$c_pkey")} PRIMARY KEY ($FN, $VERSION) INCLUDE ($NEXT_VERSION, $ID}),
  CONSTRAINT ${quoteIdent(name, "\$c_nv")} CHECK ($NEXT_VERSION IS NOT NULL AND $NEXT_VERSION >= $VERSION),
  CONSTRAINT ${quoteIdent(name, "\$c_nvr")} CHECK ((($NEXT_VERSION >> ${collection.shift})::int4) = $historyPartition),
  CONSTRAINT ${quoteIdent(name, "\$c_id")} UNIQUE ($ID, $VERSION) INCLUDE ($NEXT_VERSION, $FN}),
  CONSTRAINT ${quoteIdent(name, "\$c_fn")} CHECK (($FN < 0 AND $ID IS NOT NULL) OR ($FN >= 0 AND $ID IS NULL))"""
    }

    @Suppress("FunctionName")
    internal fun CONSTRAINT(historyPartition:Int, distributionPartition: Int): String {
        return """${CONSTRAINT(historyPartition)},
  CONSTRAINT ${quoteIdent(name, "\$c_fnr")} CHECK ((($FN & 65535)::int4 % ${collection.partitions})=$distributionPartition)
"""
    }

    override fun CREATE_SQL(): String {
        val (CREATE_TABLE, TABLESPACE) = CREATE_TABLE_and_TABLESPACE()
        val NEXT_VERSION = collection.column(NextVersion)
        return """$CREATE_TABLE $quotedName (${columnDefinitions()}) 
PARTITION BY RANGE ((($NEXT_VERSION >> ${collection.shift})::int4)) 
WITH (fillfactor=100,toast_tuple_target=$toast_tuple_target) 
$TABLESPACE"""
    }

    /**
     * Calculates the partition-number from the given [next-version][NextVersion].
     * @param nextVersion the [next-version][NextVersion] from which to calculate the partition-number.
     * @return the calculated partition-number.
     * @since 3.0
     */
    fun partitionNumber(nextVersion: Int64): Int = (nextVersion shr collection.shift).toInt()

    operator fun get(nextVersion: Int64): PgHistoryPartition? = partitions[partitionNumber(nextVersion)]

    operator fun set(nextVersion: Int64, partition: PgHistoryPartition) {
        partitions[partitionNumber(nextVersion)] = partition
    }

    override fun create(conn: PgConnection) {
        super.create(conn)
        for (entry in partitions) entry.value.create(conn)
    }

    /**
     * Create a new [PgHistoryPartition], if no such partition exists already.
     * @param conn the connection to be used to modify the database.
     * @param partitionNumber the partition-number of the partition to create.
     * @return the [PgHistoryPartition] created or already existing.
     * @see [PgCollection.historyPartitionNumberOf]
     */
    fun createPartition(conn: PgConnection, partitionNumber: Int): PgHistoryPartition {
        var partition = partitions[partitionNumber]
        if (partition == null) {
            partition = PgHistoryPartition(this, partitionNumber)
            partitions[partitionNumber] = partition
        }
        partition.create(conn)
        for (index in indices) {
            partition.createIndex(conn, index)
        }
        return partition
    }

    fun addPartition(partitionNumber: Int) {
        if (partitionNumber !in partitions) {
            val partition = PgHistoryPartition(this, partitionNumber)
            partitions[partitionNumber] = partition
            for (index in indices) partition.addIndex(index)
        }
    }

    override fun addIndex(index: PgIndex) {
        for (entry in partitions) entry.value.addIndex(index)
    }

    override fun removeIndex(index: PgIndex) {
        for (entry in partitions) entry.value.removeIndex(index)
    }

    override fun createIndex(conn: PgConnection, index: PgIndex) {
        for (entry in partitions) entry.value.createIndex(conn, index)
    }

    override fun dropIndex(conn: PgConnection, index: PgIndex) {
        for (entry in partitions) entry.value.dropIndex(conn, index)
    }
}