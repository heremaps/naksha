package naksha.psql

import kotlin.js.JsExport

/**
 * A feature partition for performance optimisation.
 * @param deleted the deleted table.
 * @param index the index in the deleted table partitions array.
 */
@JsExport
class PgDeletedPartition(val deleted: PgDeleted, index: Int) : PgTable(
    deleted.collection, "${deleted.name}_p${PgUtil.partitionPosix(index)}", deleted.storageClass, true,
    partitionOfTable = deleted, partitionOfValue = index
)