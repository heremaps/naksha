@file:Suppress("OPT_IN_USAGE")

package naksha.psql

import kotlin.js.JsExport

/**
 * A feature partition for performance optimisation.
 * @param deleted the deleted table.
 * @param index the index in the deleted table partitions array.
 * @since 3.0
 * @see [PgDeleted]
 */
@JsExport
class PgDeletedPartition(val deleted: PgDeleted, index: Int) : PgTable(
    deleted.collection, "${deleted.name}${PG_PART}${PgUtil.partitionSuffix(index)}", deleted.storageClass, true,
    partitionOfTable = deleted, partitionOfValue = index
)