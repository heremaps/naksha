@file:Suppress("OPT_IN_USAGE")

package naksha.psql

import kotlin.js.JsExport

/**
 * A feature partition for performance optimisation.
 * @property year the history year.
 * @param index the index in the history year partitions array.
 */
@JsExport
class PgHistoryPartition(val year: PgHistoryYear, index: Int) : PgTable(
    year.collection, "${year.name}_p${PgUtil.partitionPosix(index)}", year.storageClass, false,
    partitionOfTable = year, partitionOfValue = index
)