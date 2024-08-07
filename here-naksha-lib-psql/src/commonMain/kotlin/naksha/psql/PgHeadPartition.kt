@file:Suppress("OPT_IN_USAGE")

package naksha.psql

import kotlin.js.JsExport

/**
 * A feature partition for performance optimisation.
 * @property head the head table.
 * @param index the index in [PgHead.partitions].
 */
@JsExport
class PgHeadPartition internal constructor(val head: PgHead, index: Int) : PgTable(
    head.collection, "${head.name}${PG_PART}${PgUtil.partitionPosix(index)}", head.storageClass, true,
    partitionOfTable = head, partitionOfValue = index
)