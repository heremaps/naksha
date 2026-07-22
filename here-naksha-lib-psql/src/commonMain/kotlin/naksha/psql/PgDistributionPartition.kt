@file:Suppress("OPT_IN_USAGE")

package naksha.psql

import naksha.base.NakshaError.NakshaErrorCompanion.INTERNAL_ERROR
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
 * A distribution partition of either [PgHeadTable] or [PgHistoryPartition], used to store a huge amount of features in a collection.
 *
 * The name of the partitions will be `{name}${featureNumber % partitions.size}`, e.g. `foo$0` or `foo$hst$2026$0`.
 * @since 3.0
 * @see [PgHeadTable]
 * @see [PgHistoryPartition]
 */
@JsExport
class PgDistributionPartition private constructor(
    /**
     * The parent table, either [PgHeadTable] or [PgHistoryPartition].
     * @since 3.0
     */
    parent: PgTable,

    /**
     * The partition-number of this partition in the parent partitions, a value between `0` and `n`, with `n` being `parent.partitions.size - 1`.
     * @since 3.0
     */
    @JvmField
    val partitionIndex: Int
) : PgTable(parent.collection, parent.name + '$' + partitionIndex, parent) {
    /**
     * Create a distribution partition in the [PgHeadTable].
     */
    @JsName("newHeadDistributionPartition")
    constructor(parent: PgHeadTable, partitionNumber: Int) : this(parent as PgTable, partitionNumber)

    /**
     * Create a distribution partition in the history aka [PgHistoryPartition].
     */
    @JsName("newHistoryDistributionPartition")
    constructor(parent: PgHistoryPartition, partitionNumber: Int) : this(parent as PgTable, partitionNumber)

    override fun CREATE_SQL(): String {
        val (CREATE_TABLE, TABLESPACE) = CREATE_TABLE_and_TABLESPACE()
        val ID = collection.column(Id)
        val NEXT_VERSION = collection.column(NextVersion)

        // partition of HEAD.
        if (parent is PgHeadTable) return """$CREATE_TABLE $quotedName 
PARTITION OF ${parent.quotedName} (${parent.CONSTRAINT(name, partitionIndex)})
FOR VALUES FROM ($partitionIndex) TO (${partitionIndex+1}) 
WITH (fillfactor=50,toast_tuple_target=$toast_tuple_target)$TABLESPACE;
CREATE INDEX IF NOT EXISTS ${quoteIdent(name, "\$i_version")} ON $quotedName USING btree ($VersionColumn) INCLUDE ($FnColumn, $ID);"""

        // partition of HISTORY-PARTITION.
        if (parent is PgHistoryPartition) {
            val root = parent.parent as PgHistoryTable
            return """$CREATE_TABLE $quotedName 
PARTITION OF ${parent.quotedName} (${root.CONSTRAINT(name, parent.partitionIndex, partitionIndex)})
FOR VALUES FROM ($partitionIndex) TO (${partitionIndex+1}) 
WITH (fillfactor=100,toast_tuple_target=$toast_tuple_target)$TABLESPACE;
CREATE INDEX IF NOT EXISTS ${quoteIdent(name, "\$i_version")} ON $quotedName USING btree ($VersionColumn, $NEXT_VERSION) INCLUDE ($FnColumn, $ID);"""
        }

        throw NakshaException(INTERNAL_ERROR, "The distribution partition must have PgHeadTable or PgHistoryPartition as parent")
    }
}